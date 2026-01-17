package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

data class AgendaItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("location") val location: String?,
    @SerializedName("sourceJournalEntryId") val sourceJournalEntryId: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    fun toDomainModel(): AgendaItem {
        return AgendaItem(
            id = id,
            userId = userId,
            title = title,
            description = description,
            startTime = Instant.parse(startTime),
            endTime = endTime?.let { Instant.parse(it) },
            isAllDay = isAllDay,
            location = location,
            sourceJournalEntryId = sourceJournalEntryId,
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class CreateAgendaItemRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("location") val location: String?,
    @SerializedName("sourceJournalEntryId") val sourceJournalEntryId: String?
)

data class UpdateAgendaItemRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("location") val location: String?
)

data class AnalyzeJournalRequest(
    @SerializedName("journalEntryId") val journalEntryId: String,
    @SerializedName("content") val content: String
)

data class AnalyzeJournalResponse(
    @SerializedName("suggestions") val suggestions: List<EventSuggestionDto>
)

data class EventSuggestionDto(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("location") val location: String?
)
