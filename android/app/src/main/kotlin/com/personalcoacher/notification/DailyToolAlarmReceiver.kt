package com.personalcoacher.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver that triggers daily tool generation when an alarm fires.
 * This provides precise timing using AlarmManager instead of relying on WorkManager's
 * inexact periodic scheduling.
 */
@AndroidEntryPoint
class DailyToolAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() - Daily tool alarm triggered!")

        val hour = intent.getIntExtra(EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

        Log.d(TAG, "Alarm data - scheduled time: $hour:$minute")

        // Show "generation initialized" notification
        notificationHelper.showDailyToolReadyNotification(
            title = context.getString(com.personalcoacher.R.string.daily_tool_generation_started_title),
            body = context.getString(com.personalcoacher.R.string.daily_tool_generation_started_body)
        )

        // Trigger the generation worker immediately
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putBoolean(DailyAppGenerationWorker.KEY_FORCE_REGENERATE, false)
            .putBoolean(DailyAppGenerationWorker.KEY_SHOW_NOTIFICATION, true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DailyAppGenerationWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_ALARM_TRIGGERED,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Enqueued daily tool generation worker")

        // Schedule the next alarm for tomorrow
        DailyAppGenerationWorker.scheduleExactAlarm(context, hour, minute)
        Log.d(TAG, "Scheduled next alarm for tomorrow at $hour:$minute")
    }

    companion object {
        private const val TAG = "DailyToolAlarmReceiver"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val WORK_NAME_ALARM_TRIGGERED = "daily_app_generation_alarm_triggered"
    }
}
