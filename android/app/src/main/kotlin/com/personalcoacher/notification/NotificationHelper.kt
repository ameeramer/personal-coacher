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

        // Create chat response notification channel (for when user leaves app mid-stream)
        createChatResponseNotificationChannel()

        // Create event suggestion notification channel
        createEventSuggestionNotificationChannel()

        // Create event notification channel (for before/after event reminders)
        createEventNotificationChannel()

        // Create daily tool notification channel
        createDailyToolNotificationChannel()

        // Create coach call notification channel
        createCoachCallNotificationChannel()
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

    /**
     * Check if the app can schedule exact alarms.
     * On Android 12 (API 31) and above, apps need the SCHEDULE_EXACT_ALARM permission
     * which users can disable in system settings.
     *
     * If this returns false, the app will fall back to inexact alarms which may
     * not deliver notifications at the precise scheduled time.
     */
    fun canScheduleExactAlarms(): Boolean {
        val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            // Before Android 12, exact alarms are always allowed
            true
        }
        debugLog.log(TAG, "canScheduleExactAlarms() = $canSchedule")
        return canSchedule
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

    /**
     * Shows a notification when the AI coach has replied to a message.
     * Used when the user sends a message and leaves the app while waiting for the response.
     */
    fun showChatResponseNotification(title: String, body: String, conversationId: String): String {
        debugLog.log(TAG, "showChatResponseNotification() called - conversationId='$conversationId'")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                debugLog.log(TAG, "FAILED: No notification permission on Android 13+")
                return "FAILED: POST_NOTIFICATIONS permission not granted"
            }
        }

        // Check if notifications are enabled at system level
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            debugLog.log(TAG, "FAILED: System notifications are disabled for this app")
            return "FAILED: System notifications are disabled for this app"
        }

        // Check/create chat response channel
        val channel = notificationManager.getNotificationChannel(CHAT_RESPONSE_CHANNEL_ID)
        if (channel == null) {
            debugLog.log(TAG, "Creating chat response notification channel")
            createChatResponseNotificationChannel()
        } else if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            debugLog.log(TAG, "FAILED: Chat response notification channel is blocked by user")
            return "FAILED: Chat response notification channel is blocked by user"
        }

        // Create intent to open the specific conversation
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_CONVERSATION)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(), // Unique request code per conversation
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHAT_RESPONSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        // Use conversation-specific notification ID so multiple conversations don't overwrite each other
        val notificationId = CHAT_RESPONSE_NOTIFICATION_ID_BASE + (conversationId.hashCode() and 0xFFFF)
        debugLog.log(TAG, "Posting chat response notification with ID=$notificationId")

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            debugLog.log(TAG, "SUCCESS: Chat response notification posted")
            return "SUCCESS: Chat response notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    private fun createChatResponseNotificationChannel() {
        val name = context.getString(R.string.chat_response_channel_name)
        val description = context.getString(R.string.chat_response_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHAT_RESPONSE_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Chat response notification channel '$CHAT_RESPONSE_CHANNEL_ID' created")
    }

    private fun createEventSuggestionNotificationChannel() {
        val name = context.getString(R.string.event_suggestion_channel_name)
        val description = context.getString(R.string.event_suggestion_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(EVENT_SUGGESTION_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Event suggestion notification channel '$EVENT_SUGGESTION_CHANNEL_ID' created")
    }

    /**
     * Shows a notification when AI has detected potential calendar events from a journal entry.
     * Used when the user saves a journal entry and leaves the app while the AI analyzes it.
     */
    fun showEventSuggestionNotification(title: String, body: String, suggestionCount: Int): String {
        debugLog.log(TAG, "showEventSuggestionNotification() called - count=$suggestionCount")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                debugLog.log(TAG, "FAILED: No notification permission on Android 13+")
                return "FAILED: POST_NOTIFICATIONS permission not granted"
            }
        }

        // Check if notifications are enabled at system level
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            debugLog.log(TAG, "FAILED: System notifications are disabled for this app")
            return "FAILED: System notifications are disabled for this app"
        }

        // Check/create event suggestion channel
        val channel = notificationManager.getNotificationChannel(EVENT_SUGGESTION_CHANNEL_ID)
        if (channel == null) {
            debugLog.log(TAG, "Creating event suggestion notification channel")
            createEventSuggestionNotificationChannel()
        } else if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            debugLog.log(TAG, "FAILED: Event suggestion notification channel is blocked by user")
            return "FAILED: Event suggestion notification channel is blocked by user"
        }

        // Create intent to open the home screen (where suggestions are displayed)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_HOME)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            EVENT_SUGGESTION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EVENT_SUGGESTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        debugLog.log(TAG, "Posting event suggestion notification with ID=$EVENT_SUGGESTION_NOTIFICATION_ID")

        try {
            NotificationManagerCompat.from(context).notify(EVENT_SUGGESTION_NOTIFICATION_ID, notification)
            debugLog.log(TAG, "SUCCESS: Event suggestion notification posted")
            return "SUCCESS: Event suggestion notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    private fun createEventNotificationChannel() {
        val name = context.getString(R.string.event_notification_channel_name)
        val description = context.getString(R.string.event_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(EVENT_NOTIFICATION_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Event notification channel '$EVENT_NOTIFICATION_CHANNEL_ID' created")
    }

    /**
     * Shows a notification for an upcoming or completed calendar event.
     * Used for dynamic "before" and "after" event reminders.
     */
    fun showEventNotification(title: String, body: String, tag: String): String {
        debugLog.log(TAG, "showEventNotification() called - title='$title', tag='$tag'")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                debugLog.log(TAG, "FAILED: No notification permission on Android 13+")
                return "FAILED: POST_NOTIFICATIONS permission not granted"
            }
        }

        // Check if notifications are enabled at system level
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            debugLog.log(TAG, "FAILED: System notifications are disabled for this app")
            return "FAILED: System notifications are disabled for this app"
        }

        // Check/create event notification channel
        val channel = notificationManager.getNotificationChannel(EVENT_NOTIFICATION_CHANNEL_ID)
        if (channel == null) {
            debugLog.log(TAG, "Creating event notification channel")
            createEventNotificationChannel()
        } else if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            debugLog.log(TAG, "FAILED: Event notification channel is blocked by user")
            return "FAILED: Event notification channel is blocked by user"
        }

        // Create intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_COACH)
            putExtra(EXTRA_COACH_MESSAGE, body)
            putExtra(EXTRA_COACH_TITLE, title)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EVENT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        // Use tag-based notification ID
        val notificationId = EVENT_NOTIFICATION_ID_BASE + (tag.hashCode() and 0xFFFF)
        debugLog.log(TAG, "Posting event notification with ID=$notificationId, tag=$tag")

        try {
            NotificationManagerCompat.from(context).notify(tag, notificationId, notification)
            debugLog.log(TAG, "SUCCESS: Event notification posted")
            return "SUCCESS: Event notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    private fun createDailyToolNotificationChannel() {
        val name = context.getString(R.string.daily_tool_channel_name)
        val description = context.getString(R.string.daily_tool_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(DAILY_TOOL_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Daily tool notification channel '$DAILY_TOOL_CHANNEL_ID' created")
    }

    /**
     * Shows a notification when a daily tool has been generated and is ready to use.
     */
    fun showDailyToolReadyNotification(title: String, body: String): String {
        debugLog.log(TAG, "showDailyToolReadyNotification() called - title='$title', body='$body'")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                debugLog.log(TAG, "FAILED: No notification permission on Android 13+")
                return "FAILED: POST_NOTIFICATIONS permission not granted"
            }
        }

        // Check if notifications are enabled at system level
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            debugLog.log(TAG, "FAILED: System notifications are disabled for this app")
            return "FAILED: System notifications are disabled for this app"
        }

        // Check/create daily tool channel
        val channel = notificationManager.getNotificationChannel(DAILY_TOOL_CHANNEL_ID)
        if (channel == null) {
            debugLog.log(TAG, "Creating daily tool notification channel")
            createDailyToolNotificationChannel()
        } else if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            debugLog.log(TAG, "FAILED: Daily tool notification channel is blocked by user")
            return "FAILED: Daily tool notification channel is blocked by user"
        }

        // Create intent to open the daily tools screen
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_DAILY_TOOLS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_TOOL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DAILY_TOOL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        debugLog.log(TAG, "Posting daily tool notification with ID=$DAILY_TOOL_NOTIFICATION_ID")

        try {
            NotificationManagerCompat.from(context).notify(DAILY_TOOL_NOTIFICATION_ID, notification)
            debugLog.log(TAG, "SUCCESS: Daily tool notification posted")
            return "SUCCESS: Daily tool notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    private fun createCoachCallNotificationChannel() {
        val name = context.getString(R.string.coach_call_channel_name)
        val description = context.getString(R.string.coach_call_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(COACH_CALL_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        debugLog.log(TAG, "Coach call notification channel '$COACH_CALL_CHANNEL_ID' created")
    }

    /**
     * Shows a high-priority incoming call notification from the coach.
     * Uses a full-screen intent to show the call screen even when the phone is locked.
     */
    fun showIncomingCoachCallNotification(): String {
        debugLog.log(TAG, "showIncomingCoachCallNotification() called")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                debugLog.log(TAG, "FAILED: No notification permission on Android 13+")
                return "FAILED: POST_NOTIFICATIONS permission not granted"
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            debugLog.log(TAG, "FAILED: System notifications are disabled for this app")
            return "FAILED: System notifications are disabled for this app"
        }

        // Check/create coach call channel
        val channel = notificationManager.getNotificationChannel(COACH_CALL_CHANNEL_ID)
        if (channel == null) {
            createCoachCallNotificationChannel()
        } else if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            debugLog.log(TAG, "FAILED: Coach call notification channel is blocked by user")
            return "FAILED: Coach call notification channel is blocked by user"
        }

        // Full-screen intent to open the call screen directly
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_CALL)
            putExtra(EXTRA_AUTO_ANSWER_CALL, true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            COACH_CALL_NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer action
        val answerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_CALL)
            putExtra(EXTRA_AUTO_ANSWER_CALL, true)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            COACH_CALL_NOTIFICATION_ID + 1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline action - just dismiss the notification
        val declineIntent = Intent(context, CoachCallActionReceiver::class.java).apply {
            action = CoachCallActionReceiver.ACTION_DECLINE
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            COACH_CALL_NOTIFICATION_ID + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, COACH_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.coach_call_notification_title))
            .setContentText(context.getString(R.string.coach_call_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .addAction(0, context.getString(R.string.coach_call_answer), answerPendingIntent)
            .addAction(0, context.getString(R.string.coach_call_decline), declinePendingIntent)
            .build()

        debugLog.log(TAG, "Posting incoming coach call notification with ID=$COACH_CALL_NOTIFICATION_ID")
        try {
            NotificationManagerCompat.from(context).notify(COACH_CALL_NOTIFICATION_ID, notification)
            debugLog.log(TAG, "SUCCESS: Incoming coach call notification posted")
            return "SUCCESS: Incoming coach call notification posted"
        } catch (e: Exception) {
            debugLog.log(TAG, "EXCEPTION: ${e.message}")
            return "EXCEPTION: ${e.message}"
        }
    }

    /**
     * Dismisses the incoming coach call notification.
     */
    fun dismissCoachCallNotification() {
        NotificationManagerCompat.from(context).cancel(COACH_CALL_NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "journal_reminder"
        const val DYNAMIC_CHANNEL_ID = "dynamic_coach"
        const val CHAT_RESPONSE_CHANNEL_ID = "chat_response"
        const val EVENT_SUGGESTION_CHANNEL_ID = "event_suggestions"
        const val EVENT_NOTIFICATION_CHANNEL_ID = "event_notifications"
        const val DAILY_TOOL_CHANNEL_ID = "daily_tools"
        const val COACH_CALL_CHANNEL_ID = "coach_call"
        const val NOTIFICATION_ID = 1001
        const val DYNAMIC_NOTIFICATION_ID = 1002
        const val CHAT_RESPONSE_NOTIFICATION_ID_BASE = 2000
        const val EVENT_SUGGESTION_NOTIFICATION_ID = 3001
        const val EVENT_NOTIFICATION_ID_BASE = 4000
        const val DAILY_TOOL_NOTIFICATION_ID = 5001
        const val COACH_CALL_NOTIFICATION_ID = 6001
        private const val TAG = "NotificationHelper"

        // Intent extras for deep linking
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val EXTRA_COACH_MESSAGE = "coach_message"
        const val EXTRA_COACH_TITLE = "coach_title"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_AUTO_ANSWER_CALL = "auto_answer_call"
        const val NAVIGATE_TO_COACH = "coach"
        const val NAVIGATE_TO_CONVERSATION = "conversation"
        const val NAVIGATE_TO_HOME = "home"
        const val NAVIGATE_TO_DAILY_TOOLS = "daily_tools"
        const val NAVIGATE_TO_CALL = "call"
    }
}
