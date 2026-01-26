package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.Note
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

data class NoteDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    fun toDomainModel(): Note {
        return Note(
            id = id,
            userId = userId,
            title = title,
            content = content,
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class CreateNoteRequest(
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String
)

data class UpdateNoteRequest(
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String
)
