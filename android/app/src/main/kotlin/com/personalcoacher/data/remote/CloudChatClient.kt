package com.personalcoacher.data.remote

import android.util.Log
import com.google.gson.Gson
import com.personalcoacher.data.remote.dto.ChatJobStatusResponse
import com.personalcoacher.data.remote.dto.CloudChatMessage
import com.personalcoacher.data.remote.dto.CloudChatRequest
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
 * Client for server-side non-streaming chat (WhatsApp-style).
 *
 * This client connects to the server's /api/coach/chat endpoint which:
 * 1. Creates a ChatJob record in the database
 * 2. Returns immediately with a job ID
 * 3. Processes Claude request in background
 * 4. Client polls /api/coach/status/{jobId} for completion
 *
 * This avoids Android 15+ network restriction issues with SSE streaming.
 */
@Singleton
class CloudChatClient @Inject constructor(
    @Named("baseOkHttp") private val baseOkHttpClient: OkHttpClient,
    @Named("apiBaseUrl") private val apiBaseUrl: String
) {
    private val gson = Gson()

    companion object {
        private const val TAG = "CloudChatClient"
    }

    /**
     * Result types for cloud chat operations.
     */
    sealed class CloudChatResult {
        /** Job created successfully */
        data class JobCreated(
            val jobId: String,
            val statusUrl: String,
            val existing: Boolean = false
        ) : CloudChatResult()

        /** An error occurred */
        data class Error(val message: String) : CloudChatResult()
    }

    /**
     * Starts a cloud chat job (fire-and-forget).
     * Returns immediately with a job ID - client should poll for completion.
     *
     * @param conversationId The conversation ID
     * @param messageId The assistant message ID to fill
     * @param messages The conversation history
     * @param fcmToken Optional FCM token for push notification when complete
     * @param systemContext Optional additional context (journal entries, etc.)
     */
    suspend fun startChatJob(
        conversationId: String,
        messageId: String,
        messages: List<CloudChatMessage>,
        fcmToken: String? = null,
        systemContext: String? = null,
        debugCallback: ((String) -> Unit)? = null
    ): CloudChatResult {
        fun debugLog(message: String) {
            Log.d(TAG, message)
            debugCallback?.invoke("[${System.currentTimeMillis()}] $message")
        }

        debugLog("Starting cloud chat job")
        debugLog("conversationId=$conversationId, messageId=$messageId")
        debugLog("messages count=${messages.size}")

        return withContext(Dispatchers.IO) {
            try {
                val request = CloudChatRequest(
                    conversationId = conversationId,
                    messageId = messageId,
                    messages = messages,
                    fcmToken = fcmToken,
                    systemContext = systemContext
                )

                val jsonBody = gson.toJson(request)
                debugLog("Request body: ${jsonBody.take(300)}...")

                val httpRequest = Request.Builder()
                    .url("$apiBaseUrl/api/coach/chat")
                    .addHeader("content-type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = baseOkHttpClient.newCall(httpRequest).execute()
                debugLog("Response received: ${response.code}")

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    debugLog("Response body: $body")

                    val result = gson.fromJson(body, StartChatResponse::class.java)
                    debugLog("Job created: jobId=${result.jobId}, existing=${result.existing}")

                    CloudChatResult.JobCreated(
                        jobId = result.jobId,
                        statusUrl = result.statusUrl ?: "/api/coach/status/${result.jobId}",
                        existing = result.existing
                    )
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    debugLog("Error response: $errorBody")
                    CloudChatResult.Error("API error: ${response.code} - $errorBody")
                }
            } catch (e: java.net.UnknownHostException) {
                debugLog("DNS resolution failed: ${e.message}")
                CloudChatResult.Error("Unable to connect - check your internet connection")
            } catch (e: java.net.SocketException) {
                debugLog("Socket error: ${e.message}")
                CloudChatResult.Error("Network connection error - ${e.message}")
            } catch (e: Exception) {
                debugLog("Exception: ${e.javaClass.simpleName}: ${e.message}")
                CloudChatResult.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    /**
     * Polls for chat job status.
     * Used to check if the job is complete and get the response.
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
            } catch (e: java.net.UnknownHostException) {
                Log.w(TAG, "DNS resolution failed for job status check: ${e.message}")
                Result.failure(Exception("Unable to resolve host - network may be recovering"))
            } catch (e: java.net.SocketException) {
                Log.w(TAG, "Socket error for job status check: ${e.message}")
                Result.failure(Exception("Network connection error - ${e.message}"))
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
     * Response from starting a chat job
     */
    private data class StartChatResponse(
        val jobId: String,
        val statusUrl: String? = null,
        val existing: Boolean = false,
        val status: String? = null
    )
}
