package com.personalcoacher.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Service for direct Claude API calls (api.anthropic.com)
 * This allows the app to communicate directly with Claude without going through the backend.
 */
interface ClaudeApiService {

    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: ClaudeMessageRequest
    ): Response<ClaudeMessageResponse>
}

// Request DTOs
data class ClaudeMessageRequest(
    @SerializedName("model") val model: String = "claude-sonnet-4-20250514",
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    @SerializedName("system") val system: String? = null,
    @SerializedName("messages") val messages: List<ClaudeMessage>,
    @SerializedName("stream") val stream: Boolean = false
)

data class ClaudeMessage(
    @SerializedName("role") val role: String, // "user" or "assistant"
    @SerializedName("content") val content: String
)

// Response DTOs
data class ClaudeMessageResponse(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: List<ClaudeContentBlock>,
    @SerializedName("model") val model: String,
    @SerializedName("stop_reason") val stopReason: String?,
    @SerializedName("stop_sequence") val stopSequence: String?,
    @SerializedName("usage") val usage: ClaudeUsage
)

data class ClaudeContentBlock(
    @SerializedName("type") val type: String, // "text"
    @SerializedName("text") val text: String
)

data class ClaudeUsage(
    @SerializedName("input_tokens") val inputTokens: Int,
    @SerializedName("output_tokens") val outputTokens: Int
)

// Error response
data class ClaudeErrorResponse(
    @SerializedName("type") val type: String,
    @SerializedName("error") val error: ClaudeError
)

data class ClaudeError(
    @SerializedName("type") val type: String,
    @SerializedName("message") val message: String
)

// Streaming SSE DTOs
sealed class ClaudeStreamEvent {
    data class MessageStart(val message: ClaudeStreamMessage) : ClaudeStreamEvent()
    data class ContentBlockStart(val index: Int, val contentBlock: ClaudeStreamContentBlock) : ClaudeStreamEvent()
    data class ContentBlockDelta(val index: Int, val delta: ClaudeStreamDelta) : ClaudeStreamEvent()
    data class ContentBlockStop(val index: Int) : ClaudeStreamEvent()
    data class MessageDelta(val delta: ClaudeMessageDeltaContent, val usage: ClaudeUsage?) : ClaudeStreamEvent()
    object MessageStop : ClaudeStreamEvent()
    object Ping : ClaudeStreamEvent()
    data class Error(val error: ClaudeError) : ClaudeStreamEvent()
}

data class ClaudeStreamMessage(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("role") val role: String,
    @SerializedName("model") val model: String
)

data class ClaudeStreamContentBlock(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String = ""
)

data class ClaudeStreamDelta(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String = ""
)

data class ClaudeMessageDeltaContent(
    @SerializedName("stop_reason") val stopReason: String?
)
