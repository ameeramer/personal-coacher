package com.personalcoacher.data.repository

import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.SentNotificationDao
import com.personalcoacher.data.local.entity.SentNotificationEntity
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.domain.repository.DynamicNotificationRepository
import com.personalcoacher.notification.NotificationPromptBuilder
import com.personalcoacher.notification.NotificationPromptBuilder.GeneratedNotification
import com.personalcoacher.notification.TimeOfDay
import com.personalcoacher.util.DebugLogHelper
import com.personalcoacher.util.Resource
import java.time.Instant
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicNotificationRepositoryImpl @Inject constructor(
    private val claudeApiService: ClaudeApiService,
    private val journalEntryDao: JournalEntryDao,
    private val sentNotificationDao: SentNotificationDao,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper
) : DynamicNotificationRepository {

    companion object {
        private const val TAG = "DynamicNotificationRepo"
        private const val DAYS_OF_ENTRIES_TO_FETCH = 7
        private const val DAYS_OF_NOTIFICATIONS_TO_FETCH = 3
    }

    override suspend fun generateAndShowDynamicNotification(userId: String): Resource<GeneratedNotification> {
        debugLog.log(TAG, "generateDynamicNotification() called for userId=$userId")

        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            debugLog.log(TAG, "No Claude API key configured")
            return Resource.error("No Claude API key configured")
        }

        val timeOfDay = getCurrentTimeOfDay()
        debugLog.log(TAG, "Current time of day: ${timeOfDay.value}")

        // Fetch recent journal entries
        val recentEntries = journalEntryDao.getRecentEntriesSync(userId, 5)
        debugLog.log(TAG, "Found ${recentEntries.size} recent journal entries")

        // Fetch recent sent notifications (last 3 days) to avoid repetition
        val threeDaysAgo = Instant.now().minusSeconds(DAYS_OF_NOTIFICATIONS_TO_FETCH * 24 * 60 * 60L)
        val recentNotifications = sentNotificationDao.getNotificationsSince(
            userId,
            threeDaysAgo.toEpochMilli()
        )
        debugLog.log(TAG, "Found ${recentNotifications.size} recent notifications to avoid")

        // Build the prompt
        val userPrompt = NotificationPromptBuilder.buildNotificationPrompt(
            recentEntries = recentEntries,
            recentNotifications = recentNotifications,
            timeOfDay = timeOfDay,
            userName = tokenManager.getUserEmail()?.substringBefore('@')
        )

        debugLog.log(TAG, "Built notification prompt, calling Claude API...")

        return try {
            val response = claudeApiService.sendMessage(
                apiKey = apiKey,
                request = ClaudeMessageRequest(
                    maxTokens = 256,
                    system = NotificationPromptBuilder.NOTIFICATION_SYSTEM_PROMPT,
                    messages = listOf(
                        ClaudeMessage(role = "user", content = userPrompt)
                    )
                )
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorBody = response.errorBody()?.string()
                debugLog.log(TAG, "Claude API error: ${response.code()} - $errorBody")
                return Resource.error("Failed to generate notification: ${response.code()}")
            }

            val textContent = response.body()!!.content.firstOrNull { it.type == "text" }
            if (textContent == null) {
                debugLog.log(TAG, "No text content in Claude response")
                return Resource.error("No text content in response")
            }

            debugLog.log(TAG, "Claude response: ${textContent.text}")

            val notification = NotificationPromptBuilder.parseNotificationResponse(textContent.text)
            if (notification == null) {
                debugLog.log(TAG, "Failed to parse notification JSON")
                return Resource.error("Failed to parse notification response")
            }

            debugLog.log(TAG, "Parsed notification: title='${notification.title}', body='${notification.body}'")

            // Save the sent notification to avoid repetition
            val sentNotification = SentNotificationEntity.create(
                userId = userId,
                title = notification.title,
                body = notification.body,
                topicReference = notification.topicReference,
                timeOfDay = timeOfDay.value
            )
            sentNotificationDao.insertNotification(sentNotification)
            debugLog.log(TAG, "Saved notification to database with id=${sentNotification.id}")

            Resource.success(notification)
        } catch (e: Exception) {
            debugLog.log(TAG, "Exception generating notification: ${e.message}")
            Resource.error("Failed to generate notification: ${e.localizedMessage}")
        }
    }

    private fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return TimeOfDay.fromHour(hour)
    }
}
