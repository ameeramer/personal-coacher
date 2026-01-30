package com.personalcoacher.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Voice Activity Detection (VAD) manager using amplitude-based detection.
 * This is a simplified implementation that doesn't require ML models.
 *
 * For production, you could integrate Silero VAD (ONNX model) for better accuracy,
 * but amplitude-based detection works well for turn-based conversation.
 *
 * Features:
 * - Detects when user starts speaking
 * - Detects when user stops speaking (after silence threshold)
 * - Records audio during speech and returns the audio file
 * - Configurable silence duration threshold
 */
@Singleton
class SileroVadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SileroVadManager"

        // Audio recording parameters (optimized for voice)
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // VAD parameters
        private const val SPEECH_THRESHOLD_DB = -35.0 // dB threshold for speech detection
        private const val SILENCE_DURATION_MS = 1500L // How long silence before considering speech ended
        private const val MIN_SPEECH_DURATION_MS = 500L // Minimum speech duration to be valid
        private const val FRAME_SIZE_MS = 30 // Process audio in 30ms frames
    }

    // State
    private val _isListening = MutableStateFlow(false)
    val isListening: Flow<Boolean> = _isListening.asStateFlow()

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: Flow<Boolean> = _isSpeechDetected.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: Flow<Float> = _currentAmplitude.asStateFlow()

    // Events
    private val _speechEvents = Channel<SpeechEvent>(Channel.BUFFERED)
    val speechEvents: Flow<SpeechEvent> = _speechEvents.receiveAsFlow()

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null
    private val audioDir: File by lazy {
        File(context.filesDir, "vad_audio").apply { mkdirs() }
    }

    /**
     * Events emitted by the VAD
     */
    sealed class SpeechEvent {
        /** User started speaking */
        object SpeechStarted : SpeechEvent()
        /** User stopped speaking, audio file is ready */
        data class SpeechEnded(val audioFile: File, val durationMs: Long) : SpeechEvent()
        /** Error occurred */
        data class Error(val message: String) : SpeechEvent()
    }

    /**
     * Starts listening for voice activity.
     * Will emit SpeechEvent.SpeechStarted when user starts speaking,
     * and SpeechEvent.SpeechEnded with the audio file when they stop.
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (_isListening.value) {
            Log.w(TAG, "Already listening")
            return@withContext
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            ).coerceAtLeast(SAMPLE_RATE * 2) // At least 1 second buffer

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord failed to initialize")
                }
            }

            audioRecord?.startRecording()
            _isListening.value = true
            Log.d(TAG, "Started listening for voice activity")

            processAudioStream(bufferSize)

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for audio recording", e)
            _speechEvents.send(SpeechEvent.Error("Microphone permission required"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _speechEvents.send(SpeechEvent.Error(e.localizedMessage ?: "Failed to start listening"))
        }
    }

    /**
     * Stops listening for voice activity.
     */
    fun stopListening() {
        Log.d(TAG, "Stopping VAD listener")
        _isListening.value = false
        _isSpeechDetected.value = false
        listeningJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    private suspend fun processAudioStream(bufferSize: Int) = withContext(Dispatchers.IO) {
        val frameSize = SAMPLE_RATE * FRAME_SIZE_MS / 1000
        val buffer = ShortArray(frameSize)

        var isSpeaking = false
        var speechStartTime = 0L
        var lastSpeechTime = 0L
        val audioChunks = mutableListOf<ShortArray>()

        while (_isListening.value && isActive) {
            val record = audioRecord ?: break
            val readCount = record.read(buffer, 0, buffer.size)

            if (readCount > 0) {
                val amplitude = calculateRmsDb(buffer, readCount)
                _currentAmplitude.value = amplitude

                val isSpeechFrame = amplitude > SPEECH_THRESHOLD_DB

                if (isSpeechFrame) {
                    lastSpeechTime = System.currentTimeMillis()

                    if (!isSpeaking) {
                        // Speech just started
                        isSpeaking = true
                        speechStartTime = System.currentTimeMillis()
                        audioChunks.clear()
                        _isSpeechDetected.value = true
                        _speechEvents.send(SpeechEvent.SpeechStarted)
                        Log.d(TAG, "Speech started (amplitude: $amplitude dB)")
                    }

                    // Record this frame
                    audioChunks.add(buffer.copyOf(readCount))

                } else if (isSpeaking) {
                    // Silence detected while speaking
                    audioChunks.add(buffer.copyOf(readCount)) // Still record silence frames

                    val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                    if (silenceDuration >= SILENCE_DURATION_MS) {
                        // Speech ended
                        val speechDuration = System.currentTimeMillis() - speechStartTime
                        isSpeaking = false
                        _isSpeechDetected.value = false

                        if (speechDuration >= MIN_SPEECH_DURATION_MS) {
                            // Save audio and emit event
                            val audioFile = saveAudioToFile(audioChunks)
                            if (audioFile != null) {
                                Log.d(TAG, "Speech ended after ${speechDuration}ms, saved to ${audioFile.name}")
                                _speechEvents.send(SpeechEvent.SpeechEnded(audioFile, speechDuration))
                            } else {
                                _speechEvents.send(SpeechEvent.Error("Failed to save audio"))
                            }
                        } else {
                            Log.d(TAG, "Speech too short (${speechDuration}ms), ignoring")
                        }

                        audioChunks.clear()
                    }
                }
            }
        }
    }

    /**
     * Calculates the RMS (Root Mean Square) amplitude in decibels.
     */
    private fun calculateRmsDb(buffer: ShortArray, size: Int): Float {
        if (size == 0) return -100f

        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }

        val rms = sqrt(sum / size)
        // Convert to dB (reference: max short value = 32767)
        val db = if (rms > 0) 20 * kotlin.math.log10(rms / 32767.0) else -100.0

        return db.toFloat()
    }

    /**
     * Saves audio chunks to a WAV file.
     */
    private fun saveAudioToFile(chunks: List<ShortArray>): File? {
        return try {
            val audioFile = File(audioDir, "speech_${System.currentTimeMillis()}.wav")

            // Calculate total samples
            val totalSamples = chunks.sumOf { it.size }
            val totalBytes = totalSamples * 2 // 16-bit = 2 bytes per sample

            FileOutputStream(audioFile).use { fos ->
                // Write WAV header
                writeWavHeader(fos, totalBytes)

                // Write audio data
                val byteBuffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                for (chunk in chunks) {
                    for (sample in chunk) {
                        byteBuffer.clear()
                        byteBuffer.putShort(sample)
                        fos.write(byteBuffer.array())
                    }
                }
            }

            audioFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio to file", e)
            null
        }
    }

    /**
     * Writes a standard WAV file header.
     */
    private fun writeWavHeader(fos: FileOutputStream, audioDataSize: Int) {
        val totalSize = audioDataSize + 36 // 36 = header size without RIFF chunk
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        // RIFF header
        fos.write("RIFF".toByteArray())
        fos.write(intToLittleEndianBytes(totalSize))
        fos.write("WAVE".toByteArray())

        // fmt chunk
        fos.write("fmt ".toByteArray())
        fos.write(intToLittleEndianBytes(16)) // Chunk size
        fos.write(shortToLittleEndianBytes(1)) // Audio format (1 = PCM)
        fos.write(shortToLittleEndianBytes(channels))
        fos.write(intToLittleEndianBytes(SAMPLE_RATE))
        fos.write(intToLittleEndianBytes(byteRate))
        fos.write(shortToLittleEndianBytes(blockAlign))
        fos.write(shortToLittleEndianBytes(bitsPerSample))

        // data chunk
        fos.write("data".toByteArray())
        fos.write(intToLittleEndianBytes(audioDataSize))
    }

    private fun intToLittleEndianBytes(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }

    private fun shortToLittleEndianBytes(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte()
        )
    }

    /**
     * Cleans up old audio files.
     */
    fun clearCache() {
        try {
            audioDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "VAD cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}
