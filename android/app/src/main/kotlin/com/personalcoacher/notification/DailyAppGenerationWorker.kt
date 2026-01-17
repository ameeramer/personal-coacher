package com.personalcoacher.notification

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.util.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for automatic daily app generation.
 * Runs once per day to generate a personalized tool based on recent journal entries.
 */
@HiltWorker
class DailyAppGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dailyAppRepository: DailyAppRepository,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyAppGenWorker"
        private const val WORK_NAME = "daily_app_generation"

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
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily app generation")

        return try {
            val userId = tokenManager.getUserId()
            val apiKey = tokenManager.getClaudeApiKey()

            if (userId.isNullOrBlank()) {
                Log.w(TAG, "No user logged in, skipping generation")
                return Result.success()
            }

            if (apiKey.isNullOrBlank()) {
                Log.w(TAG, "No Claude API key configured, skipping generation")
                return Result.success()
            }

            // Generate today's app
            val result = dailyAppRepository.generateTodaysApp(userId, apiKey)

            result.onSuccess { app ->
                Log.i(TAG, "Successfully generated daily app: ${app.title}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily app", e)
            // Retry later if it fails
            Result.retry()
        }
    }
}
