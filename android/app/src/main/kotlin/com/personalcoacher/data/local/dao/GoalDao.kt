package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END, createdAt DESC LIMIT :limit")
    fun getGoalsForUser(userId: String, limit: Int = 100): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND status = :status ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END, createdAt DESC")
    fun getGoalsByStatus(userId: String, status: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND status = 'ACTIVE' ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END, createdAt DESC")
    fun getActiveGoals(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END, createdAt DESC LIMIT :limit")
    suspend fun getGoalsForUserSync(userId: String, limit: Int = 100): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    fun getGoalById(id: String): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalByIdSync(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE syncStatus = :status")
    suspend fun getGoalsBySyncStatus(status: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("UPDATE goals SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGoalStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE goals SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String)

    @Query("DELETE FROM goals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM goals WHERE userId = :userId")
    suspend fun getGoalCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM goals WHERE userId = :userId AND status = 'ACTIVE'")
    suspend fun getActiveGoalCount(userId: String): Int

    @Query("SELECT * FROM goals WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchGoals(userId: String, query: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun getGoalsModifiedSince(userId: String, since: Long): List<GoalEntity>

    @Query("SELECT id FROM goals WHERE userId = :userId")
    suspend fun getAllIdsForUser(userId: String): List<String>
}
