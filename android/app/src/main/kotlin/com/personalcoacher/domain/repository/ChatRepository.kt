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

    suspend fun deleteConversation(id: String): Resource<Unit>

    suspend fun uploadConversations(userId: String): Resource<Unit>

    suspend fun syncConversations(userId: String): Resource<Unit>
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
}
