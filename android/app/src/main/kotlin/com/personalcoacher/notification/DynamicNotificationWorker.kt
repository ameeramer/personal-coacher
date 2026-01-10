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
class DynamicNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dynamicNotificationRepository: DynamicNotificationRepository,
    private val notificationHelper: NotificationHelper,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        debugLog.log(TAG, "doWork() called - Dynamic notification worker executing")

        val userId = tokenManager.getUserId()
        if (userId.isNullOrBlank()) {
            debugLog.log(TAG, "No user logged in, skipping dynamic notification")
            return Result.success() // Not an error, just nothing to do
        }

        // Check if dynamic notifications are still enabled
        if (!tokenManager.getDynamicNotificationsEnabledSync()) {
            debugLog.log(TAG, "Dynamic notifications are disabled, skipping")
            return Result.success()
        }

        // Check if Claude API key is configured
        if (!tokenManager.hasClaudeApiKey()) {
            debugLog.log(TAG, "No Claude API key configured, skipping dynamic notification")
            // Don't fall back to static - that's for daily reminder. Just skip silently.
            return Result.success()
        }

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
                // Don't fall back to static - dynamic worker should only show dynamic notifications
                // Static notifications are for the daily reminder worker only
            }

            debugLog.log(TAG, "doWork() returning Result.success()")
            Result.success()
        } catch (e: Exception) {
            debugLog.log(TAG, "doWork() EXCEPTION: ${e.message}")
            // Don't fall back to static notification - just log the error
            debugLog.log(TAG, "doWork() returning Result.success() after error")
            Result.success() // Still return success to not retry infinitely
        }
    }

    companion object {
        const val WORK_NAME = "dynamic_notification_work"
        private const val TAG = "DynamicNotificationWorker"
    }
}
