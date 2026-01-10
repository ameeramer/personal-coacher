package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["userId", "date"]),
        Index(value = ["syncStatus"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val content: String,
    val mood: String?,
    val tags: String, // Stored as comma-separated string
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
) {
    fun toDomainModel(): JournalEntry {
        return JournalEntry(
            id = id,
            userId = userId,
            content = content,
            mood = Mood.fromString(mood),
            tags = if (tags.isBlank()) emptyList() else tags.split(",").filter { it.isNotBlank() },
            date = Instant.ofEpochMilli(date),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(entry: JournalEntry): JournalEntryEntity {
            return JournalEntryEntity(
                id = entry.id,
                userId = entry.userId,
                content = entry.content,
                mood = entry.mood?.serverValue,
                tags = entry.tags.joinToString(","),
                date = entry.date.toEpochMilli(),
                createdAt = entry.createdAt.toEpochMilli(),
                updatedAt = entry.updatedAt.toEpochMilli(),
                syncStatus = entry.syncStatus.name
            )
        }
    }
}
