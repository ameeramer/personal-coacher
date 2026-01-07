package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

data class SummaryDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("createdAt") val createdAt: String
) {
    fun toDomainModel(): Summary {
        return Summary(
            id = id,
            userId = userId,
            type = SummaryType.fromString(type),
            content = content,
            startDate = Instant.parse(startDate),
            endDate = Instant.parse(endDate),
            createdAt = Instant.parse(createdAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class CreateSummaryRequest(
    @SerializedName("type") val type: String
)
