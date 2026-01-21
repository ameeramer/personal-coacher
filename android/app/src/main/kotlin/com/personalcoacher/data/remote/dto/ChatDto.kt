package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageRole
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

data class ConversationDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("title") val title: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("messages") val messages: List<MessageDto>?
) {
    fun toDomainModel(): Conversation {
        return Conversation(
            id = id,
            userId = userId,
            title = title,
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            messages = messages?.map { it.toDomainModel() } ?: emptyList(),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class MessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("conversationId") val conversationId: String?,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("status") val status: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String?
) {
    fun toDomainModel(convId: String? = null): Message {
        return Message(
            id = id,
            conversationId = conversationId ?: convId ?: "",
            role = MessageRole.fromString(role),
            content = content,
            status = MessageStatus.fromString(status ?: "completed"),
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt ?: createdAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class SendMessageRequest(
    @SerializedName("message") val message: String,
    @SerializedName("conversationId") val conversationId: String?,
    @SerializedName("initialAssistantMessage") val initialAssistantMessage: String?
)

data class SendMessageResponse(
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("userMessage") val userMessage: MessageDto,
    @SerializedName("pendingMessage") val pendingMessage: PendingMessageDto,
    @SerializedName("processing") val processing: Boolean
)

data class PendingMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: String,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: String
)

data class MessageStatusResponse(
    @SerializedName("message") val message: MessageDto?
)

data class CreateConversationRequest(
    @SerializedName("title") val title: String?
)

// Local-only chat DTOs (no DB persistence on server)
data class LocalChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("conversationHistory") val conversationHistory: List<LocalMessageDto>?
)

data class LocalMessageDto(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class LocalChatResponse(
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String
)

// Cloud Chat Job DTOs - Server-side buffered streaming
data class CloudChatRequest(
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("messageId") val messageId: String,
    @SerializedName("messages") val messages: List<CloudChatMessage>,
    @SerializedName("fcmToken") val fcmToken: String?,
    @SerializedName("systemContext") val systemContext: String?
)

data class CloudChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ChatJobResponse(
    @SerializedName("jobId") val jobId: String,
    @SerializedName("statusUrl") val statusUrl: String?,
    @SerializedName("existing") val existing: Boolean?,
    @SerializedName("buffer") val buffer: String?
)

data class ChatJobStatusResponse(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,  // 'PENDING', 'STREAMING', 'COMPLETED', 'FAILED'
    @SerializedName("buffer") val buffer: String,
    @SerializedName("error") val error: String?,
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("messageId") val messageId: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class ChatJobUpdateRequest(
    @SerializedName("clientConnected") val clientConnected: Boolean?,
    @SerializedName("fcmToken") val fcmToken: String?
)
