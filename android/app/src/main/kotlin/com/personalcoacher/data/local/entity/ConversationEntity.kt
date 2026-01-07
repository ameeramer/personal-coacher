package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["syncStatus"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
) {
    fun toDomainModel(messages: List<MessageEntity> = emptyList()): Conversation {
        return Conversation(
            id = id,
            userId = userId,
            title = title,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            messages = messages.map { it.toDomainModel() },
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(conversation: Conversation): ConversationEntity {
            return ConversationEntity(
                id = conversation.id,
                userId = conversation.userId,
                title = conversation.title,
                createdAt = conversation.createdAt.toEpochMilli(),
                updatedAt = conversation.updatedAt.toEpochMilli(),
                syncStatus = conversation.syncStatus.name
            )
        }
    }
}
