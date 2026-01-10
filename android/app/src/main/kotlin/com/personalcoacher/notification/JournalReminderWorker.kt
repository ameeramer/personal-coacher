package com.personalcoacher.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.repository.DynamicNotificationRepository
import com.personalcoacher.util.DebugLogHelper
import com.personalcoacher.util.onError
import com.personalcoacher.util.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class JournalReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val tokenManager: TokenManager,
    private val dynamicNotificationRepository: DynamicNotificationRepository,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        debugLog.log(TAG, "doWork() called - Worker executing")

        // Check if dynamic AI notifications are enabled and API key is configured
        val dynamicEnabled = tokenManager.getDynamicNotificationsEnabledSync()
        val hasApiKey = tokenManager.hasClaudeApiKey()
        val userId = tokenManager.getUserId()

        debugLog.log(TAG, "Dynamic notifications enabled: $dynamicEnabled, has API key: $hasApiKey, userId: $userId")

        // If AI Coach check-ins are enabled and we have the API key, show dynamic notification
        if (dynamicEnabled && hasApiKey && !userId.isNullOrBlank()) {
            debugLog.log(TAG, "Using dynamic notification instead of static")
            return try {
                val result = dynamicNotificationRepository.generateAndShowDynamicNotification(userId)

                result.onSuccess { notification ->
                    debugLog.log(TAG, "Generated dynamic notification: ${notification.title}")
                    val notifResult = notificationHelper.showDynamicNotification(
                        title = notification.title,
                        body = notification.body,
                        topicReference = notification.topicReference
                    )
                    debugLog.log(TAG, "Show notification result: $notifResult")
                }.onError { errorMessage ->
                    debugLog.log(TAG, "Failed to generate dynamic notification: $errorMessage")
                    // Fall back to static notification
                    debugLog.log(TAG, "Falling back to static notification")
                    val staticResult = notificationHelper.showJournalReminderNotification()
                    debugLog.log(TAG, "Static notification result: $staticResult")
                }

                debugLog.log(TAG, "doWork() returning Result.success()")
                Result.success()
            } catch (e: Exception) {
                debugLog.log(TAG, "doWork() EXCEPTION during dynamic notification: ${e.message}")
                // Fall back to static notification on error
                try {
                    notificationHelper.showJournalReminderNotification()
                } catch (fallbackError: Exception) {
                    debugLog.log(TAG, "Even fallback notification failed: ${fallbackError.message}")
                }
                Result.success()
            }
        }

        // Show static notification if dynamic is not enabled
        return try {
            val result = notificationHelper.showJournalReminderNotification()
            debugLog.log(TAG, "Notification result: $result")
            debugLog.log(TAG, "doWork() returning Result.success()")
            Result.success()
        } catch (e: Exception) {
            debugLog.log(TAG, "doWork() EXCEPTION: ${e.message}")
            debugLog.log(TAG, "doWork() returning Result.failure()")
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "journal_reminder_work"
        private const val TAG = "JournalReminderWorker"
    }
}
