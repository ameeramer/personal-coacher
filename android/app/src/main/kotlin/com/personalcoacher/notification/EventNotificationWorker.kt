package com.personalcoacher.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.EventNotificationDao
import com.personalcoacher.data.local.dao.SentNotificationDao
import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.SentNotificationEntity
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.util.DebugLogHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Worker that checks for and sends event-based notifications.
 * This worker is scheduled to run periodically (e.g., every 5 minutes)
 * to check if any event notifications need to be sent based on agenda items.
 */
@HiltWorker
class EventNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val eventNotificationDao: EventNotificationDao,
    private val agendaItemDao: AgendaItemDao,
    private val sentNotificationDao: SentNotificationDao,
    private val notificationHelper: NotificationHelper,
    private val tokenManager: TokenManager,
    private val claudeApiService: ClaudeApiService,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Pre-flight check to verify DNS resolution is working.
     */
    @androidx.annotation.WorkerThread
    private fun isDnsResolutionWorking(): Boolean {
        return try {
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Boolean> {
                try {
                    val addresses = InetAddress.getAllByName("api.anthropic.com")
                    addresses.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
            future.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            debugLog.log(TAG, "DNS pre-flight check timed out after ${DNS_TIMEOUT_MS}ms")
            false
        } catch (e: Exception) {
            debugLog.log(TAG, "DNS pre-flight check failed: ${e.message}")
            false
        }
    }

    /**
     * Generate a notification message using Claude API.
     */
    private suspend fun generateEventMessage(
        agendaItem: AgendaItemEntity,
        type: String // "before" or "after"
    ): Pair<String, String>? {
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            debugLog.log(TAG, "No Claude API key configured")
            return null
        }

        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)
        val startTimeStr = dateFormat.format(Date(agendaItem.startTime))

        val systemPrompt = """You are a supportive personal coach generating a brief notification message for an upcoming or completed calendar event.

Guidelines:
- Keep messages under 100 characters (mobile notification limit)
- Be warm and supportive
- Reference the specific event
- For BEFORE: Offer encouragement or express confidence
- For AFTER: Express interest in how it went, invite reflection

You MUST respond with valid JSON:
{
  "title": "Brief title (max 50 chars)",
  "body": "The notification message (max 100 chars)"
}"""

        val prompt = buildString {
            appendLine("## Event")
            appendLine("Title: ${agendaItem.title}")
            agendaItem.description?.let { appendLine("Description: $it") }
            appendLine("Time: $startTimeStr")
            agendaItem.location?.let { appendLine("Location: $it") }
            appendLine()
            if (type == "before") {
                appendLine("Generate an encouraging notification to send BEFORE this event starts.")
            } else {
                appendLine("Generate a caring follow-up notification to send AFTER this event has ended.")
            }
        }

        return try {
            val request = ClaudeMessageRequest(
                maxTokens = 256,
                system = systemPrompt,
                messages = listOf(ClaudeMessage(role = "user", content = prompt))
            )

            val response = claudeApiService.sendMessage(apiKey!!, request = request)
            if (response.isSuccessful) {
                response.body()?.content?.firstOrNull()?.text?.let { text ->
                    // Parse JSON response
                    val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
                    val title = json.getString("title")
                    val body = json.getString("body")
                    Pair(title, body)
                }
            } else {
                debugLog.log(TAG, "Claude API error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            debugLog.log(TAG, "Error generating message: ${e.message}")
            null
        }
    }

    override suspend fun doWork(): Result {
        debugLog.log(TAG, "doWork() called - Event notification worker executing")

        val userId = tokenManager.getUserId()
        if (userId.isNullOrBlank()) {
            debugLog.log(TAG, "No user logged in, skipping event notifications")
            return Result.success()
        }

        val currentTimeMs = System.currentTimeMillis()

        return try {
            // Process pending "before" notifications
            val pendingBefore = eventNotificationDao.getPendingBeforeNotifications(userId, currentTimeMs)
            debugLog.log(TAG, "Found ${pendingBefore.size} pending before notifications")

            for (notification in pendingBefore) {
                try {
                    val agendaItem = agendaItemDao.getItemByIdSync(notification.agendaItemId)
                    if (agendaItem == null) {
                        debugLog.log(TAG, "Agenda item not found for notification ${notification.id}")
                        continue
                    }

                    // Get or generate the message
                    var title = "Event Coming Up"
                    var body = notification.beforeMessage ?: "Your \"${agendaItem.title}\" is starting soon. Good luck!"

                    // If no message is configured and we have network, try to generate one
                    if (notification.beforeMessage == null && isDnsResolutionWorking() && tokenManager.hasClaudeApiKey()) {
                        val generated = generateEventMessage(agendaItem, "before")
                        if (generated != null) {
                            title = generated.first
                            body = generated.second
                            // Update the stored message
                            eventNotificationDao.updateNotification(
                                notification.copy(beforeMessage = body)
                            )
                        }
                    }

                    // Show the notification
                    val result = notificationHelper.showEventNotification(
                        title = title,
                        body = body,
                        tag = "event-before-${notification.agendaItemId}"
                    )
                    debugLog.log(TAG, "Show before notification result: $result")

                    if (result.startsWith("SUCCESS")) {
                        // Mark as sent
                        eventNotificationDao.markBeforeNotificationSent(notification.id, currentTimeMs)

                        // Save to sent notifications for history
                        val timeOfDay = TimeOfDay.fromHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                        val sentNotification = SentNotificationEntity.create(
                            userId = userId,
                            title = title,
                            body = body,
                            topicReference = "Event: ${agendaItem.title}",
                            timeOfDay = timeOfDay.value
                        )
                        sentNotificationDao.insertNotification(sentNotification)
                        debugLog.log(TAG, "Before notification sent and saved for ${agendaItem.title}")
                    }
                } catch (e: Exception) {
                    debugLog.log(TAG, "Error processing before notification ${notification.id}: ${e.message}")
                }
            }

            // Process pending "after" notifications
            val pendingAfter = eventNotificationDao.getPendingAfterNotifications(userId, currentTimeMs)
            debugLog.log(TAG, "Found ${pendingAfter.size} pending after notifications")

            for (notification in pendingAfter) {
                try {
                    val agendaItem = agendaItemDao.getItemByIdSync(notification.agendaItemId)
                    if (agendaItem == null) {
                        debugLog.log(TAG, "Agenda item not found for notification ${notification.id}")
                        continue
                    }

                    // Get or generate the message
                    var title = "Event Follow-up"
                    var body = notification.afterMessage ?: "How did \"${agendaItem.title}\" go? Feel free to reflect on it."

                    // If no message is configured and we have network, try to generate one
                    if (notification.afterMessage == null && isDnsResolutionWorking() && tokenManager.hasClaudeApiKey()) {
                        val generated = generateEventMessage(agendaItem, "after")
                        if (generated != null) {
                            title = generated.first
                            body = generated.second
                            // Update the stored message
                            eventNotificationDao.updateNotification(
                                notification.copy(afterMessage = body)
                            )
                        }
                    }

                    // Show the notification
                    val result = notificationHelper.showEventNotification(
                        title = title,
                        body = body,
                        tag = "event-after-${notification.agendaItemId}"
                    )
                    debugLog.log(TAG, "Show after notification result: $result")

                    if (result.startsWith("SUCCESS")) {
                        // Mark as sent
                        eventNotificationDao.markAfterNotificationSent(notification.id, currentTimeMs)

                        // Save to sent notifications for history
                        val timeOfDay = TimeOfDay.fromHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                        val sentNotification = SentNotificationEntity.create(
                            userId = userId,
                            title = title,
                            body = body,
                            topicReference = "Event follow-up: ${agendaItem.title}",
                            timeOfDay = timeOfDay.value
                        )
                        sentNotificationDao.insertNotification(sentNotification)
                        debugLog.log(TAG, "After notification sent and saved for ${agendaItem.title}")
                    }
                } catch (e: Exception) {
                    debugLog.log(TAG, "Error processing after notification ${notification.id}: ${e.message}")
                }
            }

            debugLog.log(TAG, "doWork() completed successfully")
            Result.success()
        } catch (e: Exception) {
            debugLog.log(TAG, "doWork() EXCEPTION: ${e.message}")
            Result.success() // Still return success to not retry infinitely
        }
    }

    companion object {
        const val WORK_NAME = "event_notification_work"
        private const val TAG = "EventNotificationWorker"
        private const val DNS_TIMEOUT_MS = 5000L
    }
}
