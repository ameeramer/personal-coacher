package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.domain.model.EventSuggestionStatus
import java.time.Instant

@Entity(
    tableName = "event_suggestions",
    indices = [
        Index(value = ["userId", "status"]),
        Index(value = ["journalEntryId"]),
        Index(value = ["createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EventSuggestionEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val journalEntryId: String,
    val title: String,
    val description: String?,
    val suggestedStartTime: Long,
    val suggestedEndTime: Long?,
    val isAllDay: Boolean,
    val location: String?,
    val status: String,
    val createdAt: Long,
    val processedAt: Long?
) {
    fun toDomainModel(): EventSuggestion {
        return EventSuggestion(
            id = id,
            userId = userId,
            journalEntryId = journalEntryId,
            title = title,
            description = description,
            suggestedStartTime = Instant.ofEpochMilli(suggestedStartTime),
            suggestedEndTime = suggestedEndTime?.let { Instant.ofEpochMilli(it) },
            isAllDay = isAllDay,
            location = location,
            status = EventSuggestionStatus.valueOf(status),
            createdAt = Instant.ofEpochMilli(createdAt),
            processedAt = processedAt?.let { Instant.ofEpochMilli(it) }
        )
    }

    companion object {
        fun fromDomainModel(suggestion: EventSuggestion): EventSuggestionEntity {
            return EventSuggestionEntity(
                id = suggestion.id,
                userId = suggestion.userId,
                journalEntryId = suggestion.journalEntryId,
                title = suggestion.title,
                description = suggestion.description,
                suggestedStartTime = suggestion.suggestedStartTime.toEpochMilli(),
                suggestedEndTime = suggestion.suggestedEndTime?.toEpochMilli(),
                isAllDay = suggestion.isAllDay,
                location = suggestion.location,
                status = suggestion.status.name,
                createdAt = suggestion.createdAt.toEpochMilli(),
                processedAt = suggestion.processedAt?.toEpochMilli()
            )
        }
    }
}
