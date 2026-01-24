package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.GoalDao
import com.personalcoacher.data.local.entity.GoalEntity
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.GoalRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao
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
}
