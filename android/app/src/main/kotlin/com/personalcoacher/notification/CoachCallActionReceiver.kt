package com.personalcoacher.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles actions from the incoming coach call notification (e.g., decline).
 */
@AndroidEntryPoint
class CoachCallActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> {
                notificationHelper.dismissCoachCallNotification()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.personalcoacher.ACTION_DECLINE_COACH_CALL"
    }
}
