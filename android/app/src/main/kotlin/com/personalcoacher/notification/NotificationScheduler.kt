package com.personalcoacher.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.util.DebugLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper
) {
    companion object {
        private const val TAG = "NotificationScheduler"
    }

    fun scheduleJournalReminder() {
        val hour = tokenManager.getReminderHourSync()
        val minute = tokenManager.getReminderMinuteSync()
        debugLog.log(TAG, "scheduleJournalReminder() called - using stored time $hour:$minute")
        scheduleJournalReminder(hour, minute)
    }

    fun scheduleJournalReminder(hour: Int, minute: Int) {
        debugLog.log(TAG, "scheduleJournalReminder($hour, $minute) called")
        val initialDelay = calculateInitialDelay(hour, minute)
        val delayHours = initialDelay / (1000 * 60 * 60)
        val delayMinutes = (initialDelay / (1000 * 60)) % 60
        debugLog.log(TAG, "Initial delay: ${delayHours}h ${delayMinutes}m (${initialDelay}ms)")

        val workRequest = PeriodicWorkRequestBuilder<JournalReminderWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        debugLog.log(TAG, "Created PeriodicWorkRequest with ID=${workRequest.id}")
        debugLog.log(TAG, "Enqueuing work with UPDATE policy for '${JournalReminderWorker.WORK_NAME}'")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            JournalReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        debugLog.log(TAG, "Work enqueued successfully")
    }

    fun cancelJournalReminder() {
        debugLog.log(TAG, "cancelJournalReminder() called")
        WorkManager.getInstance(context).cancelUniqueWork(JournalReminderWorker.WORK_NAME)
        debugLog.log(TAG, "Work cancelled for '${JournalReminderWorker.WORK_NAME}'")
    }

    fun isReminderScheduled(): Boolean {
        debugLog.log(TAG, "isReminderScheduled() called")
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(JournalReminderWorker.WORK_NAME)
            .get()

        val result = workInfos.any { !it.state.isFinished }
        debugLog.log(TAG, "isReminderScheduled() = $result (workInfos count: ${workInfos.size})")
        return result
    }

    fun getScheduledWorkInfo(): String {
        debugLog.log(TAG, "getScheduledWorkInfo() called")
        return try {
            val journalWorkInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(JournalReminderWorker.WORK_NAME)
                .get()

            val dynamicWorkInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(DynamicNotificationWorker.WORK_NAME)
                .get()

            val result = StringBuilder()

            result.appendLine("=== Journal Reminder ===")
            if (journalWorkInfos.isEmpty()) {
                result.appendLine("No scheduled work found")
            } else {
                journalWorkInfos.forEach { info ->
                    result.appendLine("Work ID: ${info.id}")
                    result.appendLine("State: ${info.state}")
                    result.appendLine("Run Attempt: ${info.runAttemptCount}")
                }
            }

            result.appendLine()
            result.appendLine("=== Dynamic Notifications ===")
            if (dynamicWorkInfos.isEmpty()) {
                result.appendLine("No scheduled work found")
            } else {
                dynamicWorkInfos.forEach { info ->
                    result.appendLine("Work ID: ${info.id}")
                    result.appendLine("State: ${info.state}")
                    result.appendLine("Run Attempt: ${info.runAttemptCount}")
                }
            }

            result.toString()
        } catch (e: Exception) {
            "Error getting work info: ${e.message}"
        }
    }

    // Dynamic notification scheduling (multiple times per day)
    fun scheduleDynamicNotifications() {
        debugLog.log(TAG, "scheduleDynamicNotifications() called")
        // Schedule dynamic notifications every 6 hours
        // WorkManager will run the DynamicNotificationWorker periodically
        val workRequest = PeriodicWorkRequestBuilder<DynamicNotificationWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .build()

        debugLog.log(TAG, "Created PeriodicWorkRequest for dynamic notifications with ID=${workRequest.id}")
        debugLog.log(TAG, "Enqueuing work with UPDATE policy for '${DynamicNotificationWorker.WORK_NAME}'")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DynamicNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        debugLog.log(TAG, "Dynamic notification work enqueued successfully")
    }

    fun cancelDynamicNotifications() {
        debugLog.log(TAG, "cancelDynamicNotifications() called")
        WorkManager.getInstance(context).cancelUniqueWork(DynamicNotificationWorker.WORK_NAME)
        debugLog.log(TAG, "Work cancelled for '${DynamicNotificationWorker.WORK_NAME}'")
    }

    fun isDynamicNotificationsScheduled(): Boolean {
        debugLog.log(TAG, "isDynamicNotificationsScheduled() called")
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DynamicNotificationWorker.WORK_NAME)
            .get()

        val result = workInfos.any { !it.state.isFinished }
        debugLog.log(TAG, "isDynamicNotificationsScheduled() = $result (workInfos count: ${workInfos.size})")
        return result
    }

    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        debugLog.log(TAG, "calculateInitialDelay: current=${formatCalendar(currentTime)}, target=${formatCalendar(targetTime)}")

        // If target time has passed today, schedule for tomorrow
        if (targetTime.before(currentTime) || targetTime.timeInMillis == currentTime.timeInMillis) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1)
            debugLog.log(TAG, "Target time passed, scheduling for tomorrow: ${formatCalendar(targetTime)}")
        }

        return targetTime.timeInMillis - currentTime.timeInMillis
    }

    private fun formatCalendar(cal: Calendar): String {
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d %02d:%02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}
