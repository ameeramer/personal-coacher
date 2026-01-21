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
     * Sends a message with server-side buffered streaming.
     * The server holds the Claude connection and buffers chunks in the database.
     * If the client disconnects, streaming continues on the server and a push notification is sent when complete.
     *
     * This is the recommended approach for chat as it:
     * - Supports seamless background continuation
     * - Provides real-time streaming when the app is in foreground
     * - Uses a single API call (no duplicate requests)
     *
     * @param debugMode If true, logs all events for debugging
     * @param debugCallback Optional callback to receive debug log entries
     * @return Flow emitting streaming events (text deltas, completion, errors)
     */
    fun sendMessageCloudStreaming(
        conversationId: String?,
        userId: String,
        message: String,
        fcmToken: String? = null,
        debugMode: Boolean = false,
        debugCallback: ((String) -> Unit)? = null
    ): Flow<StreamingChatEvent>

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
