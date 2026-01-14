package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleRuleDao {

    @Query("SELECT * FROM schedule_rules WHERE userId = :userId ORDER BY createdAt DESC")
    fun getScheduleRulesByUser(userId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE userId = :userId AND enabled = 1 ORDER BY createdAt DESC")
    fun getEnabledScheduleRulesByUser(userId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE userId = :userId AND enabled = 1")
    suspend fun getEnabledScheduleRulesSync(userId: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE id = :id")
    suspend fun getScheduleRuleById(id: String): ScheduleRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleRule(rule: ScheduleRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleRules(rules: List<ScheduleRuleEntity>)

    @Update
    suspend fun updateScheduleRule(rule: ScheduleRuleEntity)

    @Delete
    suspend fun deleteScheduleRule(rule: ScheduleRuleEntity)

    @Query("DELETE FROM schedule_rules WHERE id = :id")
    suspend fun deleteScheduleRuleById(id: String)

    @Query("DELETE FROM schedule_rules WHERE userId = :userId")
    suspend fun deleteAllScheduleRulesForUser(userId: String)

    @Query("UPDATE schedule_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setScheduleRuleEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM schedule_rules WHERE userId = :userId")
    suspend fun getScheduleRuleCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM schedule_rules WHERE userId = :userId AND enabled = 1")
    suspend fun getEnabledScheduleRuleCount(userId: String): Int
}
