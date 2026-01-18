package com.personalcoacher.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.personalcoacher.data.local.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Voyage AI Embedding Service
 *
 * Generates embeddings using Voyage AI's voyage-4 model.
 * The user has opted out of data retention, meaning:
 * - Text is processed to generate embeddings
 * - Embeddings are returned immediately
 * - Text is deleted immediately (zero-day retention)
 * - Data is NOT used for model training
 *
 * @see <a href="https://docs.voyageai.com/docs/embeddings">Voyage AI Embeddings</a>
 */
@Singleton
class VoyageEmbeddingService @Inject constructor(
    @Named("voyageOkHttp") private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
    private val gson: Gson
) {
    companion object {
        private const val BASE_URL = "https://api.voyageai.com/v1"
        private const val MODEL = "voyage-3-large"
        const val MODEL_VERSION = "voyage-3-large-2025-01"
        const val EMBEDDING_DIMENSIONS = 1024
    }

    /**
     * Generate embedding for a single text.
     *
     * @param text The text to embed
     * @param inputType The type of input: "document" for content being indexed, "query" for search queries
     * @return FloatArray of embedding values (1024 dimensions for voyage-3-large)
     * @throws VoyageApiException if the API call fails
     */
    suspend fun embed(text: String, inputType: String = "document"): FloatArray = withContext(Dispatchers.IO) {
        val apiKey = tokenManager.getVoyageApiKeySync()
            ?: throw VoyageApiException("Voyage API key not configured")

        val request = VoyageEmbedRequest(
            input = listOf(text),
            model = MODEL,
            inputType = inputType
        )

        val response = makeRequest(apiKey, request)
        response.data.firstOrNull()?.embedding?.toFloatArray()
            ?: throw VoyageApiException("No embedding returned from Voyage API")
    }

    /**
     * Generate embeddings for multiple texts in a batch.
     *
     * @param texts List of texts to embed (max 128 texts per batch)
     * @param inputType The type of input: "document" for content being indexed, "query" for search queries
     * @return List of FloatArray embeddings in the same order as input texts
     * @throws VoyageApiException if the API call fails
     */
    suspend fun embedBatch(texts: List<String>, inputType: String = "document"): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        val apiKey = tokenManager.getVoyageApiKeySync()
            ?: throw VoyageApiException("Voyage API key not configured")

        // Voyage API supports up to 128 texts per batch
        val batches = texts.chunked(128)
        val allEmbeddings = mutableListOf<FloatArray>()

        for (batch in batches) {
            val request = VoyageEmbedRequest(
                input = batch,
                model = MODEL,
                inputType = inputType
            )

            val response = makeRequest(apiKey, request)
            // Sort by index to ensure correct order
            val sortedEmbeddings = response.data
                .sortedBy { it.index }
                .map { it.embedding.toFloatArray() }

            allEmbeddings.addAll(sortedEmbeddings)
        }

        allEmbeddings
    }

    private fun makeRequest(apiKey: String, voyageRequest: VoyageEmbedRequest): VoyageEmbedResponse {
        val json = gson.toJson(voyageRequest)
        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/embeddings")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            val errorMessage = try {
                val errorResponse = gson.fromJson(responseBody, VoyageErrorResponse::class.java)
                errorResponse.detail ?: "Unknown error"
            } catch (e: Exception) {
                responseBody ?: "Unknown error"
            }

            when (response.code) {
                401 -> throw VoyageApiException("Invalid Voyage API key")
                429 -> throw VoyageApiException("Rate limit exceeded. Please try again later.")
                else -> throw VoyageApiException("Voyage API error: $errorMessage")
            }
        }

        return gson.fromJson(responseBody, VoyageEmbedResponse::class.java)
            ?: throw VoyageApiException("Failed to parse Voyage API response")
    }

    fun getModelVersion(): String = MODEL_VERSION
}

// Request/Response DTOs

data class VoyageEmbedRequest(
    val input: List<String>,
    val model: String,
    @SerializedName("input_type")
    val inputType: String = "document"
)

data class VoyageEmbedResponse(
    val `object`: String,
    val data: List<VoyageEmbeddingData>,
    val model: String,
    val usage: VoyageUsage
)

data class VoyageEmbeddingData(
    val `object`: String,
    val embedding: List<Float>,
    val index: Int
)

data class VoyageUsage(
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class VoyageErrorResponse(
    val detail: String?
)

class VoyageApiException(message: String) : Exception(message)
