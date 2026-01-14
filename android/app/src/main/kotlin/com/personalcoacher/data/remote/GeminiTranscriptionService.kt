package com.personalcoacher.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.personalcoacher.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranscriptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GeminiTranscription"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val CUSTOM_MODEL_ID = "custom"

        val AVAILABLE_MODELS = listOf(
            GeminiModel("gemini-3-pro-preview", "Gemini 3 Pro"),
            GeminiModel("gemini-3-flash-preview", "Gemini 3 Flash"),
            GeminiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
            GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash (Default)"),
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

    suspend fun transcribeAudio(audioFile: File, mimeType: String = "audio/mp4"): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                val model = getOrCreateModel()

                // Read the audio file and encode to base64
                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                Log.d(TAG, "Transcribing audio file: ${audioFile.name}, size: ${audioBytes.size} bytes")

                val response = model.generateContent(
                    content {
                        blob(mimeType, audioBytes)
                        text("""
                            Please transcribe this audio recording.
                            Detect the language automatically and transcribe in the original language.
                            Only output the transcription text, nothing else.
                            If there is no speech or the audio is silent, respond with "[No speech detected]".
                        """.trimIndent())
                    }
                )

                val transcribedText = response.text?.trim() ?: "[No transcription returned]"
                Log.d(TAG, "Transcription completed: ${transcribedText.take(100)}...")

                TranscriptionResult.Success(transcribedText)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                TranscriptionResult.Error(e.message ?: "Unknown error occurred during transcription")
            }
        }
    }

    suspend fun transcribeAudioBytes(audioBytes: ByteArray, mimeType: String = "audio/mp4"): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                val model = getOrCreateModel()

                Log.d(TAG, "Transcribing audio bytes, size: ${audioBytes.size} bytes")

                val response = model.generateContent(
                    content {
                        blob(mimeType, audioBytes)
                        text("""
                            Please transcribe this audio recording.
                            Detect the language automatically and transcribe in the original language.
                            Only output the transcription text, nothing else.
                            If there is no speech or the audio is silent, respond with "[No speech detected]".
                        """.trimIndent())
                    }
                )

                val transcribedText = response.text?.trim() ?: "[No transcription returned]"
                Log.d(TAG, "Transcription completed: ${transcribedText.take(100)}...")

                TranscriptionResult.Success(transcribedText)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                TranscriptionResult.Error(e.message ?: "Unknown error occurred during transcription")
            }
        }
    }

    sealed class TranscriptionResult {
        data class Success(val text: String) : TranscriptionResult()
        data class Error(val message: String) : TranscriptionResult()
    }
}
