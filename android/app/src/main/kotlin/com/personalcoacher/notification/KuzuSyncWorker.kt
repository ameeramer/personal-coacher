package com.personalcoacher.notification

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.kuzu.KuzuSyncService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that performs incremental sync from Room to Kuzu knowledge graph.
 *
 * This worker is triggered:
 * 1. After journal entry creation/update (debounced)
 * 2. After chat message completion
 * 3. After agenda item creation
 * 4. Periodically (every 15 minutes) when app is in background
 *
 * The worker only runs if:
 * - RAG migration has been completed
 * - Auto-sync is enabled in settings
 * - User has Voyage API key configured
 *
 * Optimization:
 * - Uses timestamps to only sync records modified since last sync
 * - Uses MERGE (upsert) queries to handle updates efficiently
 * - Only extracts atomic thoughts from new journal entries
 */
@HiltWorker
class KuzuSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kuzuSyncService: KuzuSyncService,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_IMMEDIATE = "kuzu_sync_immediate"
        const val WORK_NAME_PERIODIC = "kuzu_sync_periodic"
        const val KEY_USER_ID = "user_id"
        const val KEY_SYNC_TYPE = "sync_type"

        // Sync types
        const val SYNC_TYPE_FULL = "full"
        const val SYNC_TYPE_JOURNAL = "journal"
        const val SYNC_TYPE_MESSAGE = "message"
        const val SYNC_TYPE_AGENDA = "agenda"
        const val SYNC_TYPE_SUMMARY = "summary"
        const val SYNC_TYPE_DAILY_APP = "daily_app"

        private const val TAG = "KuzuSyncWorker"
    }

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID)
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL

        if (userId.isNullOrBlank()) {
            Log.e(TAG, "No user ID provided, skipping sync")
            return Result.failure()
        }

        // Check if sync is enabled
        if (!kuzuSyncService.isSyncEnabled()) {
            Log.d(TAG, "Sync not enabled (RAG migration not complete or auto-sync disabled)")
            return Result.success()
        }

        // Check for Voyage API key
        if (!tokenManager.hasVoyageApiKey()) {
            Log.d(TAG, "Voyage API key not configured, skipping sync")
            return Result.success()
        }

        Log.d(TAG, "Starting $syncType sync for user $userId")

        return try {
            val stats = kuzuSyncService.performIncrementalSync(userId)

            if (stats.isEmpty) {
                Log.d(TAG, "No changes to sync")
            } else {
                Log.d(TAG, "Sync completed: ${stats.totalNodes} nodes, ${stats.relationships} relationships")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)

            // Retry with exponential backoff for transient failures
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
