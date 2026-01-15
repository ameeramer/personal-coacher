package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Transcription
import com.personalcoacher.domain.model.TranscriptionStatus
import java.time.Instant

@Entity(
    tableName = "transcriptions",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId", "chunkIndex"]),
        Index(value = ["status"])
    ]
)
data class TranscriptionEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val chunkIndex: Int,
    val content: String, // The transcribed text
    val startTime: Long, // When this chunk started recording
    val endTime: Long, // When this chunk ended recording
    val duration: Int, // Duration in seconds
    val status: String, // 'pending', 'processing', 'completed', 'failed'
    val errorMessage: String?,
    val audioFilePath: String?, // Path to audio file for retry (deleted after successful transcription)
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): Transcription {
        return Transcription(
            id = id,
            sessionId = sessionId,
            chunkIndex = chunkIndex,
            content = content,
            startTime = Instant.ofEpochMilli(startTime),
            endTime = Instant.ofEpochMilli(endTime),
            duration = duration,
            status = TranscriptionStatus.valueOf(status.uppercase()),
            errorMessage = errorMessage,
            audioFilePath = audioFilePath,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    companion object {
        fun fromDomainModel(transcription: Transcription): TranscriptionEntity {
            return TranscriptionEntity(
                id = transcription.id,
                sessionId = transcription.sessionId,
                chunkIndex = transcription.chunkIndex,
                content = transcription.content,
                startTime = transcription.startTime.toEpochMilli(),
                endTime = transcription.endTime.toEpochMilli(),
                duration = transcription.duration,
                status = transcription.status.name.lowercase(),
                errorMessage = transcription.errorMessage,
                audioFilePath = transcription.audioFilePath,
                createdAt = transcription.createdAt.toEpochMilli(),
                updatedAt = transcription.updatedAt.toEpochMilli()
            )
        }
    }
}
