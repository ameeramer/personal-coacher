package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant
import java.time.LocalDate

data class GoalDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("targetDate") val targetDate: String?,
    @SerializedName("status") val status: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    fun toDomainModel(): Goal {
        return Goal(
            id = id,
            userId = userId,
            title = title,
            description = description,
            targetDate = targetDate?.let { LocalDate.parse(it) },
            status = GoalStatus.fromString(status),
            priority = Priority.fromString(priority),
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }
}

data class CreateGoalRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("targetDate") val targetDate: String?,
    @SerializedName("priority") val priority: String
)

data class UpdateGoalRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("targetDate") val targetDate: String?,
    @SerializedName("status") val status: String,
    @SerializedName("priority") val priority: String
)
