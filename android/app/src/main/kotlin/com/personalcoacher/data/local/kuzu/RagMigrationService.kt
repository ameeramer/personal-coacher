package com.personalcoacher.data.local.kuzu

import android.util.Log
import com.kuzudb.FlatTuple
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.SummaryDao
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
 * RAG Migration Service
 *
 * Handles the one-time migration from Room database to Kuzu knowledge graph.
 * This is a user-triggered migration via a button in Settings.
 *
 * Migration steps:
 * 1. Initialize Kuzu database with schema
 * 2. Migrate journal entries with embeddings
 * 3. Migrate chat messages
 * 4. Migrate agenda items
 * 5. Migrate summaries
 * 6. Migrate daily apps
 * 7. Extract atomic thoughts from journal entries
 * 8. Build entity relationships (people, topics)
 * 9. Mark migration as complete
 */
@Singleton
class RagMigrationService @Inject constructor(
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
        private const val TAG = "RagMigrationService"
    }

    private val _migrationState = MutableStateFlow<MigrationState>(MigrationState.NotStarted)
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()

    /**
     * Check if migration has already been completed.
     */
    fun isMigrationComplete(): Boolean = tokenManager.getRagMigrationCompleteSync()

    /**
     * Start the migration process.
     */
    suspend fun startMigration(userId: String) = withContext(Dispatchers.IO) {
        if (isMigrationComplete()) {
            _migrationState.value = MigrationState.Completed(MigrationStats())
            return@withContext
        }

        try {
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.INITIALIZING,
                progress = 0f,
                message = "Initializing Kuzu database..."
            )

            // Step 1: Initialize Kuzu
            kuzuDb.initialize()
            Log.d(TAG, "Kuzu database initialized")

            // Step 2: Migrate journal entries
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.JOURNAL_ENTRIES,
                progress = 0.1f,
                message = "Migrating journal entries..."
            )
            val journalCount = migrateJournalEntries(userId)
            Log.d(TAG, "Migrated $journalCount journal entries")

            // Step 3: Migrate chat messages
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.CHAT_MESSAGES,
                progress = 0.3f,
                message = "Migrating chat messages..."
            )
            val messageCount = migrateChatMessages(userId)
            Log.d(TAG, "Migrated $messageCount chat messages")

            // Step 4: Migrate agenda items
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.AGENDA_ITEMS,
                progress = 0.4f,
                message = "Migrating agenda items..."
            )
            val agendaCount = migrateAgendaItems(userId)
            Log.d(TAG, "Migrated $agendaCount agenda items")

            // Step 5: Migrate summaries
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.SUMMARIES,
                progress = 0.5f,
                message = "Migrating summaries..."
            )
            val summaryCount = migrateSummaries(userId)
            Log.d(TAG, "Migrated $summaryCount summaries")

            // Step 6: Migrate daily apps
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.DAILY_APPS,
                progress = 0.6f,
                message = "Migrating daily apps..."
            )
            val dailyAppCount = migrateDailyApps(userId)
            Log.d(TAG, "Migrated $dailyAppCount daily apps")

            // Step 7: Extract atomic thoughts
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.ATOMIC_THOUGHTS,
                progress = 0.7f,
                message = "Extracting atomic thoughts..."
            )
            val thoughtCount = extractAtomicThoughts(userId)
            Log.d(TAG, "Extracted $thoughtCount atomic thoughts")

            // Step 8: Build knowledge graph
            _migrationState.value = MigrationState.InProgress(
                step = MigrationStep.BUILDING_GRAPH,
                progress = 0.9f,
                message = "Building knowledge graph..."
            )
            val connectionCount = buildKnowledgeGraph(userId)
            Log.d(TAG, "Created $connectionCount graph connections")

            // Step 9: Mark migration as complete
            tokenManager.setRagMigrationComplete(true)

            val stats = MigrationStats(
                journalEntries = journalCount,
                chatMessages = messageCount,
                agendaItems = agendaCount,
                summaries = summaryCount,
                dailyApps = dailyAppCount,
                atomicThoughts = thoughtCount,
                graphConnections = connectionCount
            )

            _migrationState.value = MigrationState.Completed(stats)
            Log.d(TAG, "Migration completed successfully: $stats")

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            _migrationState.value = MigrationState.Failed(e.message ?: "Unknown error")
        }
    }

    private suspend fun migrateJournalEntries(userId: String): Int {
        val entries = journalEntryDao.getEntriesForUserSync(userId)
        var count = 0

        for (entry in entries) {
            try {
                // Generate embedding for journal content
                val embedding = try {
                    voyageService.embed(entry.content)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for entry ${entry.id}", e)
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                val query = """
                    CREATE (j:JournalEntry {
                        id: '${entry.id}',
                        userId: '${entry.userId}',
                        content: '${escapeString(entry.content)}',
                        mood: ${entry.mood?.let { "'$it'" } ?: "NULL"},
                        tags: ${entry.tags?.let { "'$it'" } ?: "NULL"},
                        date: ${entry.date},
                        createdAt: ${entry.createdAt},
                        updatedAt: ${entry.updatedAt},
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()

                kuzuDb.execute(query)
                count++

                // Update progress
                val progress = 0.1f + (count.toFloat() / entries.size) * 0.2f
                _migrationState.value = MigrationState.InProgress(
                    step = MigrationStep.JOURNAL_ENTRIES,
                    progress = progress,
                    message = "Migrating journal entries ($count/${entries.size})..."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate journal entry ${entry.id}", e)
            }
        }

        return count
    }

    private suspend fun migrateChatMessages(userId: String): Int {
        val conversations = conversationDao.getConversationsForUserSync(userId)
        var count = 0

        for (conversation in conversations) {
            val messages = messageDao.getMessagesForConversationSync(conversation.id)

            for (message in messages) {
                try {
                    // Only embed user messages for retrieval
                    val embedding = if (message.role.lowercase() == "user") {
                        try {
                            voyageService.embed(message.content)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to generate embedding for message ${message.id}", e)
                            null
                        }
                    } else null

                    val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                    val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                    val query = """
                        CREATE (m:ChatMessage {
                            id: '${message.id}',
                            conversationId: '${message.conversationId}',
                            userId: '${conversation.userId}',
                            role: '${message.role}',
                            content: '${escapeString(message.content)}',
                            createdAt: ${message.createdAt},
                            embedding: $embeddingStr,
                            embeddingModel: $modelVersion
                        })
                    """.trimIndent()

                    kuzuDb.execute(query)
                    count++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate message ${message.id}", e)
                }
            }
        }

        return count
    }

    private suspend fun migrateAgendaItems(userId: String): Int {
        val items = agendaItemDao.getItemsForUserSync(userId)
        var count = 0

        for (item in items) {
            try {
                val textToEmbed = "${item.title} ${item.description ?: ""}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for agenda item ${item.id}", e)
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                val query = """
                    CREATE (a:AgendaItem {
                        id: '${item.id}',
                        userId: '${item.userId}',
                        title: '${escapeString(item.title)}',
                        description: ${item.description?.let { "'${escapeString(it)}'" } ?: "NULL"},
                        startTime: ${item.startTime},
                        endTime: ${item.endTime ?: "NULL"},
                        isAllDay: ${item.isAllDay},
                        location: ${item.location?.let { "'${escapeString(it)}'" } ?: "NULL"},
                        createdAt: ${item.createdAt},
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()

                kuzuDb.execute(query)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate agenda item ${item.id}", e)
            }
        }

        return count
    }

    private suspend fun migrateSummaries(userId: String): Int {
        val summaries = summaryDao.getSummariesForUserSync(userId)
        var count = 0

        for (summary in summaries) {
            try {
                val embedding = try {
                    voyageService.embed(summary.content)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for summary ${summary.id}", e)
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                val query = """
                    CREATE (s:Summary {
                        id: '${summary.id}',
                        userId: '${summary.userId}',
                        summaryType: '${summary.type}',
                        content: '${escapeString(summary.content)}',
                        periodStart: ${summary.startDate},
                        periodEnd: ${summary.endDate},
                        createdAt: ${summary.createdAt},
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()

                kuzuDb.execute(query)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate summary ${summary.id}", e)
            }
        }

        return count
    }

    private suspend fun migrateDailyApps(userId: String): Int {
        val apps = dailyAppDao.getAppsForUserSync(userId)
        var count = 0

        for (app in apps) {
            try {
                val textToEmbed = "${app.title} ${app.description}"
                val embedding = try {
                    voyageService.embed(textToEmbed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate embedding for daily app ${app.id}", e)
                    null
                }

                val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" } ?: "NULL"
                val modelVersion = if (embedding != null) "'${VoyageEmbeddingService.MODEL_VERSION}'" else "NULL"

                val query = """
                    CREATE (d:DailyApp {
                        id: '${app.id}',
                        userId: '${app.userId}',
                        date: ${app.date},
                        title: '${escapeString(app.title)}',
                        description: '${escapeString(app.description)}',
                        htmlCode: '${escapeString(app.htmlCode)}',
                        journalContext: ${app.journalContext?.let { "'${escapeString(it)}'" } ?: "NULL"},
                        status: '${app.status}',
                        usedAt: ${app.usedAt ?: "NULL"},
                        createdAt: ${app.createdAt},
                        embedding: $embeddingStr,
                        embeddingModel: $modelVersion
                    })
                """.trimIndent()

                kuzuDb.execute(query)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate daily app ${app.id}", e)
            }
        }

        return count
    }

    private suspend fun extractAtomicThoughts(userId: String): Int {
        // Check if Claude API key is configured for extraction
        if (!tokenManager.hasClaudeApiKey()) {
            Log.w(TAG, "Claude API key not configured, skipping atomic thought extraction")
            return 0
        }

        val entries = journalEntryDao.getEntriesForUserSync(userId)
        var count = 0
        val now = Instant.now().toEpochMilli()

        // Limit extraction to recent entries to avoid API rate limits
        val recentEntries = entries.take(20)

        for ((index, entry) in recentEntries.withIndex()) {
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

                    val thoughtQuery = """
                        CREATE (t:AtomicThought {
                            id: '$thoughtId',
                            userId: '$userId',
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
                    count++
                }

                // Store extracted people
                for (person in extractionResult.people) {
                    storePerson(userId, person, entry.id, now)
                }

                // Store extracted topics
                for (topic in extractionResult.topics) {
                    storeTopic(userId, topic, entry.id)
                }

                // Update progress
                val progress = 0.7f + (index.toFloat() / recentEntries.size) * 0.2f
                _migrationState.value = MigrationState.InProgress(
                    step = MigrationStep.ATOMIC_THOUGHTS,
                    progress = progress,
                    message = "Extracting atomic thoughts (${index + 1}/${recentEntries.size})..."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract thoughts from entry ${entry.id}", e)
            }
        }

        return count
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
            // Check if person already exists
            val existsQuery = "MATCH (p:Person {id: '$personId'}) RETURN p"
            val existsResult = kuzuDb.execute(existsQuery)
            val exists = existsResult.hasNext()

            if (!exists) {
                val createQuery = """
                    CREATE (p:Person {
                        id: '$personId',
                        userId: '$userId',
                        name: '${escapeString(person.name)}',
                        normalizedName: '${escapeString(normalizedName)}',
                        relationship: ${person.relationship?.let { "'$it'" } ?: "NULL"},
                        firstMentioned: $timestamp,
                        lastMentioned: $timestamp,
                        mentionCount: 1
                    })
                """.trimIndent()
                kuzuDb.execute(createQuery)
            } else {
                // Update existing person
                val updateQuery = """
                    MATCH (p:Person {id: '$personId'})
                    SET p.lastMentioned = $timestamp, p.mentionCount = p.mentionCount + 1
                """.trimIndent()
                kuzuDb.execute(updateQuery)
            }

            // Create MENTIONS_PERSON relationship
            val relQuery = """
                MATCH (j:JournalEntry {id: '$entryId'}), (p:Person {id: '$personId'})
                CREATE (j)-[:MENTIONS_PERSON {
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
            // Check if topic already exists
            val existsQuery = "MATCH (t:Topic {id: '$topicId'}) RETURN t"
            val existsResult = kuzuDb.execute(existsQuery)
            val exists = existsResult.hasNext()

            if (!exists) {
                val createQuery = """
                    CREATE (t:Topic {
                        id: '$topicId',
                        userId: '$userId',
                        name: '${escapeString(topic.name)}',
                        normalizedName: '${escapeString(normalizedName)}',
                        category: ${topic.category?.let { "'$it'" } ?: "NULL"},
                        createdAt: $now,
                        mentionCount: 1
                    })
                """.trimIndent()
                kuzuDb.execute(createQuery)
            } else {
                // Update existing topic
                val updateQuery = """
                    MATCH (t:Topic {id: '$topicId'})
                    SET t.mentionCount = t.mentionCount + 1
                """.trimIndent()
                kuzuDb.execute(updateQuery)
            }

            // Create RELATES_TO_TOPIC relationship
            val relQuery = """
                MATCH (j:JournalEntry {id: '$entryId'}), (t:Topic {id: '$topicId'})
                CREATE (j)-[:RELATES_TO_TOPIC {relevance: ${topic.relevance}}]->(t)
            """.trimIndent()
            kuzuDb.execute(relQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store topic ${topic.name}", e)
        }
    }

    private suspend fun buildKnowledgeGraph(userId: String): Int {
        // This method builds additional relationships between nodes
        // For now, we count the existing relationships
        var connectionCount = 0

        try {
            // Count all relationships
            val countQuery = """
                MATCH ()-[r]->()
                RETURN COUNT(r) as count
            """.trimIndent()
            val result = kuzuDb.execute(countQuery)
            if (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                connectionCount = row.getValue(0).getValue<Long>().toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count graph connections", e)
        }

        return connectionCount
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

sealed class MigrationState {
    object NotStarted : MigrationState()

    data class InProgress(
        val step: MigrationStep,
        val progress: Float,
        val message: String
    ) : MigrationState()

    data class Completed(val stats: MigrationStats) : MigrationState()

    data class Failed(val error: String) : MigrationState()
}

enum class MigrationStep {
    INITIALIZING,
    JOURNAL_ENTRIES,
    CHAT_MESSAGES,
    AGENDA_ITEMS,
    SUMMARIES,
    DAILY_APPS,
    ATOMIC_THOUGHTS,
    BUILDING_GRAPH
}

data class MigrationStats(
    val journalEntries: Int = 0,
    val chatMessages: Int = 0,
    val agendaItems: Int = 0,
    val summaries: Int = 0,
    val dailyApps: Int = 0,
    val atomicThoughts: Int = 0,
    val graphConnections: Int = 0
)
