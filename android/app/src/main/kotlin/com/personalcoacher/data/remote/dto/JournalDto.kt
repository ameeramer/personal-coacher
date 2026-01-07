package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

data class JournalEntryDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("content") val content: String,
    @SerializedName("mood") val mood: String?,
    @SerializedName("tags") val tags: List<String>,
    @SerializedName("date") val date: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    fun toDomainModel(): JournalEntry {
        return JournalEntry(
            id = id,
            userId = userId,
            content = content,
            mood = Mood.fromString(mood),
            tags = tags,
            date = Instant.parse(date),
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class CreateJournalEntryRequest(
    @SerializedName("content") val content: String,
    @SerializedName("mood") val mood: String?,
    @SerializedName("tags") val tags: List<String>,
    @SerializedName("date") val date: String?
)

data class UpdateJournalEntryRequest(
    @SerializedName("content") val content: String,
    @SerializedName("mood") val mood: String?,
    @SerializedName("tags") val tags: List<String>
)
