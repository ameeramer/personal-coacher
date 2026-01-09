package com.personalcoacher.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TARGET_HOUR = 22
        private const val TARGET_MINUTE = 15
    }

    fun scheduleJournalReminder() {
        val initialDelay = calculateInitialDelay()

        val workRequest = PeriodicWorkRequestBuilder<JournalReminderWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            JournalReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelJournalReminder() {
        WorkManager.getInstance(context).cancelUniqueWork(JournalReminderWorker.WORK_NAME)
    }

    fun isReminderScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(JournalReminderWorker.WORK_NAME)
            .get()

        return workInfos.any { !it.state.isFinished }
    }

    private fun calculateInitialDelay(): Long {
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, TARGET_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time has passed today, schedule for tomorrow
        if (targetTime.before(currentTime) || targetTime == currentTime) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1)
        }

        return targetTime.timeInMillis - currentTime.timeInMillis
    }
}
