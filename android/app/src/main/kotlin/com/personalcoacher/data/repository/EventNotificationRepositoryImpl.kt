package com.personalcoacher.data.repository

import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.EventNotificationDao
import com.personalcoacher.data.local.entity.EventNotificationEntity
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.domain.model.EventNotification
import com.personalcoacher.domain.model.EventNotificationAnalysis
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.EventNotificationRepository
import com.personalcoacher.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventNotificationRepositoryImpl @Inject constructor(
    private val eventNotificationDao: EventNotificationDao,
    private val claudeApiService: ClaudeApiService,
    private val tokenManager: TokenManager
) : EventNotificationRepository {

    override fun getNotificationForAgendaItem(agendaItemId: String): Flow<EventNotification?> {
        return eventNotificationDao.getNotificationForAgendaItem(agendaItemId)
            .map { it?.toDomainModel() }
    }

    override suspend fun getPendingNotifications(userId: String): List<EventNotification> {
        val currentTimeMs = System.currentTimeMillis()
        val beforeNotifications = eventNotificationDao.getPendingBeforeNotifications(userId, currentTimeMs)
        val afterNotifications = eventNotificationDao.getPendingAfterNotifications(userId, currentTimeMs)
        return (beforeNotifications + afterNotifications).map { it.toDomainModel() }
    }

    override suspend fun analyzeAgendaItem(
        agendaItemId: String,
        title: String,
        description: String?,
        startTime: Long,
        endTime: Long?,
        isAllDay: Boolean,
        location: String?
    ): Result<EventNotificationAnalysis> {
        val apiKey = tokenManager.getClaudeApiKey()
        if (apiKey.isNullOrBlank()) {
            return Result.Error("No Claude API key configured")
        }

        val systemPrompt = """You are an intelligent personal coach analyzing calendar events to determine if the user would benefit from supportive notifications before or after the event.

Your task is to analyze an agenda/calendar item and decide:
1. Should we send a notification BEFORE this event? (e.g., "Good luck with your presentation!")
2. If yes, how long before? (in minutes)
3. If yes, what supportive message should we send?
4. Should we send a notification AFTER this event? (e.g., "How did the presentation go?")
5. If yes, how long after? (in minutes)
6. If yes, what follow-up message should we send?

Guidelines for when to notify:
- NOTIFY for significant personal/professional events: presentations, interviews, important meetings, exams, doctor appointments, first dates, performances, competitions
- NOTIFY for emotionally significant events: difficult conversations, medical procedures, milestone celebrations
- DON'T notify for routine events: regular team standups, grocery shopping, commuting, lunch breaks, routine check-ins
- DON'T notify for all-day background events unless they're truly significant (birthday, wedding anniversary)

Timing guidelines:
- Before notifications: Usually 15-60 minutes before. For very important events, maybe 2-4 hours before.
- After notifications: Usually 30-120 minutes after, giving time to decompress. For events ending late, next morning is fine.

Message guidelines:
- Keep messages short (under 100 characters for mobile notifications)
- Be warm and supportive, not intrusive
- Before: Offer encouragement, express confidence in them
- After: Express interest in how it went, invite them to share/reflect
- Don't be generic - reference the specific event

You MUST respond with valid JSON in this exact format:
{
  "shouldNotifyBefore": boolean,
  "minutesBefore": number or null,
  "beforeMessage": "string" or null,
  "shouldNotifyAfter": boolean,
  "minutesAfter": number or null,
  "afterMessage": "string" or null,
  "reasoning": "Brief explanation of your decision"
}"""

        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)
        val startStr = dateFormat.format(Date(startTime))

        val prompt = buildString {
            appendLine("## Event Details")
            appendLine("Title: $title")
            description?.let { appendLine("Description: $it") }
            appendLine("Start: $startStr")
            endTime?.let {
                val endStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(it))
                appendLine("End: $endStr")
            }
            if (isAllDay) {
                appendLine("This is an all-day event.")
            }
            location?.let { appendLine("Location: $it") }
            appendLine()
            appendLine("Analyze this event and determine if supportive notifications would be helpful for the user.")
        }

        return try {
            val request = ClaudeMessageRequest(
                maxTokens = 512,
                system = systemPrompt,
                messages = listOf(ClaudeMessage(role = "user", content = prompt))
            )

            val response = claudeApiService.sendMessage(apiKey, request = request)
            if (response.isSuccessful) {
                response.body()?.content?.firstOrNull()?.text?.let { text ->
                    // Parse JSON response
                    val cleanedText = text.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val json = JSONObject(cleanedText)

                    val analysis = EventNotificationAnalysis(
                        shouldNotifyBefore = json.optBoolean("shouldNotifyBefore", false),
                        minutesBefore = if (json.has("minutesBefore") && !json.isNull("minutesBefore")) {
                            json.getInt("minutesBefore")
                        } else null,
                        beforeMessage = json.optString("beforeMessage", null).takeIf { it?.isNotBlank() == true },
                        shouldNotifyAfter = json.optBoolean("shouldNotifyAfter", false),
                        minutesAfter = if (json.has("minutesAfter") && !json.isNull("minutesAfter")) {
                            json.getInt("minutesAfter")
                        } else null,
                        afterMessage = json.optString("afterMessage", null).takeIf { it?.isNotBlank() == true },
                        reasoning = json.optString("reasoning", "")
                    )
                    Result.Success(analysis)
                } ?: Result.Error("Empty response from Claude")
            } else {
                Result.Error("Claude API error: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Failed to analyze event: ${e.message}")
        }
    }

    override suspend fun saveNotificationSettings(
        agendaItemId: String,
        userId: String,
        notifyBefore: Boolean,
        minutesBefore: Int?,
        beforeMessage: String?,
        notifyAfter: Boolean,
        minutesAfter: Int?,
        afterMessage: String?,
        aiDetermined: Boolean,
        aiReasoning: String?
    ): Result<EventNotification> {
        return try {
            val existingNotification = eventNotificationDao.getNotificationForAgendaItemSync(agendaItemId)
            val now = System.currentTimeMillis()

            val entity = if (existingNotification != null) {
                existingNotification.copy(
                    notifyBefore = notifyBefore,
                    minutesBefore = minutesBefore,
                    beforeMessage = beforeMessage,
                    notifyAfter = notifyAfter,
                    minutesAfter = minutesAfter,
                    afterMessage = afterMessage,
                    aiDetermined = aiDetermined,
                    aiReasoning = aiReasoning,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY.name
                )
            } else {
                EventNotificationEntity(
                    id = UUID.randomUUID().toString(),
                    agendaItemId = agendaItemId,
                    userId = userId,
                    notifyBefore = notifyBefore,
                    minutesBefore = minutesBefore,
                    beforeMessage = beforeMessage,
                    beforeNotificationSent = false,
                    beforeSentAt = null,
                    notifyAfter = notifyAfter,
                    minutesAfter = minutesAfter,
                    afterMessage = afterMessage,
                    afterNotificationSent = false,
                    afterSentAt = null,
                    aiDetermined = aiDetermined,
                    aiReasoning = aiReasoning,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY.name
                )
            }

            eventNotificationDao.insertNotification(entity)
            Result.Success(entity.toDomainModel())
        } catch (e: Exception) {
            Result.Error("Failed to save notification settings: ${e.message}")
        }
    }

    override suspend fun updateNotificationSettings(
        agendaItemId: String,
        notifyBefore: Boolean?,
        minutesBefore: Int?,
        notifyAfter: Boolean?,
        minutesAfter: Int?
    ): Result<EventNotification> {
        return try {
            val existing = eventNotificationDao.getNotificationForAgendaItemSync(agendaItemId)
                ?: return Result.Error("Notification settings not found")

            val updated = existing.copy(
                notifyBefore = notifyBefore ?: existing.notifyBefore,
                minutesBefore = minutesBefore ?: existing.minutesBefore,
                notifyAfter = notifyAfter ?: existing.notifyAfter,
                minutesAfter = minutesAfter ?: existing.minutesAfter,
                aiDetermined = false, // Manual update overrides AI
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.LOCAL_ONLY.name
            )

            eventNotificationDao.updateNotification(updated)
            Result.Success(updated.toDomainModel())
        } catch (e: Exception) {
            Result.Error("Failed to update notification settings: ${e.message}")
        }
    }

    override suspend fun deleteNotificationSettings(agendaItemId: String): Result<Unit> {
        return try {
            eventNotificationDao.deleteNotificationForAgendaItem(agendaItemId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete notification settings: ${e.message}")
        }
    }

    override suspend fun markBeforeNotificationSent(notificationId: String) {
        eventNotificationDao.markBeforeNotificationSent(notificationId, System.currentTimeMillis())
    }

    override suspend fun markAfterNotificationSent(notificationId: String) {
        eventNotificationDao.markAfterNotificationSent(notificationId, System.currentTimeMillis())
    }
}
