package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

/**
 * DTO for Daily Tool (AI-generated web app) API responses.
 */
data class DailyToolDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("date") val date: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("htmlCode") val htmlCode: String,
    @SerializedName("journalContext") val journalContext: String?,
    @SerializedName("status") val status: String,
    @SerializedName("usedAt") val usedAt: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    fun toDomainModel(): DailyApp {
        return DailyApp(
            id = id,
            userId = userId,
            date = Instant.parse(date),
            title = title,
            description = description,
            htmlCode = htmlCode,
            journalContext = journalContext,
            status = DailyAppStatus.valueOf(status.uppercase()),
            usedAt = usedAt?.let { Instant.parse(it) },
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

/**
 * Request body for creating/uploading a daily tool.
 */
data class CreateDailyToolRequest(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("htmlCode") val htmlCode: String,
    @SerializedName("journalContext") val journalContext: String?,
    @SerializedName("status") val status: String,
    @SerializedName("usedAt") val usedAt: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

/**
 * Request body for updating a daily tool status.
 */
data class UpdateDailyToolRequest(
    @SerializedName("status") val status: String,
    @SerializedName("usedAt") val usedAt: String?
)
