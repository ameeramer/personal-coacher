package com.personalcoacher.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personalcoacher.data.local.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler for Kuzu knowledge graph sync operations.
 *
 * Provides methods to:
 * - Schedule immediate sync after data changes (with debouncing)
 * - Schedule periodic background sync
 * - Cancel sync operations
 */
@Singleton
class KuzuSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "KuzuSyncScheduler"

        // Debounce delay for immediate syncs (to batch rapid changes)
        private const val DEBOUNCE_DELAY_SECONDS = 10L

        // Periodic sync interval
        private const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L
    }

    /**
     * Schedule an immediate sync with debouncing.
     * Multiple rapid calls within the debounce window will be batched.
     */
    fun scheduleImmediateSync(userId: String, syncType: String = KuzuSyncWorker.SYNC_TYPE_FULL) {
        if (!isAutoSyncEnabled()) {
            Log.d(TAG, "Auto-sync disabled, skipping immediate sync")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(KuzuSyncWorker.KEY_USER_ID, userId)
            .putString(KuzuSyncWorker.KEY_SYNC_TYPE, syncType)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<KuzuSyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setInitialDelay(DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // Use REPLACE policy to implement debouncing
        // If a sync is already pending, this cancels it and starts a new one
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                KuzuSyncWorker.WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        Log.d(TAG, "Scheduled immediate $syncType sync for user $userId (debounced)")
    }

    /**
     * Schedule periodic background sync.
     * Should be called when RAG migration completes or auto-sync is enabled.
     */
    fun schedulePeriodicSync(userId: String) {
        if (!isAutoSyncEnabled()) {
            Log.d(TAG, "Auto-sync disabled, not scheduling periodic sync")
            cancelPeriodicSync()
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(KuzuSyncWorker.KEY_USER_ID, userId)
            .putString(KuzuSyncWorker.KEY_SYNC_TYPE, KuzuSyncWorker.SYNC_TYPE_FULL)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<KuzuSyncWorker>(
            PERIODIC_SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                KuzuSyncWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

        Log.d(TAG, "Scheduled periodic sync every $PERIODIC_SYNC_INTERVAL_MINUTES minutes")
    }

    /**
     * Cancel periodic sync.
     * Called when auto-sync is disabled.
     */
    fun cancelPeriodicSync() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(KuzuSyncWorker.WORK_NAME_PERIODIC)
        Log.d(TAG, "Cancelled periodic sync")
    }

    /**
     * Cancel all pending sync operations.
     */
    fun cancelAllSync() {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(KuzuSyncWorker.WORK_NAME_IMMEDIATE)
            cancelUniqueWork(KuzuSyncWorker.WORK_NAME_PERIODIC)
        }
        Log.d(TAG, "Cancelled all sync operations")
    }

    /**
     * Check if auto-sync is enabled.
     */
    private fun isAutoSyncEnabled(): Boolean {
        return tokenManager.getRagMigrationCompleteSync() &&
               tokenManager.getRagAutoSyncEnabledSync() &&
               tokenManager.hasVoyageApiKey()
    }
}
