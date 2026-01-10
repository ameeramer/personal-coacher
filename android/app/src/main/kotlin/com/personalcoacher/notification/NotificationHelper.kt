package com.personalcoacher.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personalcoacher.MainActivity
import com.personalcoacher.R
import com.personalcoacher.util.DebugLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugLog: DebugLogHelper
) {
    companion object {
        const val CHANNEL_ID = "journal_reminder"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "NotificationHelper"
    }

    fun createNotificationChannel() {
        debugLog.log(TAG, "createNotificationChannel() called")
        val name = context.getString(R.string.notification_channel_name)
        val description = context.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Notification channel '$CHANNEL_ID' created with importance=$importance")
    }

    fun showJournalReminderNotification(): String {
        debugLog.log(TAG, "showJournalReminderNotification() called")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            debugLog.log(TAG, "Android 13+ - POST_NOTIFICATIONS permission: ${if (permissionStatus == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                debugLog.log(TAG, "FAILED: No notification permission on Android 13+")
                return "FAILED: POST_NOTIFICATIONS permission not granted"
            }
        } else {
            debugLog.log(TAG, "Android <13 - No runtime permission needed")
        }

        // Check if notifications are enabled at system level
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            debugLog.log(TAG, "FAILED: System notifications are disabled for this app")
            return "FAILED: System notifications are disabled for this app"
        }
        debugLog.log(TAG, "System notifications enabled: true")

        // Check channel status
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (channel == null) {
            debugLog.log(TAG, "WARNING: Notification channel '$CHANNEL_ID' does not exist, creating it")
            createNotificationChannel()
        } else {
            debugLog.log(TAG, "Channel '$CHANNEL_ID' exists, importance=${channel.importance}, blocked=${channel.importance == NotificationManager.IMPORTANCE_NONE}")
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                debugLog.log(TAG, "FAILED: Notification channel is blocked by user")
                return "FAILED: Notification channel '$CHANNEL_ID' is blocked by user"
            }
        }

        // Create intent to open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        debugLog.log(TAG, "Calling NotificationManagerCompat.notify() with ID=$NOTIFICATION_ID")
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            debugLog.log(TAG, "SUCCESS: Notification posted successfully")
            return "SUCCESS: Notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    fun hasNotificationPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        debugLog.log(TAG, "hasNotificationPermission() = $hasPermission")
        return hasPermission
    }
}
