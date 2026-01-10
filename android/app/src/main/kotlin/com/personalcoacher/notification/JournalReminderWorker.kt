package com.personalcoacher.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.util.DebugLogHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class JournalReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        debugLog.log(TAG, "doWork() called - Journal Reminder Worker executing")

        // Check if daily reminders are still enabled
        val notificationsEnabled = tokenManager.getNotificationsEnabledSync()
        if (!notificationsEnabled) {
            debugLog.log(TAG, "Daily reminders are disabled, skipping")
            return Result.success()
        }

        // If AI Coach check-ins are enabled, don't show static reminder (they are mutually exclusive)
        val dynamicEnabled = tokenManager.getDynamicNotificationsEnabledSync()
        if (dynamicEnabled) {
            debugLog.log(TAG, "AI Coach check-ins are enabled, skipping static reminder")
            return Result.success()
        }

        // Show static notification
        return try {
            val result = notificationHelper.showJournalReminderNotification()
            debugLog.log(TAG, "Static notification result: $result")
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
