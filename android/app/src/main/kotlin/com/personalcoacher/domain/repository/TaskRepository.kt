package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.Task
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun getTasks(userId: String, limit: Int = 100): Flow<List<Task>>

    fun getPendingTasks(userId: String): Flow<List<Task>>

    fun getCompletedTasks(userId: String): Flow<List<Task>>

    fun getTasksForGoal(goalId: String): Flow<List<Task>>

    fun getTasksDueOn(userId: String, date: LocalDate): Flow<List<Task>>

    fun getOverdueTasks(userId: String): Flow<List<Task>>

    fun getTaskById(id: String): Flow<Task?>

    fun searchTasks(userId: String, query: String): Flow<List<Task>>

    suspend fun createTask(
        userId: String,
        title: String,
        description: String,
        dueDate: LocalDate?,
        priority: Priority,
        linkedGoalId: String?
    ): Resource<Task>

    suspend fun updateTask(
        id: String,
        title: String,
        description: String,
        dueDate: LocalDate?,
        priority: Priority,
        linkedGoalId: String?
    ): Resource<Task>

    suspend fun toggleTaskCompletion(id: String): Resource<Task>

    suspend fun deleteTask(id: String): Resource<Unit>

    suspend fun syncTasks(userId: String): Resource<Unit>

    suspend fun uploadTasks(userId: String): Resource<Unit>
}
