package com.personalcoacher.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Client for streaming Claude API responses using Server-Sent Events (SSE).
 * This enables real-time display of AI responses as they're generated.
 */
@Singleton
class ClaudeStreamingClient @Inject constructor(
    @Named("claudeOkHttp") private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    /**
     * Sends a message to Claude API and streams the response as text deltas.
     * @param apiKey The Claude API key
     * @param request The message request (stream flag will be set to true)
     * @return Flow emitting text chunks as they arrive
     */
    fun streamMessage(
        apiKey: String,
        request: ClaudeMessageRequest
    ): Flow<StreamingResult> = callbackFlow {
        val streamingRequest = request.copy(stream = true)
        val jsonBody = gson.toJson(streamingRequest)

        val httpRequest = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .addHeader("accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = okHttpClient.newCall(httpRequest)

        // Launch the blocking IO work in a separate coroutine
        val job = CoroutineScope(Dispatchers.IO).launch {
            var reader: BufferedReader? = null
            try {
                val response = call.execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    val errorMessage = if (errorBody.contains("invalid_api_key")) {
                        "Invalid API key. Please check your Claude API key in Settings."
                    } else {
                        "API error: ${response.code} - ${response.message}"
                    }
                    trySend(StreamingResult.Error(errorMessage))
                    channel.close()
                    return@launch
                }

                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    trySend(StreamingResult.Error("Empty response body"))
                    channel.close()
                    return@launch
                }

                reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                var currentEvent: String? = null

                while (reader.readLine().also { line = it } != null) {
                    if (!isActive) break // Check if flow was cancelled

                    val lineContent = line ?: continue

                    when {
                        lineContent.startsWith("event:") -> {
                            currentEvent = lineContent.removePrefix("event:").trim()
                        }
                        lineContent.startsWith("data:") -> {
                            val data = lineContent.removePrefix("data:").trim()
                            if (data.isNotEmpty()) {
                                processStreamEvent(currentEvent, data)?.let { result ->
                                    val sendResult = trySend(result)
                                    if (sendResult.isFailure) {
                                        // Channel was closed, stop reading
                                        return@launch
                                    }
                                    if (result is StreamingResult.Complete || result is StreamingResult.Error) {
                                        channel.close()
                                        return@launch
                                    }
                                }
                            }
                        }
                    }
                }

                // If we reach here without a Complete event, send one
                trySend(StreamingResult.Complete)
                channel.close()

            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    trySend(StreamingResult.Error(e.localizedMessage ?: "Network error"))
                }
                channel.close()
            } finally {
                try {
                    reader?.close()
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            job.cancel()
            call.cancel()
        }
    }

    private fun processStreamEvent(eventType: String?, data: String): StreamingResult? {
        return try {
            when (eventType) {
                "message_start" -> {
                    // Message started, nothing to emit yet
                    null
                }
                "content_block_start" -> {
                    // Content block started
                    null
                }
                "content_block_delta" -> {
                    // Parse the delta to extract text
                    val json = JsonParser.parseString(data).asJsonObject
                    val delta = json.getAsJsonObject("delta")
                    val text = delta?.get("text")?.asString
                    if (!text.isNullOrEmpty()) {
                        StreamingResult.TextDelta(text)
                    } else {
                        null
                    }
                }
                "content_block_stop" -> {
                    // Content block ended
                    null
                }
                "message_delta" -> {
                    // Message is ending (contains stop_reason)
                    null
                }
                "message_stop" -> {
                    StreamingResult.Complete
                }
                "ping" -> {
                    // Keep-alive ping, ignore
                    null
                }
                "error" -> {
                    val json = JsonParser.parseString(data).asJsonObject
                    val error = json.getAsJsonObject("error")
                    val message = error?.get("message")?.asString ?: "Unknown error"
                    StreamingResult.Error(message)
                }
                else -> {
                    // Unknown event type, try to parse as content_block_delta anyway
                    // Sometimes the event type is missing
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val type = json.get("type")?.asString
                        if (type == "content_block_delta") {
                            val delta = json.getAsJsonObject("delta")
                            val text = delta?.get("text")?.asString
                            if (!text.isNullOrEmpty()) {
                                StreamingResult.TextDelta(text)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            // Log parsing error but continue streaming
            null
        }
    }
}

/**
 * Result types for streaming responses.
 */
sealed class StreamingResult {
    /** A chunk of text from the AI response */
    data class TextDelta(val text: String) : StreamingResult()
    /** Streaming completed successfully */
    object Complete : StreamingResult()
    /** An error occurred during streaming */
    data class Error(val message: String) : StreamingResult()
}
