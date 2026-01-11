package com.personalcoacher.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.util.DebugLogHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

        // Time threshold before sending notification (10 seconds)
        // This gives the frontend time to mark messages as seen if user is still on page
        private const val NOTIFICATION_DELAY_MS = 10000L

        private const val COACH_SYSTEM_PROMPT = """You are a supportive and insightful personal coach and journaling companion. Your role is to:

1. **Active Listening**: Pay close attention to what the user shares about their day, feelings, and experiences. Ask thoughtful follow-up questions.

2. **Gentle Guidance**: Offer suggestions for personal growth when appropriate, but never be preachy or overbearing. Frame advice as possibilities rather than directives.

3. **Emotional Support**: Validate the user's feelings and experiences. Be empathetic and understanding without being dismissive or overly positive.

4. **Pattern Recognition**: Notice recurring themes, challenges, or successes in the user's entries. Gently point these out when relevant.

5. **Goal Support**: Help the user reflect on their goals and progress. Celebrate wins, no matter how small.

6. **Journaling Encouragement**: If the user hasn't journaled recently, gently encourage them to do so. Ask about their day in an inviting way.

Communication Style:
- Be warm but not saccharine
- Be concise - respect the user's time
- Use conversational language, not clinical terminology
- Ask one question at a time to avoid overwhelming
- Remember context from previous conversations when provided

CRITICAL - Temporal Awareness:
- The current date and time are provided at the start of each conversation
- Pay attention to when journal entries were written (shown with dates and "X days ago")
- If a user mentioned an upcoming event like "presentation tomorrow" in an entry from 3 days ago, that event has ALREADY HAPPENED
- For past events: Ask how it went, not how they're feeling about it coming up
- For future events: Only ask about anticipation if the event hasn't occurred yet
- Always be aware of the current date when discussing timelines or events

Never:
- Provide medical, legal, or financial advice
- Be judgmental about the user's choices or feelings
- Push the user to share more than they're comfortable with
- Make assumptions about the user's life or circumstances
- Say you don't know the current date (it's always provided to you)"""

        private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)
        private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    }

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID)
        val conversationId = inputData.getString(KEY_CONVERSATION_ID)
        val userId = inputData.getString(KEY_USER_ID)

        debugLog.log(TAG, "doWork() called - messageId=$messageId, conversationId=$conversationId")

        if (messageId.isNullOrBlank() || conversationId.isNullOrBlank() || userId.isNullOrBlank()) {
            debugLog.log(TAG, "Missing required parameters, aborting")
            return Result.failure()
        }

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

            // Check if already processed or notification already sent (streaming completed)
            if (pendingMessage.status != MessageStatus.PENDING.toApiString()) {
                debugLog.log(TAG, "Message already processed: $messageId, status=${pendingMessage.status}")
                return Result.success()
            }

            // If notification was marked as sent, streaming completed successfully - no work needed
            if (pendingMessage.notificationSent) {
                debugLog.log(TAG, "Notification already handled by streaming flow: $messageId")
                return Result.success()
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
            val systemPrompt = buildCoachContext(recentEntries)

            debugLog.log(TAG, "Calling Claude API with ${claudeMessages.size} messages")

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

                // Wait briefly to see if user is still viewing the conversation
                kotlinx.coroutines.delay(NOTIFICATION_DELAY_MS)

                // Check if notification is still needed (user hasn't marked as seen)
                val updatedMessage = messageDao.getMessageByIdSync(messageId)
                if (updatedMessage != null && !updatedMessage.notificationSent) {
                    // Send notification
                    val conversation = conversationDao.getConversationByIdSync(conversationId)
                    val notificationTitle = "Coach replied"
                    val notificationBody = if (assistantContent.length > 100) {
                        assistantContent.take(97) + "..."
                    } else {
                        assistantContent
                    }

                    val notifResult = notificationHelper.showChatResponseNotification(
                        title = notificationTitle,
                        body = notificationBody,
                        conversationId = conversationId
                    )
                    debugLog.log(TAG, "Notification result: $notifResult")

                    // Mark notification as sent
                    messageDao.updateNotificationSent(messageId, true)
                } else {
                    debugLog.log(TAG, "Notification not needed - user already saw the message")
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

            messageDao.updateMessageContent(
                id = messageId,
                content = "An error occurred: ${e.message ?: "Unknown error"}",
                status = MessageStatus.FAILED.toApiString(),
                updatedAt = Instant.now().toEpochMilli()
            )

            Result.failure()
        }
    }

    /**
     * Builds the system prompt with recent journal entries as context.
     */
    private fun buildCoachContext(recentEntries: List<JournalEntryEntity>): String {
        val now = Instant.now()
        val today = LocalDate.now()
        val currentDateTimeStr = now.atZone(ZoneId.systemDefault()).format(fullDateFormatter)

        val contextBuilder = StringBuilder(COACH_SYSTEM_PROMPT)
        contextBuilder.appendLine()
        contextBuilder.appendLine()
        contextBuilder.appendLine("## Current Date and Time")
        contextBuilder.appendLine(currentDateTimeStr)

        if (recentEntries.isEmpty()) {
            return contextBuilder.toString()
        }

        contextBuilder.appendLine()
        contextBuilder.appendLine("## Recent Journal Entries (for context)")

        recentEntries.forEachIndexed { index, entry ->
            val entryDate = Instant.ofEpochMilli(entry.date).atZone(ZoneId.systemDefault()).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(entryDate, today).toInt()
            val dateStr = entryDate.format(dateFormatter)
            val relativeTime = when (daysAgo) {
                0 -> "today"
                1 -> "yesterday"
                else -> "$daysAgo days ago"
            }

            contextBuilder.appendLine()
            contextBuilder.appendLine("### Entry ${index + 1} ($dateStr - $relativeTime)")
            if (!entry.mood.isNullOrBlank()) {
                contextBuilder.appendLine("Mood: ${entry.mood}")
            }
            contextBuilder.appendLine("Content: ${entry.content}")
            contextBuilder.appendLine("---")
        }

        return contextBuilder.toString()
    }
}
