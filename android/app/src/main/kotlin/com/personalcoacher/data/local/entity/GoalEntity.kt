package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["userId", "status"]),
        Index(value = ["userId", "priority"]),
        Index(value = ["syncStatus"])
    ]
)
data class GoalEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val targetDate: String?, // Stored as ISO-8601 string (YYYY-MM-DD)
    val status: String,
    val priority: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
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
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(goal: Goal): GoalEntity {
            return GoalEntity(
                id = goal.id,
                userId = goal.userId,
                title = goal.title,
                description = goal.description,
                targetDate = goal.targetDate?.toString(),
                status = goal.status.name,
                priority = goal.priority.name,
                createdAt = goal.createdAt.toEpochMilli(),
                updatedAt = goal.updatedAt.toEpochMilli(),
                syncStatus = goal.syncStatus.name
            )
        }
    }
}
