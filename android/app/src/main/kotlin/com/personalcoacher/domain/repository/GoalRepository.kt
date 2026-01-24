package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface GoalRepository {
    fun getGoals(userId: String, limit: Int = 100): Flow<List<Goal>>

    fun getActiveGoals(userId: String): Flow<List<Goal>>

    fun getGoalsByStatus(userId: String, status: GoalStatus): Flow<List<Goal>>

    fun getGoalById(id: String): Flow<Goal?>

    fun searchGoals(userId: String, query: String): Flow<List<Goal>>

    suspend fun createGoal(
        userId: String,
        title: String,
        description: String,
        targetDate: LocalDate?,
        priority: Priority
    ): Resource<Goal>

    suspend fun updateGoal(
        id: String,
        title: String,
        description: String,
        targetDate: LocalDate?,
        priority: Priority
    ): Resource<Goal>

    suspend fun updateGoalStatus(id: String, status: GoalStatus): Resource<Goal>

    suspend fun deleteGoal(id: String): Resource<Unit>
}
