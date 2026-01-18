package com.personalcoacher.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalcoacher.R
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.util.onError
import com.personalcoacher.util.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.InetAddress
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for automatic daily app generation.
 * Supports both periodic (scheduled) and one-time (on-demand) generation.
 * Sends a notification when generation completes.
 */
@HiltWorker
class DailyAppGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dailyAppRepository: DailyAppRepository,
    private val tokenManager: TokenManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyAppGenWorker"
        private const val WORK_NAME = "daily_app_generation"
        private const val WORK_NAME_ONE_TIME = "daily_app_generation_one_time"

        // DNS resolution timeout in milliseconds
        private const val DNS_TIMEOUT_MS = 5000L

        // Notification ID for foreground service
        private const val FOREGROUND_NOTIFICATION_ID = 2002

        // Alarm request code for PendingIntent
        private const val ALARM_REQUEST_CODE = 6001

        // Input data keys
        const val KEY_FORCE_REGENERATE = "force_regenerate"
        const val KEY_SHOW_NOTIFICATION = "show_notification"

        /**
         * Schedule the daily app generation to run at a specific time each day using AlarmManager.
         * This provides precise timing that WorkManager's periodic work cannot guarantee.
         * @param context Application context
         * @param hour Hour of day (0-23)
         * @param minute Minute of hour (0-59)
         */
        fun scheduleDaily(context: Context, hour: Int, minute: Int) {
            Log.d(TAG, "scheduleDaily($hour:$minute) called - using AlarmManager for precise timing")
            scheduleExactAlarm(context, hour, minute)
        }

        /**
         * Schedule an exact alarm using AlarmManager for the next occurrence of the specified time.
         * This is the core scheduling method that provides precise timing.
         * @param context Application context
         * @param hour Hour of day (0-23)
         * @param minute Minute of hour (0-59)
         */
        fun scheduleExactAlarm(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Calculate the trigger time for the next occurrence
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If target time has passed today, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }

            val triggerTimeMs = target.timeInMillis
            val delayMinutes = (triggerTimeMs - now.timeInMillis) / (1000 * 60)

            val intent = Intent(context, DailyToolAlarmReceiver::class.java).apply {
                putExtra(DailyToolAlarmReceiver.EXTRA_HOUR, hour)
                putExtra(DailyToolAlarmReceiver.EXTRA_MINUTE, minute)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // On Android 12+, check if we can schedule exact alarms
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled exact alarm for $hour:$minute (in $delayMinutes minutes)")
                    } else {
                        // Fall back to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled inexact alarm (no exact permission) for $hour:$minute")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for $hour:$minute (in $delayMinutes minutes)")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling alarm: ${e.message}")
                // Fall back to inexact alarm
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "Fell back to inexact alarm due to SecurityException")
            }
        }

        /**
         * Cancel the daily app generation worker and alarm.
         */
        fun cancel(context: Context) {
            // Cancel any pending alarms
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailyToolAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled daily tool alarm")
            }

            // Also cancel any WorkManager work
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONE_TIME)
            WorkManager.getInstance(context).cancelUniqueWork(DailyToolAlarmReceiver.WORK_NAME_ALARM_TRIGGERED)
            Log.d(TAG, "Daily app generation worker cancelled")
        }

        /**
         * Start a one-time generation that runs in the background.
         * Will continue even if the user leaves the app or screen.
         * Uses setExpedited() with foreground service to ensure long-running work completes.
         * @param forceRegenerate If true, generates a new app even if one exists for today
         * @param showNotification If true, shows a notification when generation completes
         */
        fun startOneTimeGeneration(
            context: Context,
            forceRegenerate: Boolean = false,
            showNotification: Boolean = true
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putBoolean(KEY_FORCE_REGENERATE, forceRegenerate)
                .putBoolean(KEY_SHOW_NOTIFICATION, showNotification)
                .build()

            val request = OneTimeWorkRequestBuilder<DailyAppGenerationWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                // Expedited work runs immediately with higher priority
                // If out of quota, falls back to regular work request
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "One-time daily app generation started (forceRegenerate=$forceRegenerate, expedited=true)")
        }

        /**
         * Check if generation is currently in progress.
         */
        fun isGenerationInProgress(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME_ONE_TIME)
                .get()
            return workInfos.any { !it.state.isFinished }
        }
    }

    /**
     * Provides the ForegroundInfo required for expedited work.
     * This is needed for setExpedited() to work properly on Android 12+.
     * Without this, long-running work will be canceled when the app is backgrounded.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createForegroundNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    /**
     * Creates a low-priority notification for the foreground service.
     * This notification is shown while generation is in progress.
     */
    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(applicationContext, NotificationHelper.DAILY_TOOL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.daily_tools_generating))
            .setContentText(applicationContext.getString(R.string.daily_tools_generating_background))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * Pre-flight check to verify DNS resolution is working.
     * WorkManager's NetworkType.CONNECTED only checks if network interface is up,
     * but doesn't verify DNS is actually functional.
     * This is important because the device can report "connected" while DNS is unavailable
     * (common during Doze mode transitions or when app is backgrounded).
     *
     * Note: This function performs blocking I/O and should only be called from a worker thread.
     */
    @androidx.annotation.WorkerThread
    private fun isDnsResolutionWorking(): Boolean {
        return try {
            // Try to resolve the Claude API host with a timeout
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Boolean> {
                try {
                    val addresses = InetAddress.getAllByName("api.anthropic.com")
                    addresses.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
            future.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            Log.w(TAG, "DNS pre-flight check timed out after ${DNS_TIMEOUT_MS}ms")
            false
        } catch (e: Exception) {
            Log.w(TAG, "DNS pre-flight check failed: ${e.message}")
            false
        }
    }

    override suspend fun doWork(): Result {
        val forceRegenerate = inputData.getBoolean(KEY_FORCE_REGENERATE, false)
        val showNotification = inputData.getBoolean(KEY_SHOW_NOTIFICATION, false)

        Log.d(TAG, "Starting daily app generation (forceRegenerate=$forceRegenerate, showNotification=$showNotification, runAttemptCount=$runAttemptCount)")

        // Pre-flight DNS check - verify network is truly functional, not just "connected"
        // This prevents attempting API calls when DNS isn't working (common when app is backgrounded)
        Log.d(TAG, "Checking DNS resolution...")
        if (!isDnsResolutionWorking()) {
            Log.w(TAG, "DNS resolution not working, will retry later")
            return Result.retry()
        }
        Log.d(TAG, "DNS check passed")

        val userId = tokenManager.getUserId()
        val apiKey = tokenManager.getClaudeApiKeySync()

        if (userId.isNullOrBlank()) {
            Log.w(TAG, "No user logged in, skipping generation")
            return Result.success()
        }

        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "No Claude API key configured, skipping generation")
            if (showNotification) {
                notificationHelper.showDailyToolReadyNotification(
                    title = "Tool Generation Failed",
                    body = "Please configure your Claude API key in Settings"
                )
            }
            return Result.failure()
        }

        return try {
            // Generate today's app
            val result = dailyAppRepository.generateTodaysApp(userId, apiKey, forceRegenerate)

            result.onSuccess { app ->
                Log.i(TAG, "Successfully generated daily app: ${app.title}")

                // Show notification if requested
                if (showNotification) {
                    notificationHelper.showDailyToolReadyNotification(
                        title = "Your Daily Tool is Ready!",
                        body = app.title
                    )
                }
            }

            result.onError { message ->
                Log.e(TAG, "Failed to generate daily app: $message")
                // Only show error notification for final failures
                // The DailyAppGenerationService already has retry logic internally,
                // so if we get here, all retries have been exhausted
                if (showNotification) {
                    notificationHelper.showDailyToolReadyNotification(
                        title = "Tool Generation Failed",
                        body = message ?: "Tap to try again"
                    )
                }
            }

            // Return success regardless of generation outcome (we've handled notifications)
            // This prevents unnecessary retries for non-recoverable errors
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Job was canceled by WorkManager - don't show error notification
            // The generation might still be in progress or might have already succeeded
            // If user manually canceled, they don't need a notification
            Log.w(TAG, "Job was canceled by WorkManager - suppressing error notification")
            throw e // Re-throw to let WorkManager handle the cancellation properly
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daily app generation", e)

            // Check if this is a network error that should be retried
            // This includes Android 15+ background network restriction errors (SocketException)
            val isNetworkError = e.message?.contains("UnknownHostException", ignoreCase = true) == true ||
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    e.message?.contains("No address associated", ignoreCase = true) == true ||
                    e.message?.contains("SocketTimeoutException", ignoreCase = true) == true ||
                    e.message?.contains("ConnectException", ignoreCase = true) == true ||
                    e.message?.contains("Network", ignoreCase = true) == true ||
                    // Android 15+ background network restriction errors:
                    e.message?.contains("SocketException", ignoreCase = true) == true ||
                    e.message?.contains("connection abort", ignoreCase = true) == true ||
                    e.message?.contains("Software caused", ignoreCase = true) == true ||
                    e.message?.contains("Socket closed", ignoreCase = true) == true ||
                    e is java.net.SocketException

            if (isNetworkError) {
                Log.w(TAG, "Network error detected, will retry (attempt ${runAttemptCount + 1})")
                // After 2 retries (3 total attempts), give up and notify
                if (runAttemptCount >= 2) {
                    Log.w(TAG, "Max retries reached for network error, failing permanently")
                    if (showNotification) {
                        notificationHelper.showDailyToolReadyNotification(
                            title = "Tool Generation Failed",
                            body = "Network unavailable - tap to try again"
                        )
                    }
                    return Result.failure()
                }
                // Don't show notification for intermediate retries - only log
                Log.d(TAG, "Will retry network error, not showing notification yet")
                return Result.retry()
            }

            // Non-network error - notify and fail immediately
            if (showNotification) {
                notificationHelper.showDailyToolReadyNotification(
                    title = "Tool Generation Failed",
                    body = e.message?.take(50) ?: "Unknown error - tap to try again"
                )
            }
            Result.failure()
        }
    }
}
