package com.personalcoacher.domain.model

import java.time.Instant
import java.time.LocalDate

data class Goal(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val targetDate: LocalDate?,
    val status: GoalStatus,
    val priority: Priority,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

enum class GoalStatus(val displayName: String) {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(value: String?): GoalStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
        }
    }
}

enum class Priority(val displayName: String, val sortOrder: Int) {
    HIGH("High", 1),
    MEDIUM("Medium", 2),
    LOW("Low", 3);

    companion object {
        fun fromString(value: String?): Priority {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}
