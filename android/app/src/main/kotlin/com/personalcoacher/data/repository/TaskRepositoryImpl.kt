package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.TaskDao
import com.personalcoacher.data.local.entity.TaskEntity
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.model.Task
import com.personalcoacher.domain.repository.TaskRepository
import com.personalcoacher.notification.KuzuSyncScheduler
import com.personalcoacher.notification.KuzuSyncWorker
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val kuzuSyncScheduler: KuzuSyncScheduler
) : TaskRepository {

    override fun getTasks(userId: String, limit: Int): Flow<List<Task>> {
        return taskDao.getTasksForUser(userId, limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getPendingTasks(userId: String): Flow<List<Task>> {
        return taskDao.getPendingTasks(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getCompletedTasks(userId: String): Flow<List<Task>> {
        return taskDao.getCompletedTasks(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getTasksForGoal(goalId: String): Flow<List<Task>> {
        return taskDao.getTasksForGoal(goalId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getTasksDueOn(userId: String, date: LocalDate): Flow<List<Task>> {
        return taskDao.getTasksDueOn(userId, date.toString()).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getOverdueTasks(userId: String): Flow<List<Task>> {
        val today = LocalDate.now().toString()
        return taskDao.getOverdueTasks(userId, today).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getTaskById(id: String): Flow<Task?> {
        return taskDao.getTaskById(id).map { it?.toDomainModel() }
    }

    override fun searchTasks(userId: String, query: String): Flow<List<Task>> {
        return taskDao.searchTasks(userId, query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun createTask(
        userId: String,
        title: String,
        description: String,
        dueDate: LocalDate?,
        priority: Priority,
        linkedGoalId: String?
    ): Resource<Task> {
        return try {
            val now = Instant.now()
            val task = Task(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                description = description,
                dueDate = dueDate,
                isCompleted = false,
                priority = priority,
                linkedGoalId = linkedGoalId,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            taskDao.insertTask(TaskEntity.fromDomainModel(task))

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(userId, KuzuSyncWorker.SYNC_TYPE_TASK)

            Resource.Success(task)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create task")
        }
    }

    override suspend fun updateTask(
        id: String,
        title: String,
        description: String,
        dueDate: LocalDate?,
        priority: Priority,
        linkedGoalId: String?
    ): Resource<Task> {
        return try {
            val existingTask = taskDao.getTaskByIdSync(id)
                ?: return Resource.Error("Task not found")

            val updatedTask = existingTask.toDomainModel().copy(
                title = title,
                description = description,
                dueDate = dueDate,
                priority = priority,
                linkedGoalId = linkedGoalId,
                updatedAt = Instant.now(),
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            taskDao.updateTask(TaskEntity.fromDomainModel(updatedTask))

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(updatedTask.userId, KuzuSyncWorker.SYNC_TYPE_TASK)

            Resource.Success(updatedTask)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update task")
        }
    }

    override suspend fun toggleTaskCompletion(id: String): Resource<Task> {
        return try {
            val existingTask = taskDao.getTaskByIdSync(id)
                ?: return Resource.Error("Task not found")

            val now = Instant.now()
            val newCompletionState = !existingTask.isCompleted
            taskDao.updateTaskCompletion(id, newCompletionState, now.toEpochMilli())

            val updatedTask = taskDao.getTaskByIdSync(id)
                ?: return Resource.Error("Task not found after update")

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(updatedTask.userId, KuzuSyncWorker.SYNC_TYPE_TASK)

            Resource.Success(updatedTask.toDomainModel())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to toggle task completion")
        }
    }

    override suspend fun deleteTask(id: String): Resource<Unit> {
        return try {
            taskDao.deleteTask(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete task")
        }
    }
}
