package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.RecordingSession
import com.personalcoacher.domain.model.RecordingSessionStatus
import java.time.Instant

@Entity(
    tableName = "recording_sessions",
    indices = [
        Index(value = ["userId", "status"]),
        Index(value = ["userId", "createdAt"])
    ]
)
data class RecordingSessionEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String?,
    val chunkDuration: Int, // Duration in seconds (e.g., 1800 = 30 min)
    val status: String, // 'recording', 'paused', 'completed', 'failed'
    val startedAt: Long,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): RecordingSession {
        return RecordingSession(
            id = id,
            userId = userId,
            title = title,
            chunkDuration = chunkDuration,
            status = RecordingSessionStatus.valueOf(status.uppercase()),
            startedAt = Instant.ofEpochMilli(startedAt),
            endedAt = endedAt?.let { Instant.ofEpochMilli(it) },
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    companion object {
        fun fromDomainModel(session: RecordingSession): RecordingSessionEntity {
            return RecordingSessionEntity(
                id = session.id,
                userId = session.userId,
                title = session.title,
                chunkDuration = session.chunkDuration,
                status = session.status.name.lowercase(),
                startedAt = session.startedAt.toEpochMilli(),
                endedAt = session.endedAt?.toEpochMilli(),
                createdAt = session.createdAt.toEpochMilli(),
                updatedAt = session.updatedAt.toEpochMilli()
            )
        }
    }
}
