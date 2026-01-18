package com.personalcoacher.notification

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.util.onError
import com.personalcoacher.util.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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

        // Input data keys
        const val KEY_FORCE_REGENERATE = "force_regenerate"
        const val KEY_SHOW_NOTIFICATION = "show_notification"

        /**
         * Schedule the daily app generation worker.
         * Runs once per day when network is available.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyAppGenerationWorker>(
                24, TimeUnit.HOURS,
                1, TimeUnit.HOURS // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Daily app generation worker scheduled")
        }

        /**
         * Cancel the daily app generation worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Daily app generation worker cancelled")
        }

        /**
         * Start a one-time generation that runs in the background.
         * Will continue even if the user leaves the app or screen.
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
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "One-time daily app generation started (forceRegenerate=$forceRegenerate)")
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

    override suspend fun doWork(): Result {
        val forceRegenerate = inputData.getBoolean(KEY_FORCE_REGENERATE, false)
        val showNotification = inputData.getBoolean(KEY_SHOW_NOTIFICATION, false)

        Log.d(TAG, "Starting daily app generation (forceRegenerate=$forceRegenerate, showNotification=$showNotification, runAttemptCount=$runAttemptCount)")

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
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daily app generation", e)

            // After 2 retries (3 total attempts), give up and notify
            if (runAttemptCount >= 2) {
                Log.w(TAG, "Max retries reached, failing permanently")
                if (showNotification) {
                    notificationHelper.showDailyToolReadyNotification(
                        title = "Tool Generation Failed",
                        body = "Network error - tap to try again"
                    )
                }
                return Result.failure()
            }

            // Retry for network/transient errors
            Log.d(TAG, "Will retry (attempt ${runAttemptCount + 1})")
            Result.retry()
        }
    }
}
