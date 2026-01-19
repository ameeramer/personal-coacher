package com.personalcoacher.data.local.kuzu

import android.util.Log
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.local.entity.DailyAppEntity
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
    private val dailyAppDao: DailyAppDao
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
                atomicThoughts = thoughtCount,
                relationships = relationshipCount
            )

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

        Log.d(TAG, "Syncing ${modifiedApps.size} daily apps modified since $lastSync")

        var syncedCount = 0
        var relationshipCount = 0
        var maxTimestamp = lastSync

        for (app in modifiedApps) {
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

                // Create APP_INSPIRED_BY relationships to journal entries from same day
                relationshipCount += createDailyAppRelationships(app)

                if (app.updatedAt > maxTimestamp) {
                    maxTimestamp = app.updatedAt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync daily app ${app.id}", e)
            }
        }

        // Update timestamp
        tokenManager.setLastDailyAppSyncTimestamp(maxTimestamp)

        return Pair(syncedCount, relationshipCount)
    }

    // ==================== Node Upsert Methods (MERGE) ====================

    private suspend fun upsertJournalEntryNode(entry: JournalEntryEntity, embedding: List<Float>?) {
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

    private suspend fun upsertChatMessageNode(message: MessageEntity, userId: String, embedding: List<Float>?) {
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

    private suspend fun upsertAgendaItemNode(item: AgendaItemEntity, embedding: List<Float>?) {
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

    private suspend fun upsertSummaryNode(summary: SummaryEntity, embedding: List<Float>?) {
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

    private suspend fun upsertDailyAppNode(app: DailyAppEntity, embedding: List<Float>?) {
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
            val dayStart = app.date - (app.date % (24 * 60 * 60 * 1000))
            val dayEnd = dayStart + (24 * 60 * 60 * 1000) - 1

            val query = """
                MATCH (d:DailyApp {id: '${app.id}'}), (j:JournalEntry)
                WHERE j.userId = '${app.userId}'
                  AND j.date >= $dayStart
                  AND j.date <= $dayEnd
                MERGE (d)-[:APP_INSPIRED_BY {relevance: 1.0}]->(j)
            """.trimIndent()
            kuzuDb.execute(query)
            1
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create APP_INSPIRED_BY relationships for daily app ${app.id}", e)
            0
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
    val atomicThoughts: Int = 0,
    val relationships: Int = 0
) {
    val totalNodes: Int get() = journalEntries + chatMessages + agendaItems + summaries + dailyApps + atomicThoughts
    val isEmpty: Boolean get() = totalNodes == 0 && relationships == 0
}
