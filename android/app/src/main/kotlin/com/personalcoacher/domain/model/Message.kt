package com.personalcoacher.domain.model

import java.time.Instant

data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

enum class MessageRole {
    USER,
    ASSISTANT;

    companion object {
        fun fromString(value: String): MessageRole {
            return when (value.lowercase()) {
                "user" -> USER
                "assistant" -> ASSISTANT
                else -> USER
            }
        }
    }

    fun toApiString(): String {
        return name.lowercase()
    }
}

enum class MessageStatus {
    PENDING,     // Message created, waiting for AI response
    PROCESSING,  // AI is processing
    COMPLETED,   // Response received
    FAILED;      // Error during processing

    companion object {
        fun fromString(value: String): MessageStatus {
            return when (value.lowercase()) {
                "pending" -> PENDING
                "processing" -> PROCESSING
                "completed" -> COMPLETED
                "failed" -> FAILED
                else -> COMPLETED
            }
        }
    }

    fun toApiString(): String {
        return name.lowercase()
    }
}
