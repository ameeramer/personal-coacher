package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getConversations(userId: String): Flow<List<ConversationWithLastMessage>>

    fun getConversation(id: String): Flow<Conversation?>

    fun getMessages(conversationId: String): Flow<List<Message>>

    suspend fun createConversation(userId: String, title: String?): Resource<Conversation>

    /**
     * Creates a new conversation with an initial message from the coach.
     * This is used when opening the app from a notification to display
     * the notification content as the first message in the conversation.
     * @return The conversation ID if successful
     */
    suspend fun createConversationWithCoachMessage(userId: String, coachMessage: String): Resource<String>

    suspend fun sendMessage(
        conversationId: String?,
        userId: String,
        message: String,
        initialAssistantMessage: String? = null
    ): Resource<SendMessageResult>

    /**
     * Sends a message and streams the AI response in real-time.
     * @param debugMode If true, logs all SSE events for debugging
     * @param debugCallback Optional callback to receive debug log entries
     * @return Flow emitting streaming events (text deltas, completion, errors)
     */
    fun sendMessageStreaming(
        conversationId: String?,
        userId: String,
        message: String,
        debugMode: Boolean = false,
        debugCallback: ((String) -> Unit)? = null
    ): Flow<StreamingChatEvent>

    suspend fun checkMessageStatus(messageId: String): Resource<Message?>

    /**
     * Marks a message as "seen" by the user. This prevents sending a notification
     * for this message if the user is actively viewing the conversation.
     */
    suspend fun markMessageAsSeen(messageId: String)

    /**
     * Sends a message with background processing.
     * Creates a pending message and enqueues a background worker to process it.
     * The user can leave the app and will receive a notification when the response is ready.
     * @return SendMessageResult containing the conversation ID and pending message ID
     */
    suspend fun sendMessageBackground(
        conversationId: String?,
        userId: String,
        message: String
    ): Resource<SendMessageResult>

    suspend fun deleteConversation(id: String): Resource<Unit>

    suspend fun uploadConversations(userId: String): Resource<Unit>

    suspend fun syncConversations(userId: String): Resource<Unit>

    /**
     * Sends a message with server-side non-streaming processing (WhatsApp-style).
     * The server processes the Claude request in the background while the client polls for status.
     *
     * This is the recommended approach for chat as it:
     * - Works reliably with Android 15+ network restrictions
     * - Doesn't require maintaining an SSE connection
     * - Server continues processing if app goes to background
     * - Single API call (no duplicate requests)
     *
     * @param conversationId The conversation ID (null to create new)
     * @param userId The user ID
     * @param message The user's message
     * @param fcmToken Optional FCM token for push notification
     * @param debugCallback Optional callback to receive debug log entries
     * @return StartChatJobResult with job ID and message IDs
     */
    suspend fun startCloudChatJob(
        conversationId: String?,
        userId: String,
        message: String,
        fcmToken: String? = null,
        debugCallback: ((String) -> Unit)? = null
    ): Resource<StartChatJobResult>

    /**
     * Gets the status of a cloud chat job.
     * Used when reconnecting after the app was backgrounded.
     */
    suspend fun getCloudChatJobStatus(jobId: String): Resource<CloudChatJobStatus>

    /**
     * Marks a cloud chat job as disconnected so the server can send a push notification.
     * Call this when the app goes to background during streaming.
     */
    suspend fun markCloudChatDisconnected(jobId: String, fcmToken: String? = null): Resource<Unit>
}

data class SendMessageResult(
    val conversationId: String,
    val userMessage: Message,
    val pendingMessageId: String
)

/**
 * Result from starting a cloud chat job (non-streaming).
 */
data class StartChatJobResult(
    val conversationId: String,
    val userMessage: Message,
    val assistantMessageId: String,
    val jobId: String
)

/**
 * Events emitted during streaming chat responses.
 */
sealed class StreamingChatEvent {
    /** Initial setup complete - contains conversation and message IDs */
    data class Started(
        val conversationId: String,
        val userMessage: Message,
        val assistantMessageId: String
    ) : StreamingChatEvent()

    /** A chunk of text from the AI response */
    data class TextDelta(val text: String) : StreamingChatEvent()

    /** Streaming completed successfully with final content */
    data class Complete(val fullContent: String) : StreamingChatEvent()

    /** An error occurred */
    data class Error(val message: String) : StreamingChatEvent()

    /** Debug log entry (only emitted when debugMode is true) */
    data class DebugLog(val message: String) : StreamingChatEvent()

    /** Cloud job started - contains job ID for reconnection */
    data class CloudJobStarted(
        val conversationId: String,
        val userMessage: Message,
        val assistantMessageId: String,
        val jobId: String
    ) : StreamingChatEvent()
}

/**
 * Status of a cloud chat job.
 */
data class CloudChatJobStatus(
    val id: String,
    val status: String,  // 'PENDING', 'STREAMING', 'COMPLETED', 'FAILED'
    val buffer: String,
    val error: String?,
    val conversationId: String,
    val messageId: String
)
