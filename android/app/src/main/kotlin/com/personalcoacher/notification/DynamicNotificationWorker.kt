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
    private val notificationScheduler: NotificationScheduler,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        debugLog.log(TAG, "doWork() called - Dynamic notification worker executing")

        // Get reschedule parameters from input data
        val ruleId = inputData.getString("rule_id")
        val rescheduleDaily = inputData.getBoolean("reschedule_daily", false)
        val rescheduleInterval = inputData.getBoolean("reschedule_interval", false)
        val hour = inputData.getInt("hour", 0)
        val minute = inputData.getInt("minute", 0)
        val intervalValue = inputData.getInt("interval_value", 0)
        val intervalUnit = inputData.getString("interval_unit") ?: ""

        debugLog.log(TAG, "Input data - ruleId: $ruleId, rescheduleDaily: $rescheduleDaily, rescheduleInterval: $rescheduleInterval, time: $hour:$minute, interval: $intervalValue $intervalUnit")

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

            // Reschedule for next occurrence based on rule type
            rescheduleNextOccurrence(ruleId, rescheduleDaily, rescheduleInterval, hour, minute, intervalValue, intervalUnit)

            debugLog.log(TAG, "doWork() returning Result.success()")
            Result.success()
        } catch (e: Exception) {
            debugLog.log(TAG, "doWork() EXCEPTION: ${e.message}")
            // Don't fall back to static notification - just log the error
            // But still reschedule for next occurrence
            rescheduleNextOccurrence(ruleId, rescheduleDaily, rescheduleInterval, hour, minute, intervalValue, intervalUnit)
            debugLog.log(TAG, "doWork() returning Result.success() after error")
            Result.success() // Still return success to not retry infinitely
        }
    }

    private fun rescheduleNextOccurrence(
        ruleId: String?,
        rescheduleDaily: Boolean,
        rescheduleInterval: Boolean,
        hour: Int,
        minute: Int,
        intervalValue: Int,
        intervalUnit: String
    ) {
        if (ruleId == null) return

        when {
            rescheduleDaily -> {
                debugLog.log(TAG, "Rescheduling daily notification for rule: $ruleId at $hour:$minute")
                notificationScheduler.rescheduleDailyNotification(ruleId, hour, minute)
            }
            rescheduleInterval && intervalValue > 0 -> {
                debugLog.log(TAG, "Rescheduling interval notification for rule: $ruleId every $intervalValue $intervalUnit")
                notificationScheduler.rescheduleIntervalNotification(ruleId, intervalValue, intervalUnit)
            }
        }
    }

    companion object {
        const val WORK_NAME = "dynamic_notification_work"
        private const val TAG = "DynamicNotificationWorker"
    }
}
