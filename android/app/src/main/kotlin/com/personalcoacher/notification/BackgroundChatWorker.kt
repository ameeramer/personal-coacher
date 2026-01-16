package com.personalcoacher.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.util.CoachPrompts
import com.personalcoacher.util.DebugLogHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Background worker that processes pending AI chat messages.
 *
 * This worker is triggered when a user sends a message and allows them to leave
 * the app while the AI generates a response. When complete, a notification is
 * sent to bring them back to the conversation.
 *
 * Flow:
 * 1. User sends message -> ChatRepository saves pending assistant message
 * 2. BackgroundChatWorker is enqueued with message ID
 * 3. Worker calls Claude API and saves response
 * 4. If user hasn't marked message as "seen", send notification
 * 5. Clicking notification opens the conversation
 */
@HiltWorker
class BackgroundChatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val journalEntryDao: JournalEntryDao,
    private val agendaItemDao: AgendaItemDao,
    private val claudeApi: ClaudeApiService,
    private val tokenManager: TokenManager,
    private val notificationHelper: NotificationHelper,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PREFIX = "background_chat_"
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_USER_ID = "user_id"
        private const val TAG = "BackgroundChatWorker"

        // DNS resolution timeout in milliseconds
        private const val DNS_TIMEOUT_MS = 5000L
    }

    /**
     * Pre-flight check to verify DNS resolution is working.
     * WorkManager's NetworkType.CONNECTED only checks if network interface is up,
     * but doesn't verify DNS is actually functional.
     * This is important because the device can report "connected" while DNS is unavailable
     * (common during Doze mode transitions).
     *
     * Note: This function performs blocking I/O and should only be called from a worker thread.
     */
    @androidx.annotation.WorkerThread
    private fun isDnsResolutionWorking(): Boolean {
        return try {
            // Try to resolve the Claude API host with a timeout
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Boolean> {
                try {
                    val addresses = InetAddress.getAllByName("api.anthropic.com")
                    addresses.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
            future.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            debugLog.log(TAG, "DNS pre-flight check timed out after ${DNS_TIMEOUT_MS}ms")
            false
        } catch (e: Exception) {
            debugLog.log(TAG, "DNS pre-flight check failed: ${e.message}")
            false
        }
    }

    override suspend fun doWork(): Result {
        // Log immediately at the very start to confirm worker is running
        debugLog.log(TAG, "=== BackgroundChatWorker STARTED ===")

        val messageId = inputData.getString(KEY_MESSAGE_ID)
        val conversationId = inputData.getString(KEY_CONVERSATION_ID)
        val userId = inputData.getString(KEY_USER_ID)

        debugLog.log(TAG, "doWork() called - messageId=$messageId, conversationId=$conversationId, userId=$userId")

        if (messageId.isNullOrBlank() || conversationId.isNullOrBlank() || userId.isNullOrBlank()) {
            debugLog.log(TAG, "Missing required parameters, aborting")
            return Result.failure()
        }

        // Pre-flight DNS check - verify network is truly functional, not just "connected"
        // This prevents attempting API calls when DNS isn't working (common in Doze mode)
        debugLog.log(TAG, "Checking DNS resolution...")
        if (!isDnsResolutionWorking()) {
            debugLog.log(TAG, "DNS resolution not working, will retry later")
            return Result.retry()
        }
        debugLog.log(TAG, "DNS check passed")

        // Check for API key
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            debugLog.log(TAG, "No Claude API key configured, marking message as failed")
            messageDao.updateMessageContent(
                id = messageId,
                content = "Please configure your Claude API key in Settings to use this feature",
                status = MessageStatus.FAILED.toApiString(),
                updatedAt = Instant.now().toEpochMilli()
            )
            return Result.failure()
        }

        return try {
            // Get the pending message
            val pendingMessage = messageDao.getMessageByIdSync(messageId)
            if (pendingMessage == null) {
                debugLog.log(TAG, "Message not found: $messageId")
                return Result.failure()
            }

            // If message is completed AND user has seen it (notificationSent = true), skip
            if (pendingMessage.status == MessageStatus.COMPLETED.toApiString() && pendingMessage.notificationSent) {
                debugLog.log(TAG, "Message completed and already seen by user: $messageId")
                return Result.success()
            }

            // If message is completed BUT user hasn't seen it (notificationSent = false),
            // we need to send a notification! Streaming completed while user was away.
            if (pendingMessage.status == MessageStatus.COMPLETED.toApiString() && !pendingMessage.notificationSent) {
                debugLog.log(TAG, "Message completed by streaming but user hasn't seen it - sending notification")

                // Mark notification as sent
                messageDao.updateNotificationSent(messageId, true)

                // Send notification
                val notificationTitle = "Coach replied"
                val notificationBody = if (pendingMessage.content.length > 100) {
                    pendingMessage.content.take(97) + "..."
                } else {
                    pendingMessage.content
                }

                debugLog.log(TAG, "Sending notification for already-completed message")
                val notifResult = notificationHelper.showChatResponseNotification(
                    title = notificationTitle,
                    body = notificationBody,
                    conversationId = conversationId
                )
                debugLog.log(TAG, "Notification result: $notifResult")

                return Result.success()
            }

            // If message failed, nothing to do
            if (pendingMessage.status == MessageStatus.FAILED.toApiString()) {
                debugLog.log(TAG, "Message already failed: $messageId")
                return Result.success()
            }

            // If notificationSent is true but message is still PENDING, user returned to app before
            // streaming/worker completed. We still need to process the message but won't send notification.
            if (pendingMessage.notificationSent && pendingMessage.status == MessageStatus.PENDING.toApiString()) {
                debugLog.log(TAG, "User returned to app but message still PENDING - will process without notification")
            }

            // If content is non-empty but status is still PENDING, streaming may have been interrupted
            // We should check if streaming is still in progress or if we need to retry from scratch
            // The presence of partial content means streaming started but didn't complete
            // We'll make a fresh API call to get the full response
            if (pendingMessage.content.isNotBlank()) {
                debugLog.log(TAG, "Message has partial content (${pendingMessage.content.length} chars) but status is PENDING - streaming was interrupted, will retry with fresh API call")
                // Clear the partial content and proceed with API call
                messageDao.updateMessageContent(
                    id = messageId,
                    content = "",
                    status = MessageStatus.PENDING.toApiString(),
                    updatedAt = Instant.now().toEpochMilli()
                )
            }

            // Build conversation history for Claude API
            val existingMessages = messageDao.getMessagesForConversationSync(conversationId)
                .filter { it.id != messageId && it.content.isNotBlank() } // Exclude pending message

            val claudeMessages = existingMessages.map { msg ->
                ClaudeMessage(
                    role = msg.role.lowercase(),
                    content = msg.content
                )
            }

            // Get recent journal entries for context
            val recentEntries = journalEntryDao.getRecentEntriesSync(userId, 5)

            // Get upcoming agenda items for context
            val now = java.time.Instant.now()
            val upcomingAgendaItems = agendaItemDao.getUpcomingItemsSync(userId, now.toEpochMilli(), 10)

            val systemPrompt = CoachPrompts.buildCoachContext(recentEntries, upcomingAgendaItems)

            debugLog.log(TAG, "Calling Claude API with ${claudeMessages.size} messages, ${upcomingAgendaItems.size} agenda items")

            // Call Claude API
            val response = claudeApi.sendMessage(
                apiKey = apiKey,
                request = ClaudeMessageRequest(
                    system = systemPrompt,
                    messages = claudeMessages
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val assistantContent = result.content.firstOrNull()?.text
                    ?: "I'm sorry, I wasn't able to generate a response. Please try again."

                debugLog.log(TAG, "Claude API response received, content length: ${assistantContent.length}")

                // Update the message with the AI response
                // notificationSent stays false - will be updated after notification is sent
                messageDao.updateMessageContent(
                    id = messageId,
                    content = assistantContent,
                    status = MessageStatus.COMPLETED.toApiString(),
                    updatedAt = Instant.now().toEpochMilli()
                )

                // Update conversation timestamp
                conversationDao.updateTimestamp(conversationId, Instant.now().toEpochMilli())

                // Since we're in the BackgroundChatWorker, the streaming was interrupted
                // (user left the app), so we should send the notification immediately.
                // No delay needed - the user has already left the app.

                // Send notification FIRST, then mark as sent only if successful
                // This ensures retry if notification fails to show
                debugLog.log(TAG, "Preparing to send notification...")
                val notificationTitle = "Coach replied"
                val notificationBody = if (assistantContent.length > 100) {
                    assistantContent.take(97) + "..."
                } else {
                    assistantContent
                }

                debugLog.log(TAG, "Calling showChatResponseNotification: title='$notificationTitle', bodyLen=${notificationBody.length}")
                val notifResult = notificationHelper.showChatResponseNotification(
                    title = notificationTitle,
                    body = notificationBody,
                    conversationId = conversationId
                )
                debugLog.log(TAG, "Notification result: $notifResult")

                // Only mark notification as sent if it was actually shown successfully
                // This allows the UI to potentially retry showing the notification
                if (notifResult.startsWith("SUCCESS")) {
                    messageDao.updateNotificationSent(messageId, true)
                    debugLog.log(TAG, "Marked notificationSent = true")
                } else {
                    debugLog.log(TAG, "Notification failed to show, keeping notificationSent = false for potential retry")
                }

                debugLog.log(TAG, "doWork() returning Result.success()")
                Result.success()
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = if (errorBody?.contains("invalid_api_key") == true) {
                    "Invalid API key. Please check your Claude API key in Settings."
                } else {
                    "Failed to get AI response. Please try again."
                }

                debugLog.log(TAG, "Claude API error: $errorMessage")

                messageDao.updateMessageContent(
                    id = messageId,
                    content = errorMessage,
                    status = MessageStatus.FAILED.toApiString(),
                    updatedAt = Instant.now().toEpochMilli()
                )

                Result.failure()
            }
        } catch (e: Exception) {
            debugLog.log(TAG, "doWork() EXCEPTION: ${e.message}")

            // Check if this is a network error that should be retried
            // This includes Android 15+ background network restriction errors (SocketException)
            val isNetworkError = e.message?.contains("UnknownHostException", ignoreCase = true) == true ||
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    e.message?.contains("No address associated", ignoreCase = true) == true ||
                    e.message?.contains("SocketTimeoutException", ignoreCase = true) == true ||
                    e.message?.contains("ConnectException", ignoreCase = true) == true ||
                    e.message?.contains("Network", ignoreCase = true) == true ||
                    // Android 15+ background network restriction errors:
                    e.message?.contains("SocketException", ignoreCase = true) == true ||
                    e.message?.contains("connection abort", ignoreCase = true) == true ||
                    e.message?.contains("Software caused", ignoreCase = true) == true ||
                    e.message?.contains("Socket closed", ignoreCase = true) == true ||
                    e is java.net.SocketException

            if (isNetworkError) {
                // Don't mark message as failed for network errors - WorkManager will retry
                debugLog.log(TAG, "Network error detected, returning Result.retry() for WorkManager to retry")
                return Result.retry()
            }

            messageDao.updateMessageContent(
                id = messageId,
                content = "An error occurred: ${e.message ?: "Unknown error"}",
                status = MessageStatus.FAILED.toApiString(),
                updatedAt = Instant.now().toEpochMilli()
            )

            Result.failure()
        }
    }
}
