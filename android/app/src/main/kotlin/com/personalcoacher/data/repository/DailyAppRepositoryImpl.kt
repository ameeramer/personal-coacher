package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.entity.DailyAppDataEntity
import com.personalcoacher.data.local.entity.DailyAppEntity
import com.personalcoacher.data.remote.DailyAppGenerationService
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppData
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.DailyAppRepository
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
    private val dailyAppDao: DailyAppDao,
    private val journalEntryDao: JournalEntryDao,
    private val generationService: DailyAppGenerationService
) : DailyAppRepository {

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

    override suspend fun generateTodaysApp(userId: String, apiKey: String): Resource<DailyApp> {
        return try {
            // Check if we already have an app for today
            val (startOfDay, endOfDay) = getTodayRange()
            val existingApp = dailyAppDao.getTodaysAppSync(userId, startOfDay, endOfDay)
            if (existingApp != null) {
                return Resource.success(existingApp.toDomainModel())
            }

            // Get recent journal entries for context
            val recentEntries = journalEntryDao.getRecentEntriesSync(userId, 7)
            if (recentEntries.isEmpty()) {
                return Resource.error("No journal entries found. Start journaling to get personalized daily tools!")
            }

            // Generate the app using Claude
            val result = generationService.generateApp(apiKey, recentEntries.map { it.toDomainModel() })

            when (result) {
                is Resource.Success -> {
                    val generatedContent = result.data!!
                    val now = Instant.now()

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
            Resource.error("Failed to generate app: ${e.localizedMessage}")
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
        // TODO: Implement server sync when backend API is ready
        // For now, just mark local apps as "pending sync"
        return Resource.success(Unit)
    }

    override suspend fun downloadApps(userId: String): Resource<Unit> {
        // TODO: Implement server sync when backend API is ready
        return Resource.success(Unit)
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
