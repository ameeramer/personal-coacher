package com.personalcoacher.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.personalcoacher.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranscriptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GeminiTranscription"
        const val DEFAULT_MODEL = "gemini-3-pro-preview"
        const val CUSTOM_MODEL_ID = "custom"

        // Retry configuration
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 10000L
        private const val RETRY_MULTIPLIER = 2.0

        val AVAILABLE_MODELS = listOf(
            GeminiModel("gemini-3-pro-preview", "Gemini 3 Pro (Default)"),
            GeminiModel("gemini-3-flash-preview", "Gemini 3 Flash"),
            GeminiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
            GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
            GeminiModel("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
            GeminiModel(CUSTOM_MODEL_ID, "Custom Model")
        )
    }

    data class GeminiModel(val id: String, val displayName: String)

    private var generativeModel: GenerativeModel? = null
    private var currentModelId: String = DEFAULT_MODEL
    private var apiKey: String? = null

    fun setApiKey(key: String) {
        apiKey = key
        generativeModel = null // Reset model when API key changes
    }

    fun setModel(modelId: String) {
        if (modelId != currentModelId) {
            currentModelId = modelId
            generativeModel = null // Reset model when model changes
        }
    }

    private fun getOrCreateModel(): GenerativeModel {
        val key = apiKey ?: throw IllegalStateException("Gemini API key not set. Please set it in Settings.")

        return generativeModel ?: GenerativeModel(
            modelName = currentModelId,
            apiKey = key
        ).also { generativeModel = it }
    }

    /**
     * Determines if an exception is a transient network error that can be retried.
     */
    private fun isTransientError(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is UnknownHostException -> true
            is IOException -> {
                // Check for common transient network error messages
                val message = e.message?.lowercase() ?: ""
                message.contains("timeout") ||
                message.contains("connection reset") ||
                message.contains("connection refused") ||
                message.contains("network unreachable") ||
                message.contains("no route to host") ||
                message.contains("temporarily unavailable")
            }
            else -> {
                // Check if it's a server error (5xx) wrapped in an exception
                val message = e.message?.lowercase() ?: ""
                message.contains("500") ||
                message.contains("502") ||
                message.contains("503") ||
                message.contains("504") ||
                message.contains("server error") ||
                message.contains("service unavailable")
            }
        }
    }

    /**
     * Calculates the delay for the next retry using exponential backoff.
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val delay = (INITIAL_RETRY_DELAY_MS * Math.pow(RETRY_MULTIPLIER, (attempt - 1).toDouble())).toLong()
        return minOf(delay, MAX_RETRY_DELAY_MS)
    }

    suspend fun transcribeAudio(audioFile: File, mimeType: String = "audio/mp4"): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            var attempt = 0

            while (attempt < MAX_RETRIES) {
                attempt++
                try {
                    val model = getOrCreateModel()

                    // Read the audio file
                    val audioBytes = audioFile.readBytes()

                    Log.d(TAG, "Transcribing audio file: ${audioFile.name}, size: ${audioBytes.size} bytes (attempt $attempt)")

                    val response = model.generateContent(
                        content {
                            blob(mimeType, audioBytes)
                            text("""
                                Please transcribe this audio recording.
                                Detect the language automatically and transcribe in the original language.

                                IMPORTANT: If you detect multiple speakers in the audio, distinguish between them and label them as "Speaker 1:", "Speaker 2:", etc. at the start of each speaker's turn. Be consistent with speaker labels throughout the transcription.

                                Only output the transcription text with speaker labels if applicable, nothing else.
                                If there is no speech or the audio is silent, respond with "[No speech detected]".
                            """.trimIndent())
                        }
                    )

                    val transcribedText = response.text?.trim() ?: "[No transcription returned]"
                    Log.d(TAG, "Transcription completed: ${transcribedText.take(100)}...")

                    return@withContext TranscriptionResult.Success(transcribedText)
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Transcription attempt $attempt failed", e)

                    if (isTransientError(e) && attempt < MAX_RETRIES) {
                        val delayMs = calculateRetryDelay(attempt)
                        Log.d(TAG, "Transient error detected, retrying in ${delayMs}ms...")
                        delay(delayMs)
                        // Reset the model to force reconnection on retry
                        generativeModel = null
                    } else {
                        // Non-transient error or max retries reached
                        break
                    }
                }
            }

            Log.e(TAG, "Transcription failed after $attempt attempts", lastException)
            TranscriptionResult.Error(lastException?.message ?: "Unknown error occurred during transcription")
        }
    }

    suspend fun transcribeAudioBytes(audioBytes: ByteArray, mimeType: String = "audio/mp4"): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            var attempt = 0

            while (attempt < MAX_RETRIES) {
                attempt++
                try {
                    val model = getOrCreateModel()

                    Log.d(TAG, "Transcribing audio bytes, size: ${audioBytes.size} bytes (attempt $attempt)")

                    val response = model.generateContent(
                        content {
                            blob(mimeType, audioBytes)
                            text("""
                                Please transcribe this audio recording.
                                Detect the language automatically and transcribe in the original language.

                                IMPORTANT: If you detect multiple speakers in the audio, distinguish between them and label them as "Speaker 1:", "Speaker 2:", etc. at the start of each speaker's turn. Be consistent with speaker labels throughout the transcription.

                                Only output the transcription text with speaker labels if applicable, nothing else.
                                If there is no speech or the audio is silent, respond with "[No speech detected]".
                            """.trimIndent())
                        }
                    )

                    val transcribedText = response.text?.trim() ?: "[No transcription returned]"
                    Log.d(TAG, "Transcription completed: ${transcribedText.take(100)}...")

                    return@withContext TranscriptionResult.Success(transcribedText)
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Transcription attempt $attempt failed", e)

                    if (isTransientError(e) && attempt < MAX_RETRIES) {
                        val delayMs = calculateRetryDelay(attempt)
                        Log.d(TAG, "Transient error detected, retrying in ${delayMs}ms...")
                        delay(delayMs)
                        // Reset the model to force reconnection on retry
                        generativeModel = null
                    } else {
                        // Non-transient error or max retries reached
                        break
                    }
                }
            }

            Log.e(TAG, "Transcription failed after $attempt attempts", lastException)
            TranscriptionResult.Error(lastException?.message ?: "Unknown error occurred during transcription")
        }
    }

    sealed class TranscriptionResult {
        data class Success(val text: String) : TranscriptionResult()
        data class Error(val message: String) : TranscriptionResult()
    }
}
