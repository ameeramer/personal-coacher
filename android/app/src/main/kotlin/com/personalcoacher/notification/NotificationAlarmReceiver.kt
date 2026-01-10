package com.personalcoacher.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.personalcoacher.util.DebugLogHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver that triggers dynamic notifications when an alarm fires.
 * This provides more precise timing than WorkManager alone.
 */
@AndroidEntryPoint
class NotificationAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var debugLog: DebugLogHelper

    override fun onReceive(context: Context, intent: Intent) {
        debugLog.log(TAG, "onReceive() - Alarm triggered!")

        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        val rescheduleDaily = intent.getBooleanExtra(EXTRA_RESCHEDULE_DAILY, false)
        val rescheduleInterval = intent.getBooleanExtra(EXTRA_RESCHEDULE_INTERVAL, false)
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val intervalValue = intent.getIntExtra(EXTRA_INTERVAL_VALUE, 0)
        val intervalUnit = intent.getStringExtra(EXTRA_INTERVAL_UNIT) ?: ""

        debugLog.log(TAG, "Alarm data - ruleId: $ruleId, rescheduleDaily: $rescheduleDaily, " +
                "rescheduleInterval: $rescheduleInterval, time: $hour:$minute, " +
                "interval: $intervalValue $intervalUnit")

        // Trigger the notification worker immediately
        val workRequest = OneTimeWorkRequestBuilder<DynamicNotificationWorker>()
            .setInputData(
                workDataOf(
                    "rule_id" to ruleId,
                    "reschedule_daily" to rescheduleDaily,
                    "reschedule_interval" to rescheduleInterval,
                    "hour" to hour,
                    "minute" to minute,
                    "interval_value" to intervalValue,
                    "interval_unit" to intervalUnit,
                    "triggered_by_alarm" to true
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        debugLog.log(TAG, "Enqueued immediate notification worker")
    }

    companion object {
        private const val TAG = "NotificationAlarmReceiver"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_RESCHEDULE_DAILY = "reschedule_daily"
        const val EXTRA_RESCHEDULE_INTERVAL = "reschedule_interval"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_INTERVAL_VALUE = "interval_value"
        const val EXTRA_INTERVAL_UNIT = "interval_unit"
    }
}
