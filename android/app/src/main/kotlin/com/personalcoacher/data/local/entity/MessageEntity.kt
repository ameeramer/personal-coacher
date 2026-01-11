package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageRole
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["status"]),
        Index(value = ["syncStatus"]),
        Index(value = ["notificationSent"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String,
    val notificationSent: Boolean = true // User messages and seen messages don't need notification
) {
    fun toDomainModel(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            role = MessageRole.fromString(role),
            content = content,
            status = MessageStatus.fromString(status),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(message: Message, notificationSent: Boolean = true): MessageEntity {
            return MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                role = message.role.toApiString(),
                content = message.content,
                status = message.status.toApiString(),
                createdAt = message.createdAt.toEpochMilli(),
                updatedAt = message.updatedAt.toEpochMilli(),
                syncStatus = message.syncStatus.name,
                notificationSent = notificationSent
            )
        }
    }
}
