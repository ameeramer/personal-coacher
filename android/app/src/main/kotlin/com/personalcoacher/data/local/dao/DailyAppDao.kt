package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.DailyAppDataEntity
import com.personalcoacher.data.local.entity.DailyAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAppDao {

    // ==================== Daily Apps ====================

    /**
     * Get all apps for a user, ordered by date descending.
     */
    @Query("SELECT * FROM daily_apps WHERE userId = :userId ORDER BY date DESC")
    fun getAppsForUser(userId: String): Flow<List<DailyAppEntity>>

    /**
     * Get all liked apps for a user (My Tools library).
     */
    @Query("SELECT * FROM daily_apps WHERE userId = :userId AND status = 'LIKED' ORDER BY updatedAt DESC")
    fun getLikedAppsForUser(userId: String): Flow<List<DailyAppEntity>>

    /**
     * Get today's app for a user.
     */
    @Query("SELECT * FROM daily_apps WHERE userId = :userId AND date >= :startOfDay AND date < :endOfDay ORDER BY createdAt DESC LIMIT 1")
    fun getTodaysApp(userId: String, startOfDay: Long, endOfDay: Long): Flow<DailyAppEntity?>

    /**
     * Get today's app synchronously (for workers).
     */
    @Query("SELECT * FROM daily_apps WHERE userId = :userId AND date >= :startOfDay AND date < :endOfDay ORDER BY createdAt DESC LIMIT 1")
    suspend fun getTodaysAppSync(userId: String, startOfDay: Long, endOfDay: Long): DailyAppEntity?

    /**
     * Get an app by ID.
     */
    @Query("SELECT * FROM daily_apps WHERE id = :id")
    fun getAppById(id: String): Flow<DailyAppEntity?>

    /**
     * Get an app by ID synchronously.
     */
    @Query("SELECT * FROM daily_apps WHERE id = :id")
    suspend fun getAppByIdSync(id: String): DailyAppEntity?

    /**
     * Get apps by sync status (for backup).
     */
    @Query("SELECT * FROM daily_apps WHERE syncStatus = :status")
    suspend fun getAppsBySyncStatus(status: String): List<DailyAppEntity>

    /**
     * Insert or replace an app.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: DailyAppEntity)

    /**
     * Insert multiple apps.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<DailyAppEntity>)

    /**
     * Update an existing app.
     */
    @Update
    suspend fun updateApp(app: DailyAppEntity)

    /**
     * Update app status (liked/disliked).
     */
    @Query("UPDATE daily_apps SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAppStatus(id: String, status: String, updatedAt: Long)

    /**
     * Update app sync status.
     */
    @Query("UPDATE daily_apps SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    /**
     * Update usedAt timestamp.
     */
    @Query("UPDATE daily_apps SET usedAt = :usedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUsedAt(id: String, usedAt: Long, updatedAt: Long)

    /**
     * Delete an app.
     */
    @Query("DELETE FROM daily_apps WHERE id = :id")
    suspend fun deleteApp(id: String)

    /**
     * Delete all apps for a user.
     */
    @Query("DELETE FROM daily_apps WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    /**
     * Get count of liked apps for a user.
     */
    @Query("SELECT COUNT(*) FROM daily_apps WHERE userId = :userId AND status = 'LIKED'")
    suspend fun getLikedAppCount(userId: String): Int

    /**
     * Get recent apps for a user (for AI context to avoid duplicates).
     * Returns the most recent N apps ordered by date descending.
     */
    @Query("SELECT * FROM daily_apps WHERE userId = :userId ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentAppsSync(userId: String, limit: Int): List<DailyAppEntity>

    // ==================== Daily App Data (Key-Value Storage) ====================

    /**
     * Get all data for an app.
     */
    @Query("SELECT * FROM daily_app_data WHERE appId = :appId ORDER BY key")
    fun getDataForApp(appId: String): Flow<List<DailyAppDataEntity>>

    /**
     * Get all data for an app synchronously.
     */
    @Query("SELECT * FROM daily_app_data WHERE appId = :appId ORDER BY key")
    suspend fun getDataForAppSync(appId: String): List<DailyAppDataEntity>

    /**
     * Get a specific data entry by app ID and key.
     */
    @Query("SELECT * FROM daily_app_data WHERE appId = :appId AND key = :key")
    suspend fun getDataByKey(appId: String, key: String): DailyAppDataEntity?

    /**
     * Get data by sync status (for backup).
     */
    @Query("SELECT * FROM daily_app_data WHERE syncStatus = :status")
    suspend fun getDataBySyncStatus(status: String): List<DailyAppDataEntity>

    /**
     * Insert or replace a data entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertData(data: DailyAppDataEntity)

    /**
     * Insert multiple data entries.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataBatch(data: List<DailyAppDataEntity>)

    /**
     * Update data sync status.
     */
    @Query("UPDATE daily_app_data SET syncStatus = :status WHERE appId = :appId AND key = :key")
    suspend fun updateDataSyncStatus(appId: String, key: String, status: String)

    /**
     * Delete a specific data entry.
     */
    @Query("DELETE FROM daily_app_data WHERE appId = :appId AND key = :key")
    suspend fun deleteData(appId: String, key: String)

    /**
     * Delete all data for an app.
     */
    @Query("DELETE FROM daily_app_data WHERE appId = :appId")
    suspend fun deleteAllDataForApp(appId: String)
}
