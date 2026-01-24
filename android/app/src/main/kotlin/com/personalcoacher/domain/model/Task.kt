package com.personalcoacher.domain.model

import java.time.Instant
import java.time.LocalDate

data class Task(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val dueDate: LocalDate?,
    val isCompleted: Boolean,
    val priority: Priority,
    val linkedGoalId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
