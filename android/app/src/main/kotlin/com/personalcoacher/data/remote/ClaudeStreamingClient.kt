package com.personalcoacher.data.remote

import android.util.Log
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

    companion object {
        private const val TAG = "ClaudeStreamingClient"
    }

    /**
     * Sends a message to Claude API and streams the response as text deltas.
     * @param apiKey The Claude API key
     * @param request The message request (stream flag will be set to true)
     * @param debugMode If true, logs all SSE events for debugging
     * @param debugCallback Optional callback to receive debug log entries
     * @return Flow emitting text chunks as they arrive
     */
    fun streamMessage(
        apiKey: String,
        request: ClaudeMessageRequest,
        debugMode: Boolean = false,
        debugCallback: ((String) -> Unit)? = null
    ): Flow<StreamingResult> = callbackFlow {
        fun debugLog(message: String) {
            if (debugMode) {
                Log.d(TAG, message)
                debugCallback?.invoke("[${System.currentTimeMillis()}] $message")
            }
        }
        debugLog("Starting streaming request")
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

        debugLog("Request built, executing...")
        val call = okHttpClient.newCall(httpRequest)

        // Launch the blocking IO work in a separate coroutine
        val job = CoroutineScope(Dispatchers.IO).launch {
            var reader: BufferedReader? = null
            var lineCount = 0
            try {
                val response = call.execute()
                debugLog("Response received: ${response.code} ${response.message}")

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    debugLog("Error response body: $errorBody")
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
                    debugLog("Empty response body")
                    trySend(StreamingResult.Error("Empty response body"))
                    channel.close()
                    return@launch
                }

                debugLog("Starting to read SSE stream")
                reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                var currentEvent: String? = null

                while (reader.readLine().also { line = it } != null) {
                    lineCount++
                    if (!isActive) {
                        debugLog("Flow cancelled at line $lineCount")
                        break
                    }

                    val lineContent = line ?: continue

                    // Log raw line for debugging (truncate if too long)
                    if (lineContent.isNotEmpty()) {
                        debugLog("Line $lineCount: ${lineContent.take(200)}${if (lineContent.length > 200) "..." else ""}")
                    }

                    when {
                        lineContent.startsWith("event:") -> {
                            currentEvent = lineContent.removePrefix("event:").trim()
                            debugLog("Event type: $currentEvent")
                        }
                        lineContent.startsWith("data:") -> {
                            val data = lineContent.removePrefix("data:").trim()
                            if (data.isNotEmpty()) {
                                val result = processStreamEvent(currentEvent, data)
                                debugLog("Processed event '$currentEvent': ${result?.javaClass?.simpleName ?: "null"}")
                                result?.let { res ->
                                    val sendResult = trySend(res)
                                    if (sendResult.isFailure) {
                                        debugLog("Channel closed, stopping at line $lineCount")
                                        return@launch
                                    }
                                    if (res is StreamingResult.Complete) {
                                        debugLog("Stream complete at line $lineCount")
                                        channel.close()
                                        return@launch
                                    }
                                    if (res is StreamingResult.Error) {
                                        debugLog("Stream error at line $lineCount: ${res.message}")
                                        channel.close()
                                        return@launch
                                    }
                                }
                            }
                        }
                    }
                }

                // If we reach here without a Complete event, send one
                debugLog("Stream ended naturally at line $lineCount, sending Complete")
                trySend(StreamingResult.Complete)
                channel.close()

            } catch (e: Exception) {
                debugLog("Exception at line $lineCount: ${e.javaClass.simpleName}: ${e.message}")
                if (!call.isCanceled()) {
                    trySend(StreamingResult.Error(e.localizedMessage ?: "Network error"))
                }
                channel.close()
            } finally {
                debugLog("Cleaning up reader after $lineCount lines")
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
                    // Unknown event type, try to parse based on the "type" field in the JSON
                    // Sometimes the event: line is missing, so we need to detect the event type from the data
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val type = json.get("type")?.asString
                        when (type) {
                            "content_block_delta" -> {
                                val delta = json.getAsJsonObject("delta")
                                val text = delta?.get("text")?.asString
                                if (!text.isNullOrEmpty()) {
                                    StreamingResult.TextDelta(text)
                                } else {
                                    null
                                }
                            }
                            "message_stop" -> {
                                StreamingResult.Complete
                            }
                            "error" -> {
                                val error = json.getAsJsonObject("error")
                                val message = error?.get("message")?.asString ?: "Unknown error"
                                StreamingResult.Error(message)
                            }
                            else -> null
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
