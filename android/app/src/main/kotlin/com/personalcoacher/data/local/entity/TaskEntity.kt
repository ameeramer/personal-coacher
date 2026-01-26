package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.model.Task
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["userId", "isCompleted"]),
        Index(value = ["userId", "dueDate"]),
        Index(value = ["userId", "priority"]),
        Index(value = ["linkedGoalId"]),
        Index(value = ["syncStatus"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedGoalId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val dueDate: String?, // Stored as ISO-8601 string (YYYY-MM-DD)
    val isCompleted: Boolean,
    val priority: String,
    val linkedGoalId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
) {
    fun toDomainModel(): Task {
        return Task(
            id = id,
            userId = userId,
            title = title,
            description = description,
            dueDate = dueDate?.let { LocalDate.parse(it) },
            isCompleted = isCompleted,
            priority = Priority.fromString(priority),
            linkedGoalId = linkedGoalId,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(task: Task): TaskEntity {
            return TaskEntity(
                id = task.id,
                userId = task.userId,
                title = task.title,
                description = task.description,
                dueDate = task.dueDate?.toString(),
                isCompleted = task.isCompleted,
                priority = task.priority.name,
                linkedGoalId = task.linkedGoalId,
                createdAt = task.createdAt.toEpochMilli(),
                updatedAt = task.updatedAt.toEpochMilli(),
                syncStatus = task.syncStatus.name
            )
        }
    }
}
