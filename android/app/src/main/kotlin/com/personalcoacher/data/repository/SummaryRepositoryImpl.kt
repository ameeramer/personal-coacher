package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateSummaryRequest
import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.SummaryRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val summaryDao: SummaryDao
) : SummaryRepository {

    override fun getSummaries(userId: String, limit: Int): Flow<List<Summary>> {
        return summaryDao.getSummariesForUser(userId, limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getSummariesByType(userId: String, type: SummaryType, limit: Int): Flow<List<Summary>> {
        return summaryDao.getSummariesByType(userId, type.toApiString(), limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getSummaryById(id: String): Flow<Summary?> {
        return summaryDao.getSummaryById(id)
            .map { it?.toDomainModel() }
    }

    override suspend fun generateSummary(userId: String, type: SummaryType): Resource<Summary> {
        return try {
            val response = api.createSummary(CreateSummaryRequest(type.toApiString()))

            if (response.isSuccessful && response.body() != null) {
                val summary = response.body()!!.toDomainModel()
                summaryDao.insertSummary(
                    SummaryEntity.fromDomainModel(summary.copy(syncStatus = SyncStatus.SYNCED))
                )
                Resource.success(summary)
            } else {
                val errorBody = response.errorBody()?.string()
                if (errorBody?.contains("No journal entries") == true) {
                    Resource.error("No journal entries found for this period")
                } else {
                    Resource.error("Failed to generate summary: ${response.message()}")
                }
            }
        } catch (e: Exception) {
            Resource.error("Failed to generate summary: ${e.localizedMessage ?: "Network error"}")
        }
    }

    override suspend fun syncSummaries(userId: String): Resource<Unit> {
        return try {
            val response = api.getSummaries()
            if (response.isSuccessful && response.body() != null) {
                val serverSummaries = response.body()!!
                serverSummaries.forEach { dto ->
                    summaryDao.insertSummary(
                        SummaryEntity.fromDomainModel(
                            dto.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                        )
                    )
                }
            }
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Sync failed: ${e.localizedMessage}")
        }
    }
}
