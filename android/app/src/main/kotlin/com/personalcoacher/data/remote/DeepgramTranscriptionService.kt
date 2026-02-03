package com.personalcoacher.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Fast speech-to-text transcription service using Deepgram API.
 *
 * Deepgram provides significantly faster transcription compared to Gemini:
 * - Average latency: 300-500ms vs 2-3s for Gemini
 * - Nova-2 model: optimized for conversational speech
 * - Direct audio upload without base64 encoding
 *
 * API Documentation: https://developers.deepgram.com/docs/getting-started-with-pre-recorded-audio
 */
@Singleton
class DeepgramTranscriptionService @Inject constructor(
    @Named("claudeOkHttp") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "DeepgramTranscription"
        private const val BASE_URL = "https://api.deepgram.com/v1/listen"

        // Nova-2 is Deepgram's fastest and most accurate model for conversational speech
        private const val MODEL = "nova-2"
    }

    /**
     * Result of transcription operation
     */
    sealed class TranscriptionResult {
        data class Success(val text: String, val confidence: Float) : TranscriptionResult()
        data class Error(val message: String) : TranscriptionResult()
    }

    /**
     * Transcribes an audio file using Deepgram's Nova-2 model.
     *
     * @param audioFile The audio file to transcribe (WAV format supported)
     * @param apiKey Deepgram API key
     * @param language Language code (default: en for English)
     * @return TranscriptionResult with the transcribed text or error
     */
    suspend fun transcribeAudio(
        audioFile: File,
        apiKey: String,
        language: String = "en"
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists()) {
                return@withContext TranscriptionResult.Error("Audio file not found")
            }

            val fileSize = audioFile.length()
            Log.d(TAG, "Transcribing audio: ${audioFile.name} ($fileSize bytes)")

            if (fileSize < 100) {
                return@withContext TranscriptionResult.Error("Audio file too small")
            }

            // Build URL with query parameters for faster processing
            val url = buildString {
                append(BASE_URL)
                append("?model=$MODEL")
                append("&language=$language")
                append("&punctuate=true") // Add punctuation
                append("&smart_format=true") // Smart formatting for readability
            }

            // Determine content type based on file extension
            val contentType = when {
                audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                audioFile.name.endsWith(".webm", ignoreCase = true) -> "audio/webm"
                else -> "audio/wav" // Default to WAV
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Token $apiKey")
                .addHeader("Content-Type", contentType)
                .post(audioFile.asRequestBody(contentType.toMediaType()))
                .build()

            val startTime = System.currentTimeMillis()
            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Deepgram API error (${response.code}): $errorBody")
                return@withContext when (response.code) {
                    401, 403 -> TranscriptionResult.Error("Invalid Deepgram API key")
                    429 -> TranscriptionResult.Error("Rate limit exceeded. Please try again.")
                    else -> TranscriptionResult.Error("Transcription failed: ${response.code}")
                }
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                return@withContext TranscriptionResult.Error("Empty response from Deepgram")
            }

            // Parse response
            val json = JSONObject(responseBody)
            val results = json.optJSONObject("results")
            val channels = results?.optJSONArray("channels")
            val channel = channels?.optJSONObject(0)
            val alternatives = channel?.optJSONArray("alternatives")
            val alternative = alternatives?.optJSONObject(0)

            val transcript = alternative?.optString("transcript", "") ?: ""
            val confidence = alternative?.optDouble("confidence", 0.0)?.toFloat() ?: 0f

            Log.d(TAG, "Transcription completed in ${latency}ms: \"${transcript.take(50)}...\" (confidence: $confidence)")

            if (transcript.isBlank()) {
                TranscriptionResult.Error("No speech detected")
            } else {
                TranscriptionResult.Success(transcript, confidence)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            TranscriptionResult.Error(e.localizedMessage ?: "Transcription failed")
        }
    }
}
