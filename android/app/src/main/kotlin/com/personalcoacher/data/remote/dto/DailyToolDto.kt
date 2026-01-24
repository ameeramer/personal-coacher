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

// ==================== QStash Generation DTOs ====================

/**
 * Request body for initiating daily tool generation via QStash.
 * All fields are optional - if not provided, server will fetch from database.
 */
data class DailyToolGenerationRequest(
    @SerializedName("recentEntries") val recentEntries: List<JournalEntryForGeneration>? = null,
    @SerializedName("previousToolIds") val previousToolIds: List<String>? = null
)

/**
 * Simplified journal entry for generation request.
 */
data class JournalEntryForGeneration(
    @SerializedName("content") val content: String,
    @SerializedName("mood") val mood: String?,
    @SerializedName("tags") val tags: List<String>,
    @SerializedName("date") val date: String
)

/**
 * Response from POST /api/daily-tools/request
 */
data class DailyToolJobResponse(
    @SerializedName("jobId") val jobId: String,
    @SerializedName("statusUrl") val statusUrl: String,
    @SerializedName("qstashMessageId") val qstashMessageId: String? = null,
    @SerializedName("existing") val existing: Boolean = false
)

/**
 * Response from GET /api/daily-tools/status/{id}
 */
data class DailyToolJobStatusResponse(
    @SerializedName("status") val status: String, // PENDING, PROCESSING, COMPLETED, FAILED
    @SerializedName("error") val error: String? = null,
    @SerializedName("dailyTool") val dailyTool: DailyToolDto? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

// ==================== QStash Refinement DTOs ====================

/**
 * Request body for initiating daily tool refinement via QStash.
 */
data class DailyToolRefinementRequest(
    @SerializedName("appId") val appId: String,
    @SerializedName("feedback") val feedback: String,
    @SerializedName("currentTitle") val currentTitle: String,
    @SerializedName("currentDescription") val currentDescription: String,
    @SerializedName("currentHtmlCode") val currentHtmlCode: String,
    @SerializedName("currentJournalContext") val currentJournalContext: String?
)

/**
 * Response from POST /api/daily-tools/refine
 */
data class DailyToolRefineJobResponse(
    @SerializedName("jobId") val jobId: String,
    @SerializedName("statusUrl") val statusUrl: String,
    @SerializedName("qstashMessageId") val qstashMessageId: String? = null
)
