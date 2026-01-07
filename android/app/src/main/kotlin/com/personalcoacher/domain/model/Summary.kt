package com.personalcoacher.domain.model

import java.time.Instant

data class Summary(
    val id: String,
    val userId: String,
    val type: SummaryType,
    val content: String,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

enum class SummaryType {
    DAILY,
    WEEKLY,
    MONTHLY;

    companion object {
        fun fromString(value: String): SummaryType {
            return when (value.lowercase()) {
                "daily" -> DAILY
                "weekly" -> WEEKLY
                "monthly" -> MONTHLY
                else -> DAILY
            }
        }
    }

    fun toApiString(): String {
        return name.lowercase()
    }

    fun displayName(): String {
        return when (this) {
            DAILY -> "Daily"
            WEEKLY -> "Weekly"
            MONTHLY -> "Monthly"
        }
    }
}
