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
        private const val DEFAULT_SPEECH_THRESHOLD_DB = -50.0 // dB threshold for speech detection (lowered for better sensitivity)
        private const val THRESHOLD_MARGIN_DB = 15.0 // How much above noise floor to set threshold
        private const val SILENCE_DURATION_MS = 1500L // How long silence before considering speech ended
        private const val MIN_SPEECH_DURATION_MS = 500L // Minimum speech duration to be valid
        private const val FRAME_SIZE_MS = 30 // Process audio in 30ms frames
        private const val CALIBRATION_FRAMES = 30 // Number of frames to measure noise floor (about 1 second)
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

    // Debug logging
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: Flow<List<String>> = _debugLogs.asStateFlow()

    private fun addDebugLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        Log.d(TAG, message)
        _debugLogs.value = (_debugLogs.value + logEntry).takeLast(100) // Keep last 100 logs
    }

    fun clearDebugLogs() {
        _debugLogs.value = emptyList()
    }

    /**
     * Starts listening for voice activity.
     * Will emit SpeechEvent.SpeechStarted when user starts speaking,
     * and SpeechEvent.SpeechEnded with the audio file when they stop.
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (_isListening.value) {
            addDebugLog("Already listening, skipping")
            return@withContext
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            addDebugLog("Min buffer size: $minBufferSize")

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                addDebugLog("ERROR: Invalid buffer size returned: $minBufferSize")
                _speechEvents.send(SpeechEvent.Error("Audio format not supported on this device"))
                return@withContext
            }

            val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE * 2) // At least 1 second buffer
            addDebugLog("Using buffer size: $bufferSize")

            // Try different audio sources in order of preference
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
                MediaRecorder.AudioSource.MIC to "MIC",
                MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION"
            )

            var initSuccessful = false
            for ((source, sourceName) in audioSources) {
                addDebugLog("Trying audio source: $sourceName")
                try {
                    audioRecord?.release() // Release any previous attempt
                    audioRecord = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )

                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        addDebugLog("SUCCESS: AudioRecord initialized with $sourceName")
                        initSuccessful = true
                        break
                    } else {
                        addDebugLog("FAILED: $sourceName - state=${audioRecord?.state}")
                        audioRecord?.release()
                        audioRecord = null
                    }
                } catch (e: Exception) {
                    addDebugLog("FAILED: $sourceName - ${e.message}")
                }
            }

            if (!initSuccessful || audioRecord == null) {
                addDebugLog("ERROR: All audio sources failed to initialize")
                _speechEvents.send(SpeechEvent.Error("AudioRecord failed to initialize. Check microphone permissions and try closing other apps that may be using the microphone."))
                return@withContext
            }

            audioRecord?.startRecording()

            // Verify recording actually started
            val recordingState = audioRecord?.recordingState
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                addDebugLog("ERROR: AudioRecord failed to start recording (recordingState=$recordingState)")
                _speechEvents.send(SpeechEvent.Error("Failed to start audio recording. Try closing other apps that may be using the microphone, or restart the app."))
                audioRecord?.release()
                audioRecord = null
                return@withContext
            }

            _isListening.value = true
            addDebugLog("Started listening for voice activity (recordingState=$recordingState)")

            processAudioStream(bufferSize)

        } catch (e: SecurityException) {
            addDebugLog("ERROR: Permission denied - ${e.message}")
            _speechEvents.send(SpeechEvent.Error("Microphone permission required"))
        } catch (e: Exception) {
            addDebugLog("ERROR: Failed to start listening - ${e.message}")
            _speechEvents.send(SpeechEvent.Error(e.localizedMessage ?: "Failed to start listening"))
        }
    }

    /**
     * Stops listening for voice activity.
     */
    fun stopListening() {
        addDebugLog("Stopping VAD listener")
        _isListening.value = false
        _isSpeechDetected.value = false
        listeningJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            addDebugLog("AudioRecord stopped and released")
        } catch (e: Exception) {
            addDebugLog("Error stopping AudioRecord: ${e.message}")
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
        var frameCount = 0
        var lastLogTime = System.currentTimeMillis()

        // Adaptive threshold - will be calibrated from noise floor
        var speechThreshold = DEFAULT_SPEECH_THRESHOLD_DB
        val calibrationAmplitudes = mutableListOf<Float>()
        var isCalibrated = false

        addDebugLog("Starting audio processing loop (initial threshold: $speechThreshold dB, will calibrate...)")

        // Log immediately after entering the loop to confirm we got here
        var loopStarted = false
        var consecutiveSilentFrames = 0
        val silentFrameThreshold = 100 // About 3 seconds of silence at 30ms frames
        var hasWarnedAboutSilence = false

        while (_isListening.value && isActive) {
            if (!loopStarted) {
                addDebugLog("Audio loop entered, waiting for audio data...")
                loopStarted = true
            }
            val record = audioRecord ?: break

            // Verify AudioRecord is still recording
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                addDebugLog("ERROR: AudioRecord stopped recording unexpectedly (state=${record.recordingState})")
                _speechEvents.send(SpeechEvent.Error("Audio recording stopped unexpectedly"))
                break
            }

            val readCount = record.read(buffer, 0, buffer.size)

            if (readCount <= 0) {
                addDebugLog("WARNING: AudioRecord.read returned $readCount")
            }

            if (readCount > 0) {
                frameCount++
                val amplitude = calculateRmsDb(buffer, readCount)
                _currentAmplitude.value = amplitude

                // Calibration phase: measure noise floor from first frames
                if (!isCalibrated && frameCount <= CALIBRATION_FRAMES) {
                    calibrationAmplitudes.add(amplitude)
                    if (frameCount == CALIBRATION_FRAMES) {
                        // Calculate noise floor as the average of calibration samples
                        val noiseFloor = calibrationAmplitudes.average()
                        // Set threshold above noise floor, but not too high
                        speechThreshold = (noiseFloor + THRESHOLD_MARGIN_DB).coerceAtMost(-40.0)
                        isCalibrated = true
                        addDebugLog("CALIBRATED: noise floor=${noiseFloor.toInt()} dB, speech threshold=${speechThreshold.toInt()} dB")
                    }
                }

                // Check for sustained silence (possible permission/hardware issue)
                if (amplitude < -75) { // Near silence threshold
                    consecutiveSilentFrames++
                    if (consecutiveSilentFrames >= silentFrameThreshold && !hasWarnedAboutSilence) {
                        hasWarnedAboutSilence = true
                        addDebugLog("WARNING: Sustained silence detected (${consecutiveSilentFrames} frames at ${amplitude.toInt()} dB). Microphone may not be capturing audio properly.")
                        addDebugLog("TIP: Try restarting the app after granting microphone permission, or check if another app is using the microphone.")
                    }
                } else {
                    consecutiveSilentFrames = 0 // Reset if we get actual audio
                    hasWarnedAboutSilence = false
                }

                // Log first few frames with more detail to help debug
                if (frameCount <= 5) {
                    val maxSample = buffer.take(readCount).maxOrNull() ?: 0
                    val minSample = buffer.take(readCount).minOrNull() ?: 0
                    addDebugLog("Frame $frameCount: amplitude=${amplitude.toInt()} dB, samples read=$readCount, range=[$minSample, $maxSample]")
                }

                // Log amplitude periodically (every 3 seconds) to show the loop is working
                val now = System.currentTimeMillis()
                if (now - lastLogTime >= 3000) {
                    addDebugLog("Audio processing active: frame=$frameCount, amplitude=${amplitude.toInt()} dB, threshold=${speechThreshold.toInt()} dB, speaking=$isSpeaking")
                    lastLogTime = now
                }

                val isSpeechFrame = amplitude > speechThreshold

                if (isSpeechFrame) {
                    lastSpeechTime = System.currentTimeMillis()

                    if (!isSpeaking) {
                        // Speech just started
                        isSpeaking = true
                        speechStartTime = System.currentTimeMillis()
                        audioChunks.clear()
                        _isSpeechDetected.value = true
                        _speechEvents.send(SpeechEvent.SpeechStarted)
                        addDebugLog("🎤 Speech STARTED (amplitude: ${amplitude.toInt()} dB, threshold: ${speechThreshold.toInt()} dB)")
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
                                addDebugLog("🎤 Speech ENDED: ${speechDuration}ms, ${audioFile.length()} bytes")
                                _speechEvents.send(SpeechEvent.SpeechEnded(audioFile, speechDuration))
                            } else {
                                addDebugLog("ERROR: Failed to save audio file")
                                _speechEvents.send(SpeechEvent.Error("Failed to save audio"))
                            }
                        } else {
                            addDebugLog("Speech too short (${speechDuration}ms), ignored")
                        }

                        audioChunks.clear()
                    }
                }
            }
        }

        // Log why the loop exited
        addDebugLog("Audio loop exited: isListening=${_isListening.value}, isActive=$isActive, audioRecord=${audioRecord != null}")
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
