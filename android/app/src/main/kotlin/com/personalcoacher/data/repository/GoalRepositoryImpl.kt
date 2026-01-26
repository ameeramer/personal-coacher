package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.GoalDao
import com.personalcoacher.data.local.entity.GoalEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateGoalRequest
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.GoalRepository
import com.personalcoacher.notification.KuzuSyncScheduler
import com.personalcoacher.notification.KuzuSyncWorker
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val goalDao: GoalDao,
    private val kuzuSyncScheduler: KuzuSyncScheduler
) : GoalRepository {

    override fun getGoals(userId: String, limit: Int): Flow<List<Goal>> {
        return goalDao.getGoalsForUser(userId, limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getActiveGoals(userId: String): Flow<List<Goal>> {
        return goalDao.getActiveGoals(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getGoalsByStatus(userId: String, status: GoalStatus): Flow<List<Goal>> {
        return goalDao.getGoalsByStatus(userId, status.name).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getGoalById(id: String): Flow<Goal?> {
        return goalDao.getGoalById(id).map { it?.toDomainModel() }
    }

    override fun searchGoals(userId: String, query: String): Flow<List<Goal>> {
        return goalDao.searchGoals(userId, query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun createGoal(
        userId: String,
        title: String,
        description: String,
        targetDate: LocalDate?,
        priority: Priority
    ): Resource<Goal> {
        return try {
            val now = Instant.now()
            val goal = Goal(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                description = description,
                targetDate = targetDate,
                status = GoalStatus.ACTIVE,
                priority = priority,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            goalDao.insertGoal(GoalEntity.fromDomainModel(goal))

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(userId, KuzuSyncWorker.SYNC_TYPE_GOAL)

            Resource.Success(goal)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create goal")
        }
    }

    override suspend fun updateGoal(
        id: String,
        title: String,
        description: String,
        targetDate: LocalDate?,
        priority: Priority
    ): Resource<Goal> {
        return try {
            val existingGoal = goalDao.getGoalByIdSync(id)
                ?: return Resource.Error("Goal not found")

            val updatedGoal = existingGoal.toDomainModel().copy(
                title = title,
                description = description,
                targetDate = targetDate,
                priority = priority,
                updatedAt = Instant.now(),
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            goalDao.updateGoal(GoalEntity.fromDomainModel(updatedGoal))

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(updatedGoal.userId, KuzuSyncWorker.SYNC_TYPE_GOAL)

            Resource.Success(updatedGoal)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update goal")
        }
    }

    override suspend fun updateGoalStatus(id: String, status: GoalStatus): Resource<Goal> {
        return try {
            val now = Instant.now()
            goalDao.updateGoalStatus(id, status.name, now.toEpochMilli())

            val updatedGoal = goalDao.getGoalByIdSync(id)
                ?: return Resource.Error("Goal not found")

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(updatedGoal.userId, KuzuSyncWorker.SYNC_TYPE_GOAL)

            Resource.Success(updatedGoal.toDomainModel())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update goal status")
        }
    }

    override suspend fun deleteGoal(id: String): Resource<Unit> {
        return try {
            goalDao.deleteGoal(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete goal")
        }
    }

    override suspend fun syncGoals(userId: String): Resource<Unit> {
        return try {
            // Download goals from server (manual download action)
            val response = api.getGoals()
            if (response.isSuccessful && response.body() != null) {
                val serverGoals = response.body()!!
                serverGoals.forEach { dto ->
                    goalDao.insertGoal(
                        GoalEntity.fromDomainModel(
                            dto.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                        )
                    )
                }
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Download failed: ${e.localizedMessage}")
        }
    }

    override suspend fun uploadGoals(userId: String): Resource<Unit> {
        return try {
            // Upload all local-only goals to server (manual backup action)
            val localGoals = goalDao.getGoalsBySyncStatus(SyncStatus.LOCAL_ONLY.name)
            var uploadedCount = 0
            var failedCount = 0

            for (goal in localGoals) {
                try {
                    goalDao.updateSyncStatus(goal.id, SyncStatus.SYNCING.name)
                    val response = api.createGoal(
                        CreateGoalRequest(
                            title = goal.title,
                            description = goal.description,
                            targetDate = goal.targetDate,
                            priority = goal.priority
                        )
                    )
                    if (response.isSuccessful && response.body() != null) {
                        val serverGoal = response.body()!!
                        goalDao.deleteGoal(goal.id)
                        goalDao.insertGoal(
                            GoalEntity.fromDomainModel(
                                serverGoal.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                            )
                        )
                        uploadedCount++
                    } else {
                        goalDao.updateSyncStatus(goal.id, SyncStatus.LOCAL_ONLY.name)
                        failedCount++
                    }
                } catch (e: Exception) {
                    goalDao.updateSyncStatus(goal.id, SyncStatus.LOCAL_ONLY.name)
                    failedCount++
                }
            }

            if (failedCount > 0) {
                Resource.Error("Uploaded $uploadedCount goals, $failedCount failed")
            } else if (uploadedCount == 0) {
                Resource.Success(Unit) // Nothing to upload
            } else {
                Resource.Success(Unit)
            }
        } catch (e: Exception) {
            Resource.Error("Backup failed: ${e.localizedMessage}")
        }
    }
}
