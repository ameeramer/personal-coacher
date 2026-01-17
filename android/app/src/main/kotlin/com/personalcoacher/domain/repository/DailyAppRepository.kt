package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppData
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing AI-generated daily web apps.
 */
interface DailyAppRepository {

    // ==================== App Management ====================

    /**
     * Get all apps for a user, ordered by date descending.
     */
    fun getApps(userId: String): Flow<List<DailyApp>>

    /**
     * Get all liked apps (My Tools library).
     */
    fun getLikedApps(userId: String): Flow<List<DailyApp>>

    /**
     * Get today's app for a user.
     */
    fun getTodaysApp(userId: String): Flow<DailyApp?>

    /**
     * Get an app by ID.
     */
    fun getAppById(id: String): Flow<DailyApp?>

    /**
     * Generate a new app for today based on journal entries.
     * @param userId The user's ID
     * @param apiKey The Claude API key for generation
     * @return Resource containing the generated app or an error
     */
    suspend fun generateTodaysApp(userId: String, apiKey: String): Resource<DailyApp>

    /**
     * Update the status of an app (like/dislike).
     */
    suspend fun updateAppStatus(appId: String, status: DailyAppStatus): Resource<Unit>

    /**
     * Mark an app as used (record first open time).
     */
    suspend fun markAppAsUsed(appId: String): Resource<Unit>

    /**
     * Delete an app.
     */
    suspend fun deleteApp(appId: String): Resource<Unit>

    /**
     * Get count of liked apps.
     */
    suspend fun getLikedAppCount(userId: String): Int

    // ==================== App Data (Key-Value Storage) ====================

    /**
     * Get all data for an app.
     */
    fun getAppData(appId: String): Flow<List<DailyAppData>>

    /**
     * Get a specific data value by key.
     */
    suspend fun getDataByKey(appId: String, key: String): String?

    /**
     * Save a data value.
     */
    suspend fun saveData(appId: String, key: String, value: String): Resource<Unit>

    /**
     * Get all data for an app as a map (for JavaScript bridge).
     */
    suspend fun getAllDataAsMap(appId: String): Map<String, String>

    /**
     * Delete a specific data entry.
     */
    suspend fun deleteData(appId: String, key: String): Resource<Unit>

    /**
     * Clear all data for an app.
     */
    suspend fun clearAllData(appId: String): Resource<Unit>

    // ==================== Sync ====================

    /**
     * Upload local apps to server (backup).
     */
    suspend fun uploadApps(userId: String): Resource<Unit>

    /**
     * Download apps from server (restore).
     */
    suspend fun downloadApps(userId: String): Resource<Unit>
}
