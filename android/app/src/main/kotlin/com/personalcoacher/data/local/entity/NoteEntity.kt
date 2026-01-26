package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Note
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["userId", "createdAt"]),
        Index(value = ["syncStatus"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
) {
    fun toDomainModel(): Note {
        return Note(
            id = id,
            userId = userId,
            title = title,
            content = content,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(note: Note): NoteEntity {
            return NoteEntity(
                id = note.id,
                userId = note.userId,
                title = note.title,
                content = note.content,
                createdAt = note.createdAt.toEpochMilli(),
                updatedAt = note.updatedAt.toEpochMilli(),
                syncStatus = note.syncStatus.name
            )
        }
    }
}
