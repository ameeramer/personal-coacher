package com.personalcoacher.data.repository

import android.util.Log
import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.entity.DailyAppDataEntity
import com.personalcoacher.data.local.entity.DailyAppEntity
import com.personalcoacher.data.remote.DailyAppGenerationService
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateDailyToolRequest
import com.personalcoacher.data.remote.dto.GenerateDailyToolRequest
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppData
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.notification.KuzuSyncScheduler
import com.personalcoacher.notification.KuzuSyncWorker
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyAppRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val dailyAppDao: DailyAppDao,
    private val journalEntryDao: JournalEntryDao,
    private val generationService: DailyAppGenerationService,
    private val kuzuSyncScheduler: KuzuSyncScheduler
) : DailyAppRepository {

    companion object {
        private const val TAG = "DailyAppRepository"
    }

    // ==================== App Management ====================

    override fun getApps(userId: String): Flow<List<DailyApp>> {
        return dailyAppDao.getAppsForUser(userId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getLikedApps(userId: String): Flow<List<DailyApp>> {
        return dailyAppDao.getLikedAppsForUser(userId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getTodaysApp(userId: String): Flow<DailyApp?> {
        val (startOfDay, endOfDay) = getTodayRange()
        return dailyAppDao.getTodaysApp(userId, startOfDay, endOfDay)
            .map { it?.toDomainModel() }
    }

    override fun getAppById(id: String): Flow<DailyApp?> {
        return dailyAppDao.getAppById(id)
            .map { it?.toDomainModel() }
    }

    override suspend fun generateTodaysApp(userId: String, apiKey: String, forceRegenerate: Boolean): Resource<DailyApp> {
        return try {
            // Check if we already have an app for today (unless forceRegenerate)
            if (!forceRegenerate) {
                val (startOfDay, endOfDay) = getTodayRange()
                val existingApp = dailyAppDao.getTodaysAppSync(userId, startOfDay, endOfDay)
                if (existingApp != null) {
                    return Resource.success(existingApp.toDomainModel())
                }
            }

            // Try cloud generation first (more reliable - doesn't get killed by Android)
            Log.d(TAG, "Attempting cloud-based generation (forceRegenerate=$forceRegenerate)")
            val cloudResult = generateViaCloud(userId, forceRegenerate)
            if (cloudResult is Resource.Success) {
                return cloudResult
            }

            // Cloud generation failed - fall back to local generation if API key is available
            Log.w(TAG, "Cloud generation failed: ${(cloudResult as? Resource.Error)?.message}. Falling back to local generation.")

            if (apiKey.isBlank()) {
                return Resource.error("Cloud generation failed and no local API key available. ${cloudResult.message}")
            }

            // Get recent journal entries for local generation
            val recentEntries = journalEntryDao.getRecentEntriesSync(userId, 7)
            if (recentEntries.isEmpty()) {
                return Resource.error("No journal entries found. Start journaling to get personalized daily tools!")
            }

            // Get recent tools to avoid duplicates (last 14 tools)
            val previousTools = dailyAppDao.getRecentAppsSync(userId, 14)
                .map { it.toDomainModel() }

            Log.d(TAG, "Generating locally with ${previousTools.size} previous tools for context")

            // Generate the app using Claude (local)
            val result = generationService.generateApp(
                apiKey,
                recentEntries.map { it.toDomainModel() },
                previousTools
            )

            when (result) {
                is Resource.Success -> {
                    val generatedContent = result.data!!
                    val now = Instant.now()

                    // If forceRegenerate, delete existing app first
                    if (forceRegenerate) {
                        val (startOfDay, endOfDay) = getTodayRange()
                        dailyAppDao.getTodaysAppSync(userId, startOfDay, endOfDay)?.let {
                            dailyAppDao.deleteApp(it.id)
                        }
                    }

                    val app = DailyApp(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        date = now,
                        title = generatedContent.title,
                        description = generatedContent.description,
                        htmlCode = generatedContent.htmlCode,
                        journalContext = generatedContent.journalContext,
                        status = DailyAppStatus.PENDING,
                        usedAt = null,
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )

                    // Save to database
                    dailyAppDao.insertApp(DailyAppEntity.fromDomainModel(app))

                    // Schedule RAG knowledge graph sync
                    kuzuSyncScheduler.scheduleImmediateSync(userId, KuzuSyncWorker.SYNC_TYPE_DAILY_APP)

                    Resource.success(app)
                }
                is Resource.Error -> {
                    Resource.error(result.message ?: "Failed to generate app")
                }
                is Resource.Loading -> {
                    Resource.error("Unexpected loading state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate app", e)
            Resource.error("Failed to generate app: ${e.localizedMessage}")
        }
    }

    /**
     * Generate a daily tool using the cloud-based API endpoint.
     * This is more reliable because:
     * 1. Server doesn't get killed by Android battery optimization
     * 2. Vercel functions can run for up to 60s (Hobby) or 5min (Pro)
     * 3. API key stays secure on the server
     */
    private suspend fun generateViaCloud(userId: String, forceRegenerate: Boolean): Resource<DailyApp> {
        return try {
            Log.d(TAG, "Calling cloud generation API")
            val response = api.generateDailyTool(GenerateDailyToolRequest(forceRegenerate))

            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val app = dto.toDomainModel()

                // Save to local database (it's already saved on server, but we need local copy)
                dailyAppDao.insertApp(DailyAppEntity.fromDomainModel(app))

                // Schedule RAG knowledge graph sync
                kuzuSyncScheduler.scheduleImmediateSync(userId, KuzuSyncWorker.SYNC_TYPE_DAILY_APP)

                Log.d(TAG, "Cloud generation successful: ${app.title}")
                Resource.success(app)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = when (response.code()) {
                    400 -> errorBody?.let { parseErrorMessage(it) } ?: "No journal entries found"
                    401 -> "Not authenticated. Please log in again."
                    429 -> "Rate limited. Please try again later."
                    503 -> "Server overloaded. Please try again later."
                    else -> "Server error: ${response.code()}"
                }
                Log.w(TAG, "Cloud generation failed: $errorMessage")
                Resource.error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud generation exception", e)
            Resource.error("Network error: ${e.localizedMessage}")
        }
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
            json.get("error")?.asString ?: errorBody
        } catch (e: Exception) {
            errorBody
        }
    }

    override suspend fun updateAppStatus(appId: String, status: DailyAppStatus): Resource<Unit> {
        return try {
            val now = Instant.now().toEpochMilli()
            dailyAppDao.updateAppStatus(appId, status.name, now)
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Failed to update app status: ${e.localizedMessage}")
        }
    }

    override suspend fun markAppAsUsed(appId: String): Resource<Unit> {
        return try {
            val app = dailyAppDao.getAppByIdSync(appId)
            if (app != null && app.usedAt == null) {
                val now = Instant.now().toEpochMilli()
                dailyAppDao.updateUsedAt(appId, now, now)
            }
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Failed to mark app as used: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteApp(appId: String): Resource<Unit> {
        return try {
            dailyAppDao.deleteApp(appId)
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Failed to delete app: ${e.localizedMessage}")
        }
    }

    override suspend fun getLikedAppCount(userId: String): Int {
        return dailyAppDao.getLikedAppCount(userId)
    }

    // ==================== App Data (Key-Value Storage) ====================

    override fun getAppData(appId: String): Flow<List<DailyAppData>> {
        return dailyAppDao.getDataForApp(appId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override suspend fun getDataByKey(appId: String, key: String): String? {
        return dailyAppDao.getDataByKey(appId, key)?.value
    }

    override suspend fun saveData(appId: String, key: String, value: String): Resource<Unit> {
        return try {
            val data = DailyAppDataEntity(
                appId = appId,
                key = key,
                value = value,
                updatedAt = Instant.now().toEpochMilli(),
                syncStatus = SyncStatus.LOCAL_ONLY.name
            )
            dailyAppDao.insertData(data)
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Failed to save data: ${e.localizedMessage}")
        }
    }

    override suspend fun getAllDataAsMap(appId: String): Map<String, String> {
        return dailyAppDao.getDataForAppSync(appId)
            .associate { it.key to it.value }
    }

    override suspend fun deleteData(appId: String, key: String): Resource<Unit> {
        return try {
            dailyAppDao.deleteData(appId, key)
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Failed to delete data: ${e.localizedMessage}")
        }
    }

    override suspend fun clearAllData(appId: String): Resource<Unit> {
        return try {
            dailyAppDao.deleteAllDataForApp(appId)
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Failed to clear data: ${e.localizedMessage}")
        }
    }

    // ==================== Sync ====================

    override suspend fun uploadApps(userId: String): Resource<Unit> {
        return try {
            // Upload all local-only apps to server
            val localApps = dailyAppDao.getAppsBySyncStatus(SyncStatus.LOCAL_ONLY.name)
            var uploadedCount = 0
            var failedCount = 0

            Log.d(TAG, "Uploading ${localApps.size} daily tools to server")

            for (app in localApps) {
                try {
                    dailyAppDao.updateSyncStatus(app.id, SyncStatus.SYNCING.name)
                    val response = api.createDailyTool(
                        CreateDailyToolRequest(
                            id = app.id,
                            date = Instant.ofEpochMilli(app.date).toString(),
                            title = app.title,
                            description = app.description,
                            htmlCode = app.htmlCode,
                            journalContext = app.journalContext,
                            status = app.status,
                            usedAt = app.usedAt?.let { Instant.ofEpochMilli(it).toString() },
                            createdAt = Instant.ofEpochMilli(app.createdAt).toString(),
                            updatedAt = Instant.ofEpochMilli(app.updatedAt).toString()
                        )
                    )
                    if (response.isSuccessful) {
                        dailyAppDao.updateSyncStatus(app.id, SyncStatus.SYNCED.name)
                        uploadedCount++
                    } else {
                        Log.w(TAG, "Failed to upload app ${app.id}: ${response.code()}")
                        dailyAppDao.updateSyncStatus(app.id, SyncStatus.LOCAL_ONLY.name)
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception uploading app ${app.id}", e)
                    dailyAppDao.updateSyncStatus(app.id, SyncStatus.LOCAL_ONLY.name)
                    failedCount++
                }
            }

            Log.d(TAG, "Upload complete: $uploadedCount uploaded, $failedCount failed")

            if (failedCount > 0) {
                Resource.error("Uploaded $uploadedCount tools, $failedCount failed")
            } else {
                Resource.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Resource.error("Backup failed: ${e.localizedMessage}")
        }
    }

    override suspend fun downloadApps(userId: String): Resource<Unit> {
        return try {
            Log.d(TAG, "Downloading daily tools from server")
            val response = api.getDailyTools()
            if (response.isSuccessful && response.body() != null) {
                val serverApps = response.body()!!
                Log.d(TAG, "Downloaded ${serverApps.size} daily tools")

                for (dto in serverApps) {
                    // Only save if this app belongs to the current user
                    if (dto.userId == userId) {
                        val app = dto.toDomainModel()
                        dailyAppDao.insertApp(DailyAppEntity.fromDomainModel(app))
                    }
                }
                Resource.success(Unit)
            } else {
                Log.w(TAG, "Download failed: ${response.code()}")
                Resource.error("Download failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Resource.error("Download failed: ${e.localizedMessage}")
        }
    }

    // ==================== Helpers ====================

    private fun getTodayRange(): Pair<Long, Long> {
        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return startOfDay to endOfDay
    }
}
