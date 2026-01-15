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

        // Audio validation thresholds
        private const val MIN_AUDIO_FILE_SIZE_BYTES = 5000 // Minimum ~5KB for valid audio
        private const val MIN_BYTES_PER_SECOND = 1000 // Minimum expected bytes per second of audio
        private const val MAX_WORDS_PER_SECOND = 5.0 // Maximum reasonable speech rate

        // Hallucination detection patterns
        private val HALLUCINATION_PATTERNS = listOf(
            "once upon a time",
            "in a world where",
            "the quick brown fox",
            "lorem ipsum",
            "hello, how are you",
            "nice to meet you",
            "welcome to",
            "today we will",
            "let me introduce",
            "in this video",
            "subscribe to",
            "like and share",
            "thank you for watching"
        )

        // Improved transcription prompt with strict instructions against hallucination
        private val TRANSCRIPTION_PROMPT = """
            Please transcribe this audio recording accurately.
            Detect the language automatically and transcribe in the original language.

            IMPORTANT RULES:
            1. If you detect multiple speakers in the audio, distinguish between them and label them as "Speaker 1:", "Speaker 2:", etc. at the start of each speaker's turn. Be consistent with speaker labels throughout.
            2. ONLY transcribe what you can ACTUALLY HEAR in the audio. Do NOT make up, invent, or hallucinate any content.
            3. If the audio is silent, contains only noise, is corrupted, or you cannot clearly hear speech, respond ONLY with "[No speech detected]"
            4. If the audio quality is poor and you can only partially understand it, transcribe only what you can clearly hear and mark unclear parts with [inaudible].
            5. Do NOT generate fictional conversations, stories, or any content that is not present in the actual audio.
            6. If the audio sounds like background noise, static, or non-speech sounds, respond with "[No speech detected]"

            Only output the transcription text with speaker labels if applicable, nothing else.
        """.trimIndent()

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

    /**
     * Validates audio file before sending to transcription.
     * Returns null if valid, or an error message if invalid.
     */
    private fun validateAudioFile(audioFile: File, durationSeconds: Int? = null): String? {
        if (!audioFile.exists()) {
            return "Audio file does not exist"
        }

        val fileSize = audioFile.length()
        if (fileSize < MIN_AUDIO_FILE_SIZE_BYTES) {
            return "Audio file too small (${fileSize} bytes) - likely no audio captured. Please check microphone permissions and that no other app is using the microphone."
        }

        // If we know the duration, check if the file size is reasonable
        if (durationSeconds != null && durationSeconds > 0) {
            val bytesPerSecond = fileSize / durationSeconds
            if (bytesPerSecond < MIN_BYTES_PER_SECOND) {
                return "Audio file appears to contain mostly silence or corrupted data (${bytesPerSecond} bytes/sec). Recording may have failed."
            }
        }

        return null
    }

    /**
     * Validates transcription response to detect potential hallucinations.
     * Returns null if valid, or an error message if suspicious.
     */
    private fun validateTranscriptionResponse(
        transcribedText: String,
        audioFileSizeBytes: Long,
        durationSeconds: Int?
    ): String? {
        val text = transcribedText.lowercase().trim()

        // Check for "[No speech detected]" response - this is valid
        if (text == "[no speech detected]") {
            return null
        }

        // Check for hallucination patterns
        for (pattern in HALLUCINATION_PATTERNS) {
            if (text.contains(pattern)) {
                Log.w(TAG, "Detected potential hallucination pattern: '$pattern' in transcription")
                return "Transcription appears to be AI-generated content rather than actual speech. The audio may not have been captured correctly."
            }
        }

        // Check for unrealistic word count relative to duration
        if (durationSeconds != null && durationSeconds > 0) {
            val wordCount = transcribedText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            val wordsPerSecond = wordCount.toDouble() / durationSeconds

            if (wordsPerSecond > MAX_WORDS_PER_SECOND && durationSeconds > 5) {
                Log.w(TAG, "Suspiciously high word rate: $wordsPerSecond words/sec for ${durationSeconds}s of audio")
                return "Transcription contains more words than physically possible for the recording duration. The audio may not have been captured correctly."
            }
        }

        // Check for very small audio files producing long transcriptions
        if (audioFileSizeBytes < 50000 && transcribedText.length > 500) { // Less than 50KB producing 500+ chars
            Log.w(TAG, "Small audio file ($audioFileSizeBytes bytes) produced long transcription (${transcribedText.length} chars)")
            return "The audio file is too small to contain the returned transcription. Recording may have failed."
        }

        return null
    }

    suspend fun transcribeAudio(
        audioFile: File,
        mimeType: String = "audio/mp4",
        durationSeconds: Int? = null
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            // Validate audio file before processing
            val validationError = validateAudioFile(audioFile, durationSeconds)
            if (validationError != null) {
                Log.e(TAG, "Audio validation failed: $validationError")
                return@withContext TranscriptionResult.Error(validationError)
            }

            var lastException: Exception? = null
            var attempt = 0
            val audioFileSize = audioFile.length()

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
                            text(TRANSCRIPTION_PROMPT)
                        }
                    )

                    val transcribedText = response.text?.trim() ?: "[No transcription returned]"
                    Log.d(TAG, "Transcription completed: ${transcribedText.take(100)}...")

                    // Validate the transcription response for potential hallucinations
                    val responseValidationError = validateTranscriptionResponse(
                        transcribedText,
                        audioFileSize,
                        durationSeconds
                    )
                    if (responseValidationError != null) {
                        Log.e(TAG, "Transcription response validation failed: $responseValidationError")
                        return@withContext TranscriptionResult.Error(responseValidationError)
                    }

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

    suspend fun transcribeAudioBytes(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        durationSeconds: Int? = null
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            // Validate audio bytes before processing
            if (audioBytes.size < MIN_AUDIO_FILE_SIZE_BYTES) {
                val error = "Audio data too small (${audioBytes.size} bytes) - likely no audio captured. Please check microphone permissions and that no other app is using the microphone."
                Log.e(TAG, "Audio validation failed: $error")
                return@withContext TranscriptionResult.Error(error)
            }

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
                            text(TRANSCRIPTION_PROMPT)
                        }
                    )

                    val transcribedText = response.text?.trim() ?: "[No transcription returned]"
                    Log.d(TAG, "Transcription completed: ${transcribedText.take(100)}...")

                    // Validate the transcription response for potential hallucinations
                    val responseValidationError = validateTranscriptionResponse(
                        transcribedText,
                        audioBytes.size.toLong(),
                        durationSeconds
                    )
                    if (responseValidationError != null) {
                        Log.e(TAG, "Transcription response validation failed: $responseValidationError")
                        return@withContext TranscriptionResult.Error(responseValidationError)
                    }

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
