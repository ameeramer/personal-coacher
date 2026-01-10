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

        // Create dynamic coach notification channel
        createDynamicNotificationChannel()
    }

    private fun createDynamicNotificationChannel() {
        debugLog.log(TAG, "createDynamicNotificationChannel() called")
        val name = context.getString(R.string.dynamic_notification_channel_name)
        val description = context.getString(R.string.dynamic_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(DYNAMIC_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Dynamic notification channel '$DYNAMIC_CHANNEL_ID' created with importance=$importance")
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

    fun showDynamicNotification(title: String, body: String, topicReference: String? = null): String {
        debugLog.log(TAG, "showDynamicNotification() called - title='$title', body='$body', topic='$topicReference'")

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

        // Check/create dynamic channel
        val channel = notificationManager.getNotificationChannel(DYNAMIC_CHANNEL_ID)
        if (channel == null) {
            debugLog.log(TAG, "WARNING: Dynamic notification channel '$DYNAMIC_CHANNEL_ID' does not exist, creating it")
            createDynamicNotificationChannel()
        } else {
            debugLog.log(TAG, "Dynamic channel '$DYNAMIC_CHANNEL_ID' exists, importance=${channel.importance}, blocked=${channel.importance == NotificationManager.IMPORTANCE_NONE}")
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                debugLog.log(TAG, "FAILED: Dynamic notification channel is blocked by user")
                return "FAILED: Dynamic notification channel '$DYNAMIC_CHANNEL_ID' is blocked by user"
            }
        }

        // Create intent to open app and start a coach conversation with the notification message
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_COACH)
            putExtra(EXTRA_COACH_MESSAGE, body)
            putExtra(EXTRA_COACH_TITLE, title)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // Unique request code for each notification
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DYNAMIC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        debugLog.log(TAG, "Calling NotificationManagerCompat.notify() with ID=$DYNAMIC_NOTIFICATION_ID")
        try {
            NotificationManagerCompat.from(context).notify(DYNAMIC_NOTIFICATION_ID, notification)
            debugLog.log(TAG, "SUCCESS: Dynamic notification posted successfully")
            return "SUCCESS: Dynamic notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    companion object {
        const val CHANNEL_ID = "journal_reminder"
        const val DYNAMIC_CHANNEL_ID = "dynamic_coach"
        const val NOTIFICATION_ID = 1001
        const val DYNAMIC_NOTIFICATION_ID = 1002
        private const val TAG = "NotificationHelper"

        // Intent extras for deep linking
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val EXTRA_COACH_MESSAGE = "coach_message"
        const val EXTRA_COACH_TITLE = "coach_title"
        const val NAVIGATE_TO_COACH = "coach"
    }
}
