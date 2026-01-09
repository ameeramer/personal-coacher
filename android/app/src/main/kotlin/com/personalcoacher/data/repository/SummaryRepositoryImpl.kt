package com.personalcoacher.data.repository

import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.SummaryRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val claudeApi: ClaudeApiService,
    private val tokenManager: TokenManager,
    private val journalEntryDao: JournalEntryDao,
    private val summaryDao: SummaryDao
) : SummaryRepository {

    companion object {
        private const val SUMMARY_SYSTEM_PROMPT = """You are a skilled summarizer helping users reflect on their journal entries. Your task is to:

1. **Capture Key Themes**: Identify the main topics, emotions, and events from the entries.

2. **Note Patterns**: Highlight any recurring themes, moods, or situations.

3. **Acknowledge Growth**: Point out any progress, insights, or positive developments.

4. **Gentle Observations**: Offer thoughtful observations that might help the user see their experiences from a new perspective.

5. **Forward Looking**: End with an encouraging note or gentle question for reflection.

Format your summary in a readable way with clear sections. Keep it concise but meaningful."""
    }

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
        // Check for API key first
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            return Resource.error("Please configure your Claude API key in Settings to use this feature")
        }

        return try {
            // Calculate date range based on summary type
            val now = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val (startDate, endDate) = when (type) {
                SummaryType.DAILY -> {
                    val start = now.atStartOfDay(zone).toInstant()
                    val end = now.atTime(LocalTime.MAX).atZone(zone).toInstant()
                    start to end
                }
                SummaryType.WEEKLY -> {
                    val start = now.minusDays(6).atStartOfDay(zone).toInstant()
                    val end = now.atTime(LocalTime.MAX).atZone(zone).toInstant()
                    start to end
                }
                SummaryType.MONTHLY -> {
                    val start = now.minusDays(29).atStartOfDay(zone).toInstant()
                    val end = now.atTime(LocalTime.MAX).atZone(zone).toInstant()
                    start to end
                }
            }

            // Get journal entries from local database
            val entries = journalEntryDao.getEntriesInRangeSync(
                userId = userId,
                startDate = startDate.toEpochMilli(),
                endDate = endDate.toEpochMilli()
            )

            if (entries.isEmpty()) {
                return Resource.error("No journal entries found for this period")
            }

            // Format entries for Claude
            val periodLabel = when (type) {
                SummaryType.DAILY -> "day"
                SummaryType.WEEKLY -> "week"
                SummaryType.MONTHLY -> "month"
            }

            val entriesText = entries.joinToString("\n\n---\n\n") { entry ->
                val dateStr = Instant.ofEpochMilli(entry.date)
                    .atZone(zone)
                    .toLocalDate()
                    .toString()
                buildString {
                    append("Date: $dateStr\n")
                    if (!entry.mood.isNullOrBlank()) append("Mood: ${entry.mood}\n")
                    if (entry.tags.isNotEmpty()) append("Tags: ${entry.tags}\n")
                    append("\n${entry.content}")
                }
            }

            val userPrompt = "Please provide a thoughtful summary of this $periodLabel's journal entries:\n\n$entriesText"

            // Call Claude API directly
            val response = claudeApi.sendMessage(
                apiKey = apiKey,
                request = ClaudeMessageRequest(
                    system = SUMMARY_SYSTEM_PROMPT,
                    messages = listOf(ClaudeMessage(role = "user", content = userPrompt))
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val summaryContent = result.content.firstOrNull()?.text ?: ""

                // Create summary with local ID and save locally
                val createdAt = Instant.now()
                val summary = Summary(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = type,
                    content = summaryContent,
                    startDate = startDate,
                    endDate = endDate,
                    createdAt = createdAt,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )

                summaryDao.insertSummary(SummaryEntity.fromDomainModel(summary))
                Resource.success(summary)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = if (errorBody?.contains("invalid_api_key") == true) {
                    "Invalid API key. Please check your Claude API key in Settings."
                } else {
                    "Failed to generate summary: ${response.message()}"
                }
                Resource.error(errorMessage)
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
