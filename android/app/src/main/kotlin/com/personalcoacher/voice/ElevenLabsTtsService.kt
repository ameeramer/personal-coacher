package com.personalcoacher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Text-to-Speech service using ElevenLabs API.
 * Provides natural-sounding voice synthesis for the journaling coach.
 *
 * Features:
 * - Streaming audio playback for low latency
 * - Multiple voice options
 * - Voice emotion/style support
 * - Audio file caching
 */
@Singleton
class ElevenLabsTtsService @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("claudeOkHttp") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ElevenLabsTtsService"
        private const val BASE_URL = "https://api.elevenlabs.io/v1"

        // Default voice - Rachel (warm, conversational female voice)
        // Other options: "21m00Tcm4TlvDq8ikWAM" (Rachel), "EXAVITQu4vr4xnSDxMaL" (Bella)
        // "MF3mGyEYCl7XYWbV9V6O" (Elli), "TxGEqnHWrfWFTfGW9XjX" (Josh)
        const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel

        // Model options
        const val MODEL_MULTILINGUAL_V2 = "eleven_multilingual_v2" // Best quality, slower
        const val MODEL_TURBO_V2_5 = "eleven_turbo_v2_5" // Fast, good quality
        const val MODEL_FLASH = "eleven_flash_v2_5" // Fastest, lower quality
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: Flow<Boolean> = _isSpeaking.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false) // Default to earpiece for phone call experience
    val isSpeakerOn: Flow<Boolean> = _isSpeakerOn.asStateFlow()

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var currentAudioTrack: AudioTrack? = null
    private var currentMediaPlayer: android.media.MediaPlayer? = null
    private val audioDir: File by lazy {
        File(context.cacheDir, "tts_audio").apply { mkdirs() }
    }

    /**
     * Result of TTS operation
     */
    sealed class TtsResult {
        data class Success(val audioFile: File) : TtsResult()
        data class Error(val message: String) : TtsResult()
    }

    /**
     * Generates speech from text using ElevenLabs API.
     *
     * @param apiKey ElevenLabs API key
     * @param text Text to convert to speech
     * @param voiceId Voice ID to use (default: Rachel)
     * @param modelId Model to use (default: turbo for balance of speed/quality)
     * @param stability Voice stability (0.0-1.0, default: 0.5)
     * @param similarityBoost Voice clarity (0.0-1.0, default: 0.75)
     * @return TtsResult with audio file or error
     */
    suspend fun generateSpeech(
        apiKey: String,
        text: String,
        voiceId: String = DEFAULT_VOICE_ID,
        modelId: String = MODEL_TURBO_V2_5,
        stability: Float = 0.5f,
        similarityBoost: Float = 0.75f
    ): TtsResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating speech for text (${text.length} chars)")

            val requestBody = JSONObject().apply {
                put("text", text)
                put("model_id", modelId)
                put("voice_settings", JSONObject().apply {
                    put("stability", stability)
                    put("similarity_boost", similarityBoost)
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/text-to-speech/$voiceId")
                .addHeader("xi-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "ElevenLabs API error: ${response.code} - $errorBody")
                return@withContext when {
                    response.code == 401 -> TtsResult.Error("Invalid ElevenLabs API key. Please check your settings.")
                    response.code == 429 -> TtsResult.Error("Rate limit exceeded. Please try again later.")
                    errorBody.contains("quota") -> TtsResult.Error("ElevenLabs quota exceeded.")
                    else -> TtsResult.Error("TTS error: ${response.code}")
                }
            }

            // Save audio to file
            val audioFile = File(audioDir, "tts_${System.currentTimeMillis()}.mp3")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(audioFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Speech generated successfully: ${audioFile.length()} bytes")
            TtsResult.Success(audioFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating speech", e)
            TtsResult.Error(e.localizedMessage ?: "Failed to generate speech")
        }
    }

    /**
     * Generates speech and immediately plays it.
     * This is a convenience method for the voice call flow.
     *
     * @param apiKey ElevenLabs API key
     * @param text Text to speak
     * @param onComplete Callback when playback completes
     */
    suspend fun speakText(
        apiKey: String,
        text: String,
        voiceId: String = DEFAULT_VOICE_ID,
        onComplete: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "speakText called with ${text.length} chars")
        val result = generateSpeech(apiKey, text, voiceId)

        when (result) {
            is TtsResult.Success -> {
                Log.d(TAG, "Speech generated successfully, playing audio file")
                withContext(Dispatchers.Main) {
                    playAudioFile(result.audioFile, onComplete)
                }
            }
            is TtsResult.Error -> {
                Log.e(TAG, "Failed to generate speech: ${result.message}")
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    /**
     * Generates speech and plays it, suspending until playback completes.
     * Use this for strict turn-taking conversations where you need to
     * ensure speech has finished before continuing.
     *
     * @param apiKey ElevenLabs API key
     * @param text Text to speak
     * @param voiceId Voice ID to use
     * @return true if speech was played successfully, false otherwise
     */
    suspend fun speakTextAndWait(
        apiKey: String,
        text: String,
        voiceId: String = DEFAULT_VOICE_ID
    ): Boolean {
        Log.d(TAG, "speakTextAndWait called with ${text.length} chars")

        // Generate speech on IO dispatcher
        val result = withContext(Dispatchers.IO) {
            generateSpeech(apiKey, text, voiceId)
        }

        return when (result) {
            is TtsResult.Success -> {
                Log.d(TAG, "Speech generated successfully, playing audio file and waiting...")
                // Use suspendCancellableCoroutine to wait for playback to complete
                suspendCancellableCoroutine { continuation ->
                    // playAudioFile must be called on Main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        playAudioFile(result.audioFile) {
                            Log.d(TAG, "speakTextAndWait: playback completed")
                            if (continuation.isActive) {
                                continuation.resume(true) {}
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        Log.d(TAG, "speakTextAndWait: cancelled, stopping playback")
                        stopSpeaking()
                    }
                }
            }
            is TtsResult.Error -> {
                Log.e(TAG, "Failed to generate speech: ${result.message}")
                false
            }
        }
    }

    /**
     * Toggles between speaker and earpiece output.
     * Sets MODE_IN_COMMUNICATION for earpiece routing, MODE_NORMAL for speaker.
     */
    fun toggleSpeaker(): Boolean {
        val newState = !_isSpeakerOn.value
        _isSpeakerOn.value = newState
        if (newState) {
            // Speaker on: set mode first, then enable speaker
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        } else {
            // Earpiece: set mode and disable speaker
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        }
        Log.d(TAG, "Speaker mode: ${if (newState) "ON" else "OFF"}, audio mode: ${audioManager.mode}")
        return newState
    }

    /**
     * Sets the speaker state directly.
     */
    fun setSpeakerOn(enabled: Boolean) {
        _isSpeakerOn.value = enabled
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = enabled
        Log.d(TAG, "Speaker mode set to: ${if (enabled) "ON" else "OFF"}")
    }

    /**
     * Plays an audio file through the earpiece or speaker.
     */
    fun playAudioFile(audioFile: File, onComplete: () -> Unit = {}) {
        try {
            _isSpeaking.value = true

            // Ensure speaker state is applied
            audioManager.isSpeakerphoneOn = _isSpeakerOn.value

            currentMediaPlayer?.release()
            currentMediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isSpeaking.value = false
                    it.release()
                    currentMediaPlayer = null
                    onComplete()
                    // Clean up audio file after playback
                    audioFile.delete()
                }
                setOnErrorListener { mp, _, _ ->
                    _isSpeaking.value = false
                    mp.release()
                    currentMediaPlayer = null
                    onComplete()
                    true
                }
                start()
            }

            Log.d(TAG, "Playing audio: ${audioFile.name} (speaker: ${_isSpeakerOn.value})")

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            _isSpeaking.value = false
            onComplete()
        }
    }

    /**
     * Stops any currently playing audio.
     */
    fun stopSpeaking() {
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
            currentAudioTrack = null
            currentMediaPlayer?.stop()
            currentMediaPlayer?.release()
            currentMediaPlayer = null
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
    }

    /**
     * Streams speech directly from ElevenLabs API for lower latency.
     * Audio starts playing as soon as the first chunks arrive from the server,
     * rather than waiting for the entire audio to be generated.
     *
     * Uses PCM format with AudioTrack for direct streaming playback.
     *
     * @param apiKey ElevenLabs API key
     * @param text Text to speak
     * @param voiceId Voice ID to use
     * @return true if speech was played successfully, false otherwise
     */
    suspend fun speakTextStreaming(
        apiKey: String,
        text: String,
        voiceId: String = DEFAULT_VOICE_ID
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "speakTextStreaming called with ${text.length} chars")

        try {
            _isSpeaking.value = true

            // Use PCM 24kHz for good quality with lower latency than MP3
            // ElevenLabs supports: pcm_16000, pcm_22050, pcm_24000, pcm_44100
            val sampleRate = 24000
            val outputFormat = "pcm_24000"

            val requestBody = JSONObject().apply {
                put("text", text)
                put("model_id", MODEL_TURBO_V2_5) // Fast model for streaming
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/text-to-speech/$voiceId/stream?output_format=$outputFormat")
                .addHeader("xi-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/pcm")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Starting streaming TTS request...")

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "ElevenLabs streaming API error: ${response.code} - $errorBody")
                _isSpeaking.value = false
                return@withContext false
            }

            // Set up AudioTrack for PCM playback
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Ensure speaker state is applied
            withContext(Dispatchers.Main) {
                audioManager.isSpeakerphoneOn = _isSpeakerOn.value
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize * 2) // Double buffer for smoother playback
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            currentAudioTrack = audioTrack
            audioTrack.play()

            Log.d(TAG, "AudioTrack started, streaming audio...")

            // Stream audio chunks directly to AudioTrack
            val inputStream = response.body?.byteStream()
            if (inputStream != null) {
                val buffer = ByteArray(bufferSize)
                var bytesRead: Int
                var totalBytes = 0
                var firstChunkTime: Long? = null

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!coroutineContext.isActive) {
                        Log.d(TAG, "Streaming cancelled")
                        break
                    }

                    if (firstChunkTime == null) {
                        firstChunkTime = System.currentTimeMillis()
                        Log.d(TAG, "First audio chunk received, starting playback!")
                    }

                    audioTrack.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }

                Log.d(TAG, "Streaming complete: $totalBytes bytes streamed")
                inputStream.close()
            }

            // Wait for AudioTrack to finish playing remaining buffer
            // Estimate remaining playback time based on buffer
            val remainingFrames = audioTrack.playbackHeadPosition
            if (remainingFrames > 0) {
                // Give time for buffer to play out
                kotlinx.coroutines.delay(200)
            }

            audioTrack.stop()
            audioTrack.release()
            currentAudioTrack = null

            _isSpeaking.value = false
            Log.d(TAG, "Streaming TTS playback completed")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming TTS", e)
            currentAudioTrack?.release()
            currentAudioTrack = null
            _isSpeaking.value = false
            false
        }
    }

    /**
     * Cleans up cached audio files.
     */
    fun clearCache() {
        try {
            audioDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "TTS cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}
