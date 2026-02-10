package com.personalcoacher.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalcoacher.util.DebugLogHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver that fires at the scheduled coach call time.
 * Shows an incoming call notification to the user.
 */
@AndroidEntryPoint
class CoachCallAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var notificationScheduler: NotificationScheduler

    @Inject
    lateinit var debugLog: DebugLogHelper

    override fun onReceive(context: Context, intent: Intent) {
        debugLog.log(TAG, "onReceive() - Coach call alarm triggered!")

        // Show the incoming call notification
        val result = notificationHelper.showIncomingCoachCallNotification()
        debugLog.log(TAG, "Notification result: $result")

        // Reschedule for tomorrow
        val hour = intent.getIntExtra(EXTRA_HOUR, 21)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        notificationScheduler.rescheduleCoachCall(hour, minute)
        debugLog.log(TAG, "Rescheduled coach call for tomorrow at $hour:$minute")
    }

    companion object {
        private const val TAG = "CoachCallAlarmReceiver"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
    }
}
