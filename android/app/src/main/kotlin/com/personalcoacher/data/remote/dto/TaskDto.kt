package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.model.Task
import java.time.Instant
import java.time.LocalDate

data class TaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("dueDate") val dueDate: String?,
    @SerializedName("isCompleted") val isCompleted: Boolean,
    @SerializedName("priority") val priority: String,
    @SerializedName("linkedGoalId") val linkedGoalId: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    fun toDomainModel(): Task {
        return Task(
            id = id,
            userId = userId,
            title = title,
            description = description,
            dueDate = dueDate?.let { parseDate(it) },
            isCompleted = isCompleted,
            priority = Priority.fromString(priority),
            linkedGoalId = linkedGoalId,
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun parseDate(dateStr: String): LocalDate {
        // Handle both date-only (YYYY-MM-DD) and datetime (ISO-8601 timestamp) formats
        return try {
            LocalDate.parse(dateStr.take(10)) // Take just the date portion
        } catch (e: Exception) {
            LocalDate.parse(dateStr) // Fallback to full parse
        }
    }
}

data class CreateTaskRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("dueDate") val dueDate: String?,
    @SerializedName("priority") val priority: String,
    @SerializedName("linkedGoalId") val linkedGoalId: String?
)

data class UpdateTaskRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("dueDate") val dueDate: String?,
    @SerializedName("isCompleted") val isCompleted: Boolean,
    @SerializedName("priority") val priority: String,
    @SerializedName("linkedGoalId") val linkedGoalId: String?
)
