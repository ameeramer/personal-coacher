package com.personalcoacher.data.local.kuzu

import android.util.Log
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.GoalDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.NoteDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.dao.TaskDao
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.local.entity.DailyAppEntity
import com.personalcoacher.data.local.entity.NoteEntity
import com.personalcoacher.data.local.entity.GoalEntity
import com.personalcoacher.data.local.entity.TaskEntity
import com.personalcoacher.data.remote.AtomicThoughtExtractor
import com.personalcoacher.data.remote.VoyageEmbeddingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAG Incremental Sync Service
 *
 * Handles real-time incremental synchronization from Room database to Kuzu knowledge graph.
 * Unlike the full migration, this service:
 * 1. Only processes records modified since the last sync timestamp
 * 2. Uses MERGE (upsert) to update existing nodes instead of CREATE
 * 3. Only creates relationships for new nodes (skips existing-to-existing relationships)
 * 4. Runs in the background via WorkManager with debouncing
 *
 * Cost Optimization Strategy:
 * - Embeddings: Only generated for new/modified content
 * - Atomic Thoughts: Only extracted from new journal entries
 * - Relationships: Only created between new nodes and existing nodes
 * - No recalculation of existing-to-existing relationships
 */
@Singleton
class KuzuSyncService @Inject constructor(
    private val kuzuDb: KuzuDatabaseManager,
    private val voyageService: VoyageEmbeddingService,
    private val atomicThoughtExtractor: AtomicThoughtExtractor,
    private val tokenManager: TokenManager,
    private val journalEntryDao: JournalEntryDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val agendaItemDao: AgendaItemDao,
    private val summaryDao: SummaryDao,
    private val dailyAppDao: DailyAppDao,
    private val noteDao: NoteDao,
    private val goalDao: GoalDao,
    private val taskDao: TaskDao
) {
    companion object {
        private const val TAG = "KuzuSyncService"
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Check if incremental sync is available (RAG migration complete + auto-sync enabled).
     */
    fun isSyncEnabled(): Boolean {
        return tokenManager.getRagMigrationCompleteSync() && tokenManager.getRagAutoSyncEnabledSync()
    }

    /**
     * Perform incremental sync for all entity types.
     * Returns sync statistics.
     */
    suspend fun performIncrementalSync(userId: String): SyncStats = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) {
            Log.d(TAG, "Sync disabled - RAG migration not complete or auto-sync disabled")
            return@withContext SyncStats()
        }

        try {
            _syncState.value = SyncState.Syncing("Starting incremental sync...")

            // Ensure Kuzu is initialized
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            var journalCount = 0
            var messageCount = 0
            var agendaCount = 0
            var summaryCount = 0
            var dailyAppCount = 0
            var noteCount = 0
            var userGoalCount = 0
            var userTaskCount = 0
            var thoughtCount = 0
            var relationshipCount = 0

            // Sync journal entries
            _syncState.value = SyncState.Syncing("Syncing journal entries...")
            val journalResult = syncJournalEntries(userId)
            journalCount = journalResult.first
            thoughtCount = journalResult.second
            relationshipCount += journalResult.third

            // Sync chat messages
            _syncState.value = SyncState.Syncing("Syncing chat messages...")
            messageCount = syncChatMessages(userId)

            // Sync agenda items
            _syncState.value = SyncState.Syncing("Syncing agenda items...")
            val agendaResult = syncAgendaItems(userId)
            agendaCount = agendaResult.first
            relationshipCount += agendaResult.second

            // Sync summaries
            _syncState.value = SyncState.Syncing("Syncing summaries...")
            val summaryResult = syncSummaries(userId)
            summaryCount = summaryResult.first
            relationshipCount += summaryResult.second

            // Sync daily apps
            _syncState.value = SyncState.Syncing("Syncing daily apps...")
            val dailyAppResult = syncDailyApps(userId)
            dailyAppCount = dailyAppResult.first
            relationshipCount += dailyAppResult.second

            // Sync notes
            _syncState.value = SyncState.Syncing("Syncing notes...")
            val noteResult = syncNotes(userId)
            noteCount = noteResult.first
            thoughtCount += noteResult.second
            relationshipCount += noteResult.third

            // Sync user goals
            _syncState.value = SyncState.Syncing("Syncing user goals...")
            val userGoalResult = syncUserGoals(userId)
            userGoalCount = userGoalResult.first
            thoughtCount += userGoalResult.second
            relationshipCount += userGoalResult.third

            // Sync user tasks
            _syncState.value = SyncState.Syncing("Syncing user tasks...")
            val userTaskResult = syncUserTasks(userId)
            userTaskCount = userTaskResult.first
            thoughtCount += userTaskResult.second
            relationshipCount += userTaskResult.third

            // Sync deletions - remove orphaned nodes from GraphRAG
            _syncState.value = SyncState.Syncing("Syncing deletions...")
            val deletedCount = syncDeletions(userId)

            // Checkpoint to persist changes
            try {
                kuzuDb.checkpoint()
            } catch (e: Exception) {
                Log.w(TAG, "Checkpoint failed but sync data should be in WAL", e)
            }

            val stats = SyncStats(
                journalEntries = journalCount,
                chatMessages = messageCount,
                agendaItems = agendaCount,
                summaries = summaryCount,
                dailyApps = dailyAppCount,
                notes = noteCount,
                userGoals = userGoalCount,
                userTasks = userTaskCount,
                atomicThoughts = thoughtCount,
                relationships = relationshipCount,
                deletedNodes = deletedCount
            )

            val currentTime = System.currentTimeMillis()

            // Always update the "last checked" timestamp (sync ran, even if nothing to sync)
            tokenManager.setLastCheckedTimestamp(currentTime)

            // Only update the "last synced" timestamp if actual data was changed
            if (!stats.isEmpty) {
                tokenManager.setLastOverallSyncTimestamp(currentTime)
            }

            _syncState.value = SyncState.Completed(stats)
            Log.d(TAG, "Incremental sync completed: $stats")
            stats

        } catch (e: Exception) {
            Log.e(TAG, "Incremental sync failed", e)
            _syncState.value = SyncState.Failed(e.message ?: "Unknown error")
            SyncStats()
        }
    }

    /**
     * Sync a single journal entry immediately (called after creation/update).
     */
    suspend fun syncJournalEntry(entry: JournalEntryEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) return@withContext false

        try {
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            // Generate embedding
            val embedding = try {
                voyageService.embed(entry.content)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate embedding for entry ${entry.id}", e)
                null
            }

            // Upsert journal entry node
            upsertJournalEntryNode(entry, embedding)

            // Extract atomic thoughts for new entries
            if (tokenManager.hasClaudeApiKey()) {
                extractAndStoreThoughts(entry)
            }

            // Update sync timestamp
            tokenManager.setLastJournalSyncTimestamp(entry.updatedAt)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync journal entry ${entry.id}", e)
            false
        }
    }

    /**
     * Sync a single chat message immediately (called after completion).
     */
    suspend fun syncChatMessage(message: MessageEntity, conversationUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) return@withContext false

        try {
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            // Only embed user messages
            val embedding = if (message.role.lowercase() == "user") {
                try {
                    voyageService.embed(message.content)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for message ${message.id}", e)
                    null
                }
            } else null

            upsertChatMessageNode(message, conversationUserId, embedding)

            // Update sync timestamp
            tokenManager.setLastMessageSyncTimestamp(message.updatedAt)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync chat message ${message.id}", e)
            false
        }
    }

    /**
     * Sync a single agenda item immediately.
     */
    suspend fun syncAgendaItem(item: AgendaItemEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) return@withContext false

        try {
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            val textToEmbed = "${item.title} ${item.description ?: ""}"
            val embedding = try {
                voyageService.embed(textToEmbed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate embedding for agenda item ${item.id}", e)
                null
            }

            upsertAgendaItemNode(item, embedding)

            // Create SOURCED_FROM relationship if applicable
            if (item.sourceJournalEntryId != null) {
                createAgendaSourceRelationship(item)
            }

            // Update sync timestamp
            tokenManager.setLastAgendaSyncTimestamp(item.updatedAt)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync agenda item ${item.id}", e)
            false
        }
    }

    // ==================== Batch Sync Methods ====================

    private suspend fun syncJournalEntries(userId: String): Triple<Int, Int, Int> {
        val lastSync = tokenManager.getLastJournalSyncTimestampSync()
        val modifiedEntries = journalEntryDao.getEntriesModifiedSince(userId, lastSync)

        if (modifiedEntries.isEmpty()) {
            Log.d(TAG, "No journal entries to sync since $lastSync")
            return Triple(0, 0, 0)
        }

        Log.d(TAG, "Syncing ${modifiedEntries.size} journal entries modified since $lastSync")

        var syncedCount = 0
        var thoughtCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (entry in modifiedEntries) {
            try {
                // Generate embedding
                val embedding = try {
                    voyageService.embed(entry.content)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for entry ${entry.id}", e)
                    null
                }

                // Upsert node
                upsertJournalEntryNode(entry, embedding)
                syncedCount++

                // Extract atomic thoughts
                if (tokenManager.hasClaudeApiKey()) {
                    val result = extractAndStoreThoughts(entry)
                    thoughtCount += result.first
                    relationshipCount += result.second
                }

                if (entry.updatedAt > maxTimestamp) {
                    maxTimestamp = entry.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync journal entry ${entry.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastJournalSyncTimestamp(maxTimestamp)

        return Triple(syncedCount, thoughtCount, relationshipCount)
    }

    private suspend fun syncChatMessages(userId: String): Int {
        val lastSync = tokenManager.getLastMessageSyncTimestampSync()
        val modifiedMessages = messageDao.getMessagesModifiedSince(userId, lastSync)

        if (modifiedMessages.isEmpty()) {
            Log.d(TAG, "No chat messages to sync since $lastSync")
            return 0
        }

        Log.d(TAG, "Syncing ${modifiedMessages.size} chat messages modified since $lastSync")

        var syncedCount = 0
        var maxTimestamp = lastSync

        for (message in modifiedMessages) {
            try {
                // Only embed user messages
                val embedding = if (message.role.lowercase() == "user") {
                    try {
                        voyageService.embed(message.content)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to generate embedding for message ${message.id}", e)
                        null
                    }
                } else null

                // Get userId from conversation
                val conversation = conversationDao.getConversationByIdSync(message.conversationId)
                val msgUserId = conversation?.userId ?: userId

                upsertChatMessageNode(message, msgUserId, embedding)
                syncedCount++

                if (message.updatedAt > maxTimestamp) {
                    maxTimestamp = message.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync chat message ${message.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastMessageSyncTimestamp(maxTimestamp)

        return syncedCount
    }

    private suspend fun syncAgendaItems(userId: String): Pair<Int, Int> {
        val lastSync = tokenManager.getLastAgendaSyncTimestampSync()
        val modifiedItems = agendaItemDao.getItemsModifiedSince(userId, lastSync)

        if (modifiedItems.isEmpty()) {
            Log.d(TAG, "No agenda items to sync since $lastSync")
            return Pair(0, 0)
        }

        Log.d(TAG, "Syncing ${modifiedItems.size} agenda items modified since $lastSync")

        var syncedCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (item in modifiedItems) {
            try {
                val textToEmbed = "${item.title} ${item.description ?: ""}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for agenda item ${item.id}", e)
                    null
                }

                upsertAgendaItemNode(item, embedding)
                syncedCount++

                // Create relationship if applicable
                if (item.sourceJournalEntryId != null) {
                    if (createAgendaSourceRelationship(item)) {
                        relationshipCount++
                    }
                }

                if (item.updatedAt > maxTimestamp) {
                    maxTimestamp = item.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync agenda item ${item.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastAgendaSyncTimestamp(maxTimestamp)

        return Pair(syncedCount, relationshipCount)
    }

    private suspend fun syncSummaries(userId: String): Pair<Int, Int> {
        val lastSync = tokenManager.getLastSummarySyncTimestampSync()
        val newSummaries = summaryDao.getSummariesCreatedSince(userId, lastSync)

        if (newSummaries.isEmpty()) {
            Log.d(TAG, "No summaries to sync since $lastSync")
            return Pair(0, 0)
        }

        Log.d(TAG, "Syncing ${newSummaries.size} summaries created since $lastSync")

        var syncedCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (summary in newSummaries) {
            try {
                val embedding = try {
                    voyageService.embed(summary.content)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for summary ${summary.id}", e)
                    null
                }

                upsertSummaryNode(summary, embedding)
                syncedCount++

                // Create SUMMARIZES relationships to journal entries in range
                relationshipCount += createSummaryRelationships(summary)

                if (summary.createdAt > maxTimestamp) {
                    maxTimestamp = summary.createdAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync summary ${summary.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastSummarySyncTimestamp(maxTimestamp)

        return Pair(syncedCount, relationshipCount)
    }

    private suspend fun syncDailyApps(userId: String): Pair<Int, Int> {
        val lastSync = tokenManager.getLastDailyAppSyncTimestampSync()
        val modifiedApps = dailyAppDao.getAppsModifiedSince(userId, lastSync)

        if (modifiedApps.isEmpty()) {
            Log.d(TAG, "No daily apps to sync since $lastSync")
            return Pair(0, 0)
        }

        // Only sync apps that are LIKED (saved by user) - not pending or generated-only apps
        val savedApps = modifiedApps.filter { it.status == "LIKED" }
        Log.d(TAG, "Syncing ${savedApps.size} saved daily apps (out of ${modifiedApps.size} modified since $lastSync)")

        var syncedCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (app in savedApps) {
            try {
                val textToEmbed = "${app.title} ${app.description}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for daily app ${app.id}", e)
                    null
                }

                upsertDailyAppNode(app, embedding)
                syncedCount++

                // Create APP_INSPIRED_BY relationships to journal entries from past 7 days
                relationshipCount += createDailyAppRelationships(app)

                if (app.updatedAt > maxTimestamp) {
                    maxTimestamp = app.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync daily app ${app.id}", e)
            }
        }

        // Update timestamp with max from all modified apps (including non-saved)
        // This prevents re-processing the same apps on next sync
        val actualMaxTimestamp = modifiedApps.maxOfOrNull { it.updatedAt } ?: maxTimestamp
        tokenManager.setLastDailyAppSyncTimestamp(actualMaxTimestamp)

        return Pair(syncedCount, relationshipCount)
    }

    private suspend fun syncNotes(userId: String): Triple<Int, Int, Int> {
        val lastSync = tokenManager.getLastNoteSyncTimestampSync()
        val modifiedNotes = noteDao.getNotesModifiedSince(userId, lastSync)

        if (modifiedNotes.isEmpty()) {
            Log.d(TAG, "No notes to sync since $lastSync")
            return Triple(0, 0, 0)
        }

        Log.d(TAG, "Syncing ${modifiedNotes.size} notes modified since $lastSync")

        var syncedCount = 0
        var thoughtCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (note in modifiedNotes) {
            try {
                val textToEmbed = "${note.title} ${note.content}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for note ${note.id}", e)
                    null
                }

                upsertNoteNode(note, embedding)
                syncedCount++

                // Extract atomic thoughts from note
                if (tokenManager.hasClaudeApiKey()) {
                    val result = extractAndStoreThoughtsFromNote(note)
                    thoughtCount += result.first
                    relationshipCount += result.second
                }

                if (note.updatedAt > maxTimestamp) {
                    maxTimestamp = note.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync note ${note.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastNoteSyncTimestamp(maxTimestamp)

        return Triple(syncedCount, thoughtCount, relationshipCount)
    }

    private suspend fun syncUserGoals(userId: String): Triple<Int, Int, Int> {
        val lastSync = tokenManager.getLastUserGoalSyncTimestampSync()
        val modifiedGoals = goalDao.getGoalsModifiedSince(userId, lastSync)

        if (modifiedGoals.isEmpty()) {
            Log.d(TAG, "No user goals to sync since $lastSync")
            return Triple(0, 0, 0)
        }

        Log.d(TAG, "Syncing ${modifiedGoals.size} user goals modified since $lastSync")

        var syncedCount = 0
        var thoughtCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (goal in modifiedGoals) {
            try {
                val textToEmbed = "${goal.title} ${goal.description}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for user goal ${goal.id}", e)
                    null
                }

                upsertUserGoalNode(goal, embedding)
                syncedCount++

                // Extract atomic thoughts from goal
                if (tokenManager.hasClaudeApiKey()) {
                    val result = extractAndStoreThoughtsFromGoal(goal)
                    thoughtCount += result.first
                    relationshipCount += result.second
                }

                if (goal.updatedAt > maxTimestamp) {
                    maxTimestamp = goal.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync user goal ${goal.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastUserGoalSyncTimestamp(maxTimestamp)

        return Triple(syncedCount, thoughtCount, relationshipCount)
    }

    private suspend fun syncUserTasks(userId: String): Triple<Int, Int, Int> {
        val lastSync = tokenManager.getLastUserTaskSyncTimestampSync()
        val modifiedTasks = taskDao.getTasksModifiedSince(userId, lastSync)

        if (modifiedTasks.isEmpty()) {
            Log.d(TAG, "No user tasks to sync since $lastSync")
            return Triple(0, 0, 0)
        }

        Log.d(TAG, "Syncing ${modifiedTasks.size} user tasks modified since $lastSync")

        var syncedCount = 0
        var thoughtCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (task in modifiedTasks) {
            try {
                val textToEmbed = "${task.title} ${task.description}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for user task ${task.id}", e)
                    null
                }

                upsertUserTaskNode(task, embedding)
                syncedCount++

                // Create TASK_LINKED_TO_GOAL relationship if applicable
                if (task.linkedGoalId != null) {
                    if (createTaskGoalRelationship(task)) {
                        relationshipCount++
                    }
                }

                // Extract atomic thoughts from task
                if (tokenManager.hasClaudeApiKey()) {
                    val result = extractAndStoreThoughtsFromTask(task)
                    thoughtCount += result.first
                    relationshipCount += result.second
                }

                if (task.updatedAt > maxTimestamp) {
                    maxTimestamp = task.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync user task ${task.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastUserTaskSyncTimestamp(maxTimestamp)

        return Triple(syncedCount, thoughtCount, relationshipCount)
    }

    /**
     * Sync a single note immediately (called after creation/update).
     */
    suspend fun syncNote(note: NoteEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) return@withContext false

        try {
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            val textToEmbed = "${note.title} ${note.content}"
            val embedding = try {
                voyageService.embed(textToEmbed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate embedding for note ${note.id}", e)
                null
            }

            upsertNoteNode(note, embedding)

            // Update sync timestamp
            tokenManager.setLastNoteSyncTimestamp(note.updatedAt)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync note ${note.id}", e)
            false
        }
    }

    /**
     * Sync a single user goal immediately (called after creation/update).
     */
    suspend fun syncUserGoal(goal: GoalEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) return@withContext false

        try {
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            val textToEmbed = "${goal.title} ${goal.description}"
            val embedding = try {
                voyageService.embed(textToEmbed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate embedding for user goal ${goal.id}", e)
                null
            }

            upsertUserGoalNode(goal, embedding)

            // Update sync timestamp
            tokenManager.setLastUserGoalSyncTimestamp(goal.updatedAt)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user goal ${goal.id}", e)
            false
        }
    }

    /**
     * Sync a single user task immediately (called after creation/update).
     */
    suspend fun syncUserTask(task: TaskEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled()) return@withContext false

        try {
            if (!kuzuDb.isInitialized()) {
                kuzuDb.initialize()
            }

            val textToEmbed = "${task.title} ${task.description}"
            val embedding = try {
                voyageService.embed(textToEmbed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate embedding for user task ${task.id}", e)
                null
            }

            upsertUserTaskNode(task, embedding)

            // Create TASK_LINKED_TO_GOAL relationship if applicable
            if (task.linkedGoalId != null) {
                createTaskGoalRelationship(task)
            }

            // Update sync timestamp
            tokenManager.setLastUserTaskSyncTimestamp(task.updatedAt)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user task ${task.id}", e)
            false
        }
    }

    // ==================== Node Upsert Methods (MERGE) ====================

    private suspend fun upsertJournalEntryNode(entry: JournalEntryEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        // Use MERGE to upsert
        val query = """
            MERGE (j:JournalEntry {id: '${entry.id}'})
            SET j.userId = '${entry.userId}',
                j.content = '${escapeString(entry.content)}',
                j.mood = ${entry.mood?.let { "'$it'" } ?: "NULL"},
                j.tags = ${if (entry.tags.isNotBlank()) "'${entry.tags}'" else "NULL"},
                j.date = ${entry.date},
                j.createdAt = ${entry.createdAt},
                j.updatedAt = ${entry.updatedAt},
                j.embedding = $embeddingStr,
                j.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertChatMessageNode(message: MessageEntity, userId: String, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (m:ChatMessage {id: '${message.id}'})
            SET m.conversationId = '${message.conversationId}',
                m.userId = '$userId',
                m.role = '${message.role}',
                m.content = '${escapeString(message.content)}',
                m.createdAt = ${message.createdAt},
                m.embedding = $embeddingStr,
                m.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertAgendaItemNode(item: AgendaItemEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (a:AgendaItem {id: '${item.id}'})
            SET a.userId = '${item.userId}',
                a.title = '${escapeString(item.title)}',
                a.description = ${item.description?.let { "'${escapeString(it)}'" } ?: "NULL"},
                a.startTime = ${item.startTime},
                a.endTime = ${item.endTime ?: "NULL"},
                a.isAllDay = ${item.isAllDay},
                a.location = ${item.location?.let { "'${escapeString(it)}'" } ?: "NULL"},
                a.createdAt = ${item.createdAt},
                a.embedding = $embeddingStr,
                a.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertSummaryNode(summary: SummaryEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (s:Summary {id: '${summary.id}'})
            SET s.userId = '${summary.userId}',
                s.summaryType = '${summary.type}',
                s.content = '${escapeString(summary.content)}',
                s.periodStart = ${summary.startDate},
                s.periodEnd = ${summary.endDate},
                s.createdAt = ${summary.createdAt},
                s.embedding = $embeddingStr,
                s.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertDailyAppNode(app: DailyAppEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (d:DailyApp {id: '${app.id}'})
            SET d.userId = '${app.userId}',
                d.date = ${app.date},
                d.title = '${escapeString(app.title)}',
                d.description = '${escapeString(app.description)}',
                d.htmlCode = '${escapeString(app.htmlCode)}',
                d.journalContext = ${app.journalContext?.let { "'${escapeString(it)}'" } ?: "NULL"},
                d.status = '${app.status}',
                d.usedAt = ${app.usedAt ?: "NULL"},
                d.createdAt = ${app.createdAt},
                d.embedding = $embeddingStr,
                d.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertNoteNode(note: NoteEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (n:Note {id: '${note.id}'})
            SET n.userId = '${note.userId}',
                n.title = '${escapeString(note.title)}',
                n.content = '${escapeString(note.content)}',
                n.createdAt = ${note.createdAt},
                n.updatedAt = ${note.updatedAt},
                n.embedding = $embeddingStr,
                n.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertUserGoalNode(goal: GoalEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (g:UserGoal {id: '${goal.id}'})
            SET g.userId = '${goal.userId}',
                g.title = '${escapeString(goal.title)}',
                g.description = '${escapeString(goal.description)}',
                g.targetDate = ${goal.targetDate?.let { "'$it'" } ?: "NULL"},
                g.status = '${goal.status}',
                g.priority = '${goal.priority}',
                g.createdAt = ${goal.createdAt},
                g.updatedAt = ${goal.updatedAt},
                g.embedding = $embeddingStr,
                g.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    private suspend fun upsertUserTaskNode(task: TaskEntity, embedding: FloatArray?) {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
        val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

        val query = """
            MERGE (t:UserTask {id: '${task.id}'})
            SET t.userId = '${task.userId}',
                t.title = '${escapeString(task.title)}',
                t.description = '${escapeString(task.description)}',
                t.dueDate = ${task.dueDate?.let { "'$it'" } ?: "NULL"},
                t.isCompleted = ${task.isCompleted},
                t.priority = '${task.priority}',
                t.linkedGoalId = ${task.linkedGoalId?.let { "'$it'" } ?: "NULL"},
                t.createdAt = ${task.createdAt},
                t.updatedAt = ${task.updatedAt},
                t.embedding = $embeddingStr,
                t.embeddingModel = $modelVersion
        """.trimIndent()

        kuzuDb.execute(query)
    }

    // ==================== Relationship Methods ====================

    private suspend fun createAgendaSourceRelationship(item: AgendaItemEntity): Boolean {
        return try {
            val query = """
                MATCH (a:AgendaItem {id: '${item.id}'}), (j:JournalEntry {id: '${item.sourceJournalEntryId}'})
                MERGE (a)-[:SOURCED_FROM {createdAt: ${item.createdAt}}]->(j)
            """.trimIndent()
            kuzuDb.execute(query)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create SOURCED_FROM relationship for agenda item ${item.id}", e)
            false
        }
    }

    private suspend fun createSummaryRelationships(summary: SummaryEntity): Int {
        return try {
            val query = """
                MATCH (s:Summary {id: '${summary.id}'}), (j:JournalEntry)
                WHERE j.userId = '${summary.userId}'
                  AND j.date >= ${summary.startDate}
                  AND j.date <= ${summary.endDate}
                MERGE (s)-[:SUMMARIZES {weight: 1.0}]->(j)
            """.trimIndent()
            val result = kuzuDb.execute(query)
            // Count created relationships (approximate)
            1
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create SUMMARIZES relationships for summary ${summary.id}", e)
            0
        }
    }

    private suspend fun createDailyAppRelationships(app: DailyAppEntity): Int {
        return try {
            // DailyApp generation uses the past 7 days of journal entries (see DailyAppRepositoryImpl:80)
            // So we should connect APP_INSPIRED_BY to entries from the past 7 days, not just the same day
            val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
            val dayStart = app.date - SEVEN_DAYS_MS
            val dayEnd = app.date + (24 * 60 * 60 * 1000) - 1

            val query = """
                MATCH (d:DailyApp {id: '${app.id}'}), (j:JournalEntry)
                WHERE j.userId = '${app.userId}'
                  AND j.date >= $dayStart
                  AND j.date <= $dayEnd
                MERGE (d)-[:APP_INSPIRED_BY {relevance: 1.0}]->(j)
            """.trimIndent()
            kuzuDb.execute(query)
            Log.d(TAG, "Created APP_INSPIRED_BY relationships for daily app ${app.id} (past 7 days)")
            1
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create APP_INSPIRED_BY relationships for daily app ${app.id}", e)
            0
        }
    }

    private suspend fun createTaskGoalRelationship(task: TaskEntity): Boolean {
        return try {
            val query = """
                MATCH (t:UserTask {id: '${task.id}'}), (g:UserGoal {id: '${task.linkedGoalId}'})
                MERGE (t)-[:TASK_LINKED_TO_GOAL {createdAt: ${task.createdAt}}]->(g)
            """.trimIndent()
            kuzuDb.execute(query)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create TASK_LINKED_TO_GOAL relationship for task ${task.id}", e)
            false
        }
    }

    // ==================== Atomic Thought Extraction ====================

    private suspend fun extractAndStoreThoughts(entry: JournalEntryEntity): Pair<Int, Int> {
        var thoughtCount = 0
        var relationshipCount = 0
        val now = Instant.now().toEpochMilli()

        try {
            val extractionResult = atomicThoughtExtractor.extract(
                entryContent = entry.content,
                entryDate = entry.date.toString()
            )

            // Store extracted thoughts
            for (thought in extractionResult.thoughts) {
                val thoughtId = UUID.randomUUID().toString()

                val embedding = try {
                    voyageService.embed(thought.content)
                } catch (e: Exception) {
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                // Create thought node
                val thoughtQuery = """
                    CREATE (t:AtomicThought {
                        id: '$thoughtId',
                        userId: '${entry.userId}',
                        content: '${escapeString(thought.content)}',
                        thoughtType: '${thought.type}',
                        confidence: ${thought.confidence},
                        sentiment: ${thought.sentiment},
                        importance: ${thought.importance},
                        createdAt: $now,
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()
                kuzuDb.execute(thoughtQuery)

                // Create EXTRACTED_FROM relationship
                val relQuery = """
                    MATCH (t:AtomicThought {id: '$thoughtId'}), (j:JournalEntry {id: '${entry.id}'})
                    CREATE (t)-[:EXTRACTED_FROM {extractedAt: $now, confidence: ${thought.confidence}}]->(j)
                """.trimIndent()
                kuzuDb.execute(relQuery)

                thoughtCount++
                relationshipCount++
            }

            // Store extracted people
            for (person in extractionResult.people) {
                storePerson(entry.userId, person, entry.id, now)
                relationshipCount++
            }

            // Store extracted topics
            for (topic in extractionResult.topics) {
                storeTopic(entry.userId, topic, entry.id)
                relationshipCount++
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thoughts from entry ${entry.id}", e)
        }

        return Pair(thoughtCount, relationshipCount)
    }

    /**
     * Extract and store atomic thoughts from a Note.
     */
    private suspend fun extractAndStoreThoughtsFromNote(note: NoteEntity): Pair<Int, Int> {
        var thoughtCount = 0
        var relationshipCount = 0
        val now = Instant.now().toEpochMilli()

        try {
            val extractionResult = atomicThoughtExtractor.extractFromNote(
                noteTitle = note.title,
                noteContent = note.content
            )

            // Store extracted thoughts
            for (thought in extractionResult.thoughts) {
                val thoughtId = UUID.randomUUID().toString()

                val embedding = try {
                    voyageService.embed(thought.content)
                } catch (e: Exception) {
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                // Create thought node
                val thoughtQuery = """
                    CREATE (t:AtomicThought {
                        id: '$thoughtId',
                        userId: '${note.userId}',
                        content: '${escapeString(thought.content)}',
                        thoughtType: '${thought.type}',
                        confidence: ${thought.confidence},
                        sentiment: ${thought.sentiment},
                        importance: ${thought.importance},
                        sourceType: 'note',
                        sourceId: '${note.id}',
                        createdAt: $now,
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()
                kuzuDb.execute(thoughtQuery)

                // Create EXTRACTED_FROM_NOTE relationship
                val relQuery = """
                    MATCH (t:AtomicThought {id: '$thoughtId'}), (n:Note {id: '${note.id}'})
                    CREATE (t)-[:EXTRACTED_FROM_NOTE {extractedAt: $now, confidence: ${thought.confidence}}]->(n)
                """.trimIndent()
                kuzuDb.execute(relQuery)

                thoughtCount++
                relationshipCount++
            }

            // Store extracted topics
            for (topic in extractionResult.topics) {
                storeTopicFromNote(note.userId, topic, note.id)
                relationshipCount++
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thoughts from note ${note.id}", e)
        }

        return Pair(thoughtCount, relationshipCount)
    }

    /**
     * Extract and store atomic thoughts from a UserGoal.
     */
    private suspend fun extractAndStoreThoughtsFromGoal(goal: GoalEntity): Pair<Int, Int> {
        var thoughtCount = 0
        var relationshipCount = 0
        val now = Instant.now().toEpochMilli()

        try {
            val extractionResult = atomicThoughtExtractor.extractFromGoal(
                goalTitle = goal.title,
                goalDescription = goal.description,
                priority = goal.priority,
                targetDate = goal.targetDate
            )

            // Store extracted thoughts
            for (thought in extractionResult.thoughts) {
                val thoughtId = UUID.randomUUID().toString()

                val embedding = try {
                    voyageService.embed(thought.content)
                } catch (e: Exception) {
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                // Create thought node
                val thoughtQuery = """
                    CREATE (t:AtomicThought {
                        id: '$thoughtId',
                        userId: '${goal.userId}',
                        content: '${escapeString(thought.content)}',
                        thoughtType: '${thought.type}',
                        confidence: ${thought.confidence},
                        sentiment: ${thought.sentiment},
                        importance: ${thought.importance},
                        sourceType: 'goal',
                        sourceId: '${goal.id}',
                        createdAt: $now,
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()
                kuzuDb.execute(thoughtQuery)

                // Create EXTRACTED_FROM_GOAL relationship
                val relQuery = """
                    MATCH (t:AtomicThought {id: '$thoughtId'}), (g:UserGoal {id: '${goal.id}'})
                    CREATE (t)-[:EXTRACTED_FROM_GOAL {extractedAt: $now, confidence: ${thought.confidence}}]->(g)
                """.trimIndent()
                kuzuDb.execute(relQuery)

                thoughtCount++
                relationshipCount++
            }

            // Store extracted topics
            for (topic in extractionResult.topics) {
                storeTopicFromGoal(goal.userId, topic, goal.id)
                relationshipCount++
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thoughts from goal ${goal.id}", e)
        }

        return Pair(thoughtCount, relationshipCount)
    }

    /**
     * Extract and store atomic thoughts from a UserTask.
     */
    private suspend fun extractAndStoreThoughtsFromTask(task: TaskEntity): Pair<Int, Int> {
        var thoughtCount = 0
        var relationshipCount = 0
        val now = Instant.now().toEpochMilli()

        try {
            // Get linked goal title if available
            val linkedGoalTitle = task.linkedGoalId?.let { goalId ->
                goalDao.getGoalByIdSync(goalId)?.title
            }

            val extractionResult = atomicThoughtExtractor.extractFromTask(
                taskTitle = task.title,
                taskDescription = task.description,
                priority = task.priority,
                dueDate = task.dueDate,
                linkedGoalTitle = linkedGoalTitle
            )

            // Store extracted thoughts
            for (thought in extractionResult.thoughts) {
                val thoughtId = UUID.randomUUID().toString()

                val embedding = try {
                    voyageService.embed(thought.content)
                } catch (e: Exception) {
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                // Create thought node
                val thoughtQuery = """
                    CREATE (t:AtomicThought {
                        id: '$thoughtId',
                        userId: '${task.userId}',
                        content: '${escapeString(thought.content)}',
                        thoughtType: '${thought.type}',
                        confidence: ${thought.confidence},
                        sentiment: ${thought.sentiment},
                        importance: ${thought.importance},
                        sourceType: 'task',
                        sourceId: '${task.id}',
                        createdAt: $now,
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()
                kuzuDb.execute(thoughtQuery)

                // Create EXTRACTED_FROM_TASK relationship
                val relQuery = """
                    MATCH (t:AtomicThought {id: '$thoughtId'}), (u:UserTask {id: '${task.id}'})
                    CREATE (t)-[:EXTRACTED_FROM_TASK {extractedAt: $now, confidence: ${thought.confidence}}]->(u)
                """.trimIndent()
                kuzuDb.execute(relQuery)

                thoughtCount++
                relationshipCount++
            }

            // Store extracted topics
            for (topic in extractionResult.topics) {
                storeTopicFromTask(task.userId, topic, task.id)
                relationshipCount++
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thoughts from task ${task.id}", e)
        }

        return Pair(thoughtCount, relationshipCount)
    }

    private suspend fun storeTopicFromNote(
        userId: String,
        topic: com.personalcoacher.data.remote.ExtractedTopic,
        noteId: String
    ) {
        val normalizedName = topic.name.lowercase().trim()
        val topicId = "topic_${userId}_$normalizedName".hashCode().toString()
        val now = Instant.now().toEpochMilli()

        try {
            // Use MERGE for upsert
            val mergeQuery = """
                MERGE (t:Topic {id: '$topicId'})
                ON CREATE SET t.userId = '$userId',
                              t.name = '${escapeString(topic.name)}',
                              t.normalizedName = '${escapeString(normalizedName)}',
                              t.category = ${topic.category?.let { "'$it'" } ?: "NULL"},
                              t.createdAt = $now,
                              t.mentionCount = 1
                ON MATCH SET t.mentionCount = t.mentionCount + 1
            """.trimIndent()
            kuzuDb.execute(mergeQuery)

            // Create NOTE_RELATES_TO_TOPIC relationship
            val relQuery = """
                MATCH (n:Note {id: '$noteId'}), (t:Topic {id: '$topicId'})
                MERGE (n)-[:RELATES_TO_TOPIC {relevance: ${topic.relevance}}]->(t)
            """.trimIndent()
            kuzuDb.execute(relQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store topic ${topic.name} from note", e)
        }
    }

    private suspend fun storeTopicFromGoal(
        userId: String,
        topic: com.personalcoacher.data.remote.ExtractedTopic,
        goalId: String
    ) {
        val normalizedName = topic.name.lowercase().trim()
        val topicId = "topic_${userId}_$normalizedName".hashCode().toString()
        val now = Instant.now().toEpochMilli()

        try {
            // Use MERGE for upsert
            val mergeQuery = """
                MERGE (t:Topic {id: '$topicId'})
                ON CREATE SET t.userId = '$userId',
                              t.name = '${escapeString(topic.name)}',
                              t.normalizedName = '${escapeString(normalizedName)}',
                              t.category = ${topic.category?.let { "'$it'" } ?: "NULL"},
                              t.createdAt = $now,
                              t.mentionCount = 1
                ON MATCH SET t.mentionCount = t.mentionCount + 1
            """.trimIndent()
            kuzuDb.execute(mergeQuery)

            // Create GOAL_RELATES_TO_TOPIC relationship
            val relQuery = """
                MATCH (g:UserGoal {id: '$goalId'}), (t:Topic {id: '$topicId'})
                MERGE (g)-[:RELATES_TO_TOPIC {relevance: ${topic.relevance}}]->(t)
            """.trimIndent()
            kuzuDb.execute(relQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store topic ${topic.name} from goal", e)
        }
    }

    private suspend fun storeTopicFromTask(
        userId: String,
        topic: com.personalcoacher.data.remote.ExtractedTopic,
        taskId: String
    ) {
        val normalizedName = topic.name.lowercase().trim()
        val topicId = "topic_${userId}_$normalizedName".hashCode().toString()
        val now = Instant.now().toEpochMilli()

        try {
            // Use MERGE for upsert
            val mergeQuery = """
                MERGE (t:Topic {id: '$topicId'})
                ON CREATE SET t.userId = '$userId',
                              t.name = '${escapeString(topic.name)}',
                              t.normalizedName = '${escapeString(normalizedName)}',
                              t.category = ${topic.category?.let { "'$it'" } ?: "NULL"},
                              t.createdAt = $now,
                              t.mentionCount = 1
                ON MATCH SET t.mentionCount = t.mentionCount + 1
            """.trimIndent()
            kuzuDb.execute(mergeQuery)

            // Create TASK_RELATES_TO_TOPIC relationship
            val relQuery = """
                MATCH (u:UserTask {id: '$taskId'}), (t:Topic {id: '$topicId'})
                MERGE (u)-[:RELATES_TO_TOPIC {relevance: ${topic.relevance}}]->(t)
            """.trimIndent()
            kuzuDb.execute(relQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store topic ${topic.name} from task", e)
        }
    }

    private suspend fun storePerson(
        userId: String,
        person: com.personalcoacher.data.remote.ExtractedPerson,
        entryId: String,
        timestamp: Long
    ) {
        val normalizedName = person.name.lowercase().trim()
        val personId = "person_${userId}_$normalizedName".hashCode().toString()

        try {
            // Use MERGE for upsert
            val mergeQuery = """
                MERGE (p:Person {id: '$personId'})
                ON CREATE SET p.userId = '$userId',
                              p.name = '${escapeString(person.name)}',
                              p.normalizedName = '${escapeString(normalizedName)}',
                              p.relationship = ${person.relationship?.let { "'$it'" } ?: "NULL"},
                              p.firstMentioned = $timestamp,
                              p.lastMentioned = $timestamp,
                              p.mentionCount = 1
                ON MATCH SET p.lastMentioned = $timestamp,
                             p.mentionCount = p.mentionCount + 1
            """.trimIndent()
            kuzuDb.execute(mergeQuery)

            // Create MENTIONS_PERSON relationship
            val relQuery = """
                MATCH (j:JournalEntry {id: '$entryId'}), (p:Person {id: '$personId'})
                MERGE (j)-[:MENTIONS_PERSON {
                    mentionedAt: $timestamp,
                    sentiment: ${person.sentiment ?: 0f},
                    context: ''
                }]->(p)
            """.trimIndent()
            kuzuDb.execute(relQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store person ${person.name}", e)
        }
    }

    private suspend fun storeTopic(
        userId: String,
        topic: com.personalcoacher.data.remote.ExtractedTopic,
        entryId: String
    ) {
        val normalizedName = topic.name.lowercase().trim()
        val topicId = "topic_${userId}_$normalizedName".hashCode().toString()
        val now = Instant.now().toEpochMilli()

        try {
            // Use MERGE for upsert
            val mergeQuery = """
                MERGE (t:Topic {id: '$topicId'})
                ON CREATE SET t.userId = '$userId',
                              t.name = '${escapeString(topic.name)}',
                              t.normalizedName = '${escapeString(normalizedName)}',
                              t.category = ${topic.category?.let { "'$it'" } ?: "NULL"},
                              t.createdAt = $now,
                              t.mentionCount = 1
                ON MATCH SET t.mentionCount = t.mentionCount + 1
            """.trimIndent()
            kuzuDb.execute(mergeQuery)

            // Create RELATES_TO_TOPIC relationship
            val relQuery = """
                MATCH (j:JournalEntry {id: '$entryId'}), (t:Topic {id: '$topicId'})
                MERGE (j)-[:RELATES_TO_TOPIC {relevance: ${topic.relevance}}]->(t)
            """.trimIndent()
            kuzuDb.execute(relQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store topic ${topic.name}", e)
        }
    }

    // ==================== Deletion Sync ====================

    /**
     * Sync deletions: Remove nodes from GraphRAG that no longer exist in Room SQL.
     * This compares IDs from both databases and deletes orphaned nodes in Kuzu.
     */
    private suspend fun syncDeletions(userId: String): Int {
        var deletedCount = 0

        try {
            // Sync JournalEntry deletions
            deletedCount += syncNodeDeletions(
                nodeType = "JournalEntry",
                roomIds = journalEntryDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync ChatMessage deletions
            deletedCount += syncNodeDeletions(
                nodeType = "ChatMessage",
                roomIds = messageDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync AgendaItem deletions
            deletedCount += syncNodeDeletions(
                nodeType = "AgendaItem",
                roomIds = agendaItemDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync Summary deletions
            deletedCount += syncNodeDeletions(
                nodeType = "Summary",
                roomIds = summaryDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync DailyApp deletions (only LIKED apps are synced)
            deletedCount += syncNodeDeletions(
                nodeType = "DailyApp",
                roomIds = dailyAppDao.getAllSavedIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync Note deletions
            deletedCount += syncNodeDeletions(
                nodeType = "Note",
                roomIds = noteDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync UserGoal deletions
            deletedCount += syncNodeDeletions(
                nodeType = "UserGoal",
                roomIds = goalDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // Sync UserTask deletions
            deletedCount += syncNodeDeletions(
                nodeType = "UserTask",
                roomIds = taskDao.getAllIdsForUser(userId).toSet(),
                userId = userId
            )

            // When journal entries are deleted, their associated AtomicThoughts become orphaned
            // Clean up orphaned AtomicThoughts (those with no EXTRACTED_FROM relationship)
            deletedCount += cleanupOrphanedThoughts(userId)

            Log.d(TAG, "Deletion sync completed: $deletedCount nodes deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error during deletion sync", e)
        }

        return deletedCount
    }

    /**
     * Delete nodes from Kuzu that no longer exist in Room.
     */
    private suspend fun syncNodeDeletions(
        nodeType: String,
        roomIds: Set<String>,
        userId: String
    ): Int {
        var deletedCount = 0

        try {
            // Query all node IDs of this type from Kuzu for this user
            val kuzuQuery = """
                MATCH (n:$nodeType)
                WHERE n.userId = '$userId'
                RETURN n.id AS id
            """.trimIndent()

            val result = kuzuDb.execute(kuzuQuery)

            val kuzuIds = mutableSetOf<String>()
            while (result.hasNext()) {
                val tuple = result.getNext()
                val id = tuple.getValue(0)?.getValue<String>()
                if (id != null) {
                    kuzuIds.add(id)
                }
            }

            // Find IDs that exist in Kuzu but not in Room (orphaned nodes)
            val orphanedIds = kuzuIds - roomIds

            // Delete each orphaned node
            for (orphanedId in orphanedIds) {
                try {
                    val deleteQuery = """
                        MATCH (n:$nodeType {id: '$orphanedId'})
                        DETACH DELETE n
                    """.trimIndent()
                    kuzuDb.execute(deleteQuery)
                    deletedCount++
                    Log.d(TAG, "Deleted orphaned $nodeType node: $orphanedId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete orphaned $nodeType node: $orphanedId", e)
                }
            }

            if (orphanedIds.isNotEmpty()) {
                Log.d(TAG, "Deleted ${orphanedIds.size} orphaned $nodeType nodes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing $nodeType deletions", e)
        }

        return deletedCount
    }

    /**
     * Clean up AtomicThoughts that no longer have a parent JournalEntry.
     */
    private suspend fun cleanupOrphanedThoughts(userId: String): Int {
        var deletedCount = 0

        try {
            // Find AtomicThoughts without EXTRACTED_FROM relationship to any JournalEntry
            val orphanedQuery = """
                MATCH (t:AtomicThought)
                WHERE t.userId = '$userId'
                  AND NOT EXISTS { MATCH (t)-[:EXTRACTED_FROM]->(:JournalEntry) }
                RETURN t.id AS id
            """.trimIndent()

            val result = kuzuDb.execute(orphanedQuery)

            val orphanedIds = mutableListOf<String>()
            while (result.hasNext()) {
                val tuple = result.getNext()
                val id = tuple.getValue(0)?.getValue<String>()
                if (id != null) {
                    orphanedIds.add(id)
                }
            }

            // Delete orphaned thoughts
            for (orphanedId in orphanedIds) {
                try {
                    val deleteQuery = """
                        MATCH (t:AtomicThought {id: '$orphanedId'})
                        DETACH DELETE t
                    """.trimIndent()
                    kuzuDb.execute(deleteQuery)
                    deletedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete orphaned AtomicThought: $orphanedId", e)
                }
            }

            if (orphanedIds.isNotEmpty()) {
                Log.d(TAG, "Deleted ${orphanedIds.size} orphaned AtomicThoughts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up orphaned thoughts", e)
        }

        return deletedCount
    }

    // ==================== Utility Methods ====================

    private fun escapeString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

// State classes

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data class Completed(val stats: SyncStats) : SyncState()
    data class Failed(val error: String) : SyncState()
}

data class SyncStats(
    val journalEntries: Int = 0,
    val chatMessages: Int = 0,
    val agendaItems: Int = 0,
    val summaries: Int = 0,
    val dailyApps: Int = 0,
    val notes: Int = 0,
    val userGoals: Int = 0,
    val userTasks: Int = 0,
    val atomicThoughts: Int = 0,
    val relationships: Int = 0,
    val deletedNodes: Int = 0
) {
    val totalNodes: Int get() = journalEntries + chatMessages + agendaItems + summaries + dailyApps + notes + userGoals + userTasks + atomicThoughts
    val isEmpty: Boolean get() = totalNodes == 0 && relationships == 0 && deletedNodes == 0
}
