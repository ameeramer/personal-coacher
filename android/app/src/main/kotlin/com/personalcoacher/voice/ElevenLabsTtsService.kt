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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    private val _isSpeakerOn = MutableStateFlow(true) // Default to speaker for easier use
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
     */
    fun toggleSpeaker(): Boolean {
        val newState = !_isSpeakerOn.value
        _isSpeakerOn.value = newState
        audioManager.isSpeakerphoneOn = newState
        Log.d(TAG, "Speaker mode: ${if (newState) "ON" else "OFF"}")
        return newState
    }

    /**
     * Sets the speaker state directly.
     */
    fun setSpeakerOn(enabled: Boolean) {
        _isSpeakerOn.value = enabled
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
                        .setUsage(if (_isSpeakerOn.value) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
