package com.personalcoacher.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.personalcoacher.data.remote.dto.ChatJobStatusResponse
import com.personalcoacher.data.remote.dto.CloudChatMessage
import com.personalcoacher.data.remote.dto.CloudChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Client for server-side buffered streaming chat via Edge Functions.
 *
 * This client connects to the server's /api/coach/chat endpoint which:
 * 1. Creates a ChatJob record in the database
 * 2. Starts Claude streaming in the background
 * 3. Returns SSE events as chunks arrive
 * 4. Buffers all chunks in the database
 * 5. If client disconnects, streaming continues on server
 * 6. Client can reconnect and poll /api/coach/status/{jobId} for buffered content
 */
@Singleton
class CloudChatClient @Inject constructor(
    @Named("baseOkHttp") private val baseOkHttpClient: OkHttpClient,
    @Named("apiBaseUrl") private val apiBaseUrl: String
) {
    private val gson = Gson()

    // Create a client specifically for SSE with longer timeouts
    private val sseClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for SSE
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "CloudChatClient"
    }

    /**
     * Result types for cloud chat streaming.
     */
    sealed class CloudStreamResult {
        /** Job created successfully, streaming starting */
        data class Started(val jobId: String) : CloudStreamResult()
        /** A chunk of text from the AI response */
        data class TextDelta(val text: String, val jobId: String) : CloudStreamResult()
        /** Streaming completed successfully */
        data class Complete(val content: String, val jobId: String) : CloudStreamResult()
        /** An error occurred */
        data class Error(val message: String, val jobId: String? = null) : CloudStreamResult()
    }

    /**
     * Starts a cloud chat session with server-side buffering.
     * Returns a Flow that emits streaming events.
     *
     * @param conversationId The conversation ID
     * @param messageId The assistant message ID to fill
     * @param messages The conversation history
     * @param fcmToken Optional FCM token for push notification when complete
     * @param systemContext Optional additional context (journal entries, etc.)
     */
    fun streamChat(
        conversationId: String,
        messageId: String,
        messages: List<CloudChatMessage>,
        fcmToken: String? = null,
        systemContext: String? = null,
        debugCallback: ((String) -> Unit)? = null
    ): Flow<CloudStreamResult> = callbackFlow {
        fun debugLog(message: String) {
            Log.d(TAG, message)
            debugCallback?.invoke("[${System.currentTimeMillis()}] $message")
        }

        debugLog("Starting cloud chat request")
        debugLog("conversationId=$conversationId, messageId=$messageId")
        debugLog("messages count=${messages.size}")

        val request = CloudChatRequest(
            conversationId = conversationId,
            messageId = messageId,
            messages = messages,
            fcmToken = fcmToken,
            systemContext = systemContext
        )

        val jsonBody = gson.toJson(request)
        debugLog("Request body: ${jsonBody.take(500)}...")

        val httpRequest = Request.Builder()
            .url("$apiBaseUrl/api/coach/chat")
            .addHeader("content-type", "application/json")
            .addHeader("accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = sseClient.newCall(httpRequest)

        try {
            val result = withContext(Dispatchers.IO) {
                var reader: BufferedReader? = null
                var jobId: String? = null
                var fullContent = StringBuilder()

                try {
                    val response = call.execute()
                    debugLog("Response received: ${response.code} ${response.message}")

                    // Get job ID from header
                    jobId = response.header("X-Job-Id")
                    debugLog("Job ID from header: $jobId")

                    if (jobId != null) {
                        send(CloudStreamResult.Started(jobId))
                    }

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        debugLog("Error response body: $errorBody")
                        send(CloudStreamResult.Error("API error: ${response.code} - $errorBody", jobId))
                        return@withContext false
                    }

                    val inputStream = response.body?.byteStream()
                    if (inputStream == null) {
                        debugLog("Empty response body")
                        send(CloudStreamResult.Error("Empty response body", jobId))
                        return@withContext false
                    }

                    debugLog("Starting to read SSE stream")
                    reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 256)
                    var line: String?

                    while (true) {
                        line = reader.readLine()
                        if (line == null) {
                            debugLog("End of stream reached")
                            break
                        }

                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isNotEmpty()) {
                                try {
                                    val json = JsonParser.parseString(data).asJsonObject
                                    val type = json.get("type")?.asString
                                    val eventJobId = json.get("jobId")?.asString ?: jobId

                                    when (type) {
                                        "delta" -> {
                                            val text = json.get("text")?.asString ?: ""
                                            fullContent.append(text)
                                            send(CloudStreamResult.TextDelta(text, eventJobId ?: ""))
                                        }
                                        "complete" -> {
                                            val content = json.get("content")?.asString ?: fullContent.toString()
                                            debugLog("Stream complete, content length: ${content.length}")
                                            send(CloudStreamResult.Complete(content, eventJobId ?: ""))
                                            return@withContext true
                                        }
                                        "error" -> {
                                            val error = json.get("error")?.asString ?: "Unknown error"
                                            debugLog("Stream error: $error")
                                            send(CloudStreamResult.Error(error, eventJobId))
                                            return@withContext false
                                        }
                                    }
                                } catch (e: Exception) {
                                    debugLog("Failed to parse SSE event: ${e.message}")
                                }
                            }
                        }
                    }

                    // If we reach here without a complete event, still send what we have
                    if (fullContent.isNotEmpty()) {
                        debugLog("Stream ended without complete event, sending accumulated content")
                        send(CloudStreamResult.Complete(fullContent.toString(), jobId ?: ""))
                    }

                    true
                } catch (e: Exception) {
                    debugLog("Exception: ${e.javaClass.simpleName}: ${e.message}")
                    if (!call.isCanceled()) {
                        send(CloudStreamResult.Error(e.localizedMessage ?: "Network error", jobId))
                    }
                    false
                } finally {
                    try {
                        reader?.close()
                    } catch (_: Exception) {}
                }
            }

            channel.close()
        } catch (e: Exception) {
            debugLog("Outer exception: ${e.javaClass.simpleName}: ${e.message}")
            if (!call.isCanceled()) {
                trySend(CloudStreamResult.Error(e.localizedMessage ?: "Streaming error"))
            }
            channel.close()
        }

        awaitClose {
            call.cancel()
        }
    }

    /**
     * Polls for chat job status when reconnecting after disconnect.
     * Useful when the app went to background and needs to catch up.
     *
     * @param jobId The job ID to check
     * @return The current job status and buffered content
     */
    suspend fun getJobStatus(jobId: String): Result<ChatJobStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val httpRequest = Request.Builder()
                    .url("$apiBaseUrl/api/coach/status/$jobId")
                    .addHeader("content-type", "application/json")
                    .get()
                    .build()

                val response = baseOkHttpClient.newCall(httpRequest).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val status = gson.fromJson(body, ChatJobStatusResponse::class.java)
                    Result.success(status)
                } else {
                    Result.failure(Exception("Failed to get job status: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Marks the client as disconnected so the server can send a push notification.
     * Call this when the app goes to background during streaming.
     */
    suspend fun markDisconnected(jobId: String, fcmToken: String? = null): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildMap {
                    put("clientConnected", false)
                    if (fcmToken != null) put("fcmToken", fcmToken)
                }
                val jsonBody = gson.toJson(body)

                val httpRequest = Request.Builder()
                    .url("$apiBaseUrl/api/coach/status/$jobId")
                    .addHeader("content-type", "application/json")
                    .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = baseOkHttpClient.newCall(httpRequest).execute()

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to mark disconnected: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Polls for job completion with retries.
     * Useful when reconnecting after the app was backgrounded.
     */
    suspend fun pollUntilComplete(
        jobId: String,
        maxAttempts: Int = 60,
        delayMs: Long = 2000
    ): Result<String> {
        repeat(maxAttempts) { attempt ->
            val result = getJobStatus(jobId)

            result.onSuccess { status ->
                when (status.status) {
                    "COMPLETED" -> return Result.success(status.buffer)
                    "FAILED" -> return Result.failure(Exception(status.error ?: "Job failed"))
                }
            }

            result.onFailure {
                if (attempt == maxAttempts - 1) {
                    return Result.failure(it)
                }
            }

            delay(delayMs)
        }

        return Result.failure(Exception("Polling timed out"))
    }
}
