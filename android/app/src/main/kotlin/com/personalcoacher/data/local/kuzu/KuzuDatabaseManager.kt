package com.personalcoacher.data.local.kuzu

import android.content.Context
import com.kuzudb.Connection
import com.kuzudb.Database
import com.kuzudb.QueryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Kuzu graph database for the RAG (Retrieval-Augmented Generation) system.
 *
 * Kuzu is an embedded graph database that supports:
 * - Vector search (HNSW index) for semantic similarity
 * - Full-text search (BM25) for keyword matching
 * - Graph traversal (Cypher) for relationship queries
 *
 * All user data remains on-device. Embeddings are generated via Voyage AI (stateless cloud)
 * and stored locally in Kuzu.
 */
@Singleton
class KuzuDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DATABASE_DIR = "kuzu_rag_db"
        const val EMBEDDING_DIMENSIONS = 1024
    }

    private var database: Database? = null
    private var connection: Connection? = null
    private val mutex = Mutex()

    /**
     * Initialize the Kuzu database and create schema if needed.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (database != null) return@withLock

            val dbPath = File(context.filesDir, DATABASE_DIR).absolutePath
            database = Database(dbPath)
            connection = Connection(database)

            // Create schema
            createSchema()
        }
    }

    /**
     * Execute a Cypher query and return the result.
     */
    suspend fun execute(query: String): QueryResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: throw IllegalStateException("Database not initialized")
            conn.query(query)
        }
    }

    /**
     * Close the database connection.
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.destroy()
            database?.destroy()
            connection = null
            database = null
        }
    }

    /**
     * Check if the database is initialized.
     */
    fun isInitialized(): Boolean = database != null

    /**
     * Delete the database files (for testing or reset).
     */
    suspend fun deleteDatabase() = withContext(Dispatchers.IO) {
        mutex.withLock {
            close()
            val dbDir = File(context.filesDir, DATABASE_DIR)
            if (dbDir.exists()) {
                dbDir.deleteRecursively()
            }
        }
    }

    private fun createSchema() {
        val conn = connection ?: return

        // ============================================
        // NODE TABLES
        // ============================================

        // Journal entries - the raw user input with embeddings
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS JournalEntry(
                id STRING PRIMARY KEY,
                userId STRING,
                content STRING,
                mood STRING,
                tags STRING,
                date INT64,
                createdAt INT64,
                updatedAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // Atomic thoughts - extracted concepts from journal entries
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS AtomicThought(
                id STRING PRIMARY KEY,
                userId STRING,
                content STRING,
                thoughtType STRING,
                confidence FLOAT,
                sentiment FLOAT,
                importance INT8,
                createdAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // Chat messages - conversation history with embeddings
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS ChatMessage(
                id STRING PRIMARY KEY,
                conversationId STRING,
                userId STRING,
                role STRING,
                content STRING,
                createdAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // People mentioned in entries
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS Person(
                id STRING PRIMARY KEY,
                userId STRING,
                name STRING,
                normalizedName STRING,
                relationship STRING,
                firstMentioned INT64,
                lastMentioned INT64,
                mentionCount INT32
            )
        """.trimIndent())

        // Topics/themes that emerge from entries
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS Topic(
                id STRING PRIMARY KEY,
                userId STRING,
                name STRING,
                normalizedName STRING,
                category STRING,
                createdAt INT64,
                mentionCount INT32
            )
        """.trimIndent())

        // Goals the user has expressed
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS Goal(
                id STRING PRIMARY KEY,
                userId STRING,
                description STRING,
                status STRING,
                createdAt INT64,
                updatedAt INT64,
                targetDate INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // Agenda/Calendar items
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS AgendaItem(
                id STRING PRIMARY KEY,
                userId STRING,
                title STRING,
                description STRING,
                startTime INT64,
                endTime INT64,
                isAllDay BOOLEAN,
                location STRING,
                createdAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // Summaries (AI-generated)
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS Summary(
                id STRING PRIMARY KEY,
                userId STRING,
                summaryType STRING,
                content STRING,
                periodStart INT64,
                periodEnd INT64,
                createdAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // Daily Apps (AI-generated web apps)
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS DailyApp(
                id STRING PRIMARY KEY,
                userId STRING,
                date INT64,
                title STRING,
                description STRING,
                htmlCode STRING,
                journalContext STRING,
                status STRING,
                usedAt INT64,
                createdAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // ============================================
        // RELATIONSHIP TABLES
        // ============================================

        // Atomic thoughts extracted from journal entries
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM(
                FROM AtomicThought TO JournalEntry,
                extractedAt INT64,
                confidence FLOAT
            )
        """.trimIndent())

        // Thoughts related to other thoughts
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS RELATES_TO(
                FROM AtomicThought TO AtomicThought,
                relationType STRING,
                strength FLOAT
            )
        """.trimIndent())

        // Journal entry mentions a person
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS MENTIONS_PERSON(
                FROM JournalEntry TO Person,
                mentionedAt INT64,
                sentiment FLOAT,
                context STRING
            )
        """.trimIndent())

        // Journal entry relates to a topic
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS RELATES_TO_TOPIC(
                FROM JournalEntry TO Topic,
                relevance FLOAT
            )
        """.trimIndent())

        // Atomic thought relates to a topic
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS THOUGHT_TOPIC(
                FROM AtomicThought TO Topic,
                relevance FLOAT
            )
        """.trimIndent())

        // Thought relates to a goal
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS SUPPORTS_GOAL(
                FROM AtomicThought TO Goal,
                supportType STRING,
                createdAt INT64
            )
        """.trimIndent())

        // Journal entry tracks goal progress
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS TRACKS_GOAL(
                FROM JournalEntry TO Goal,
                progressNote STRING,
                createdAt INT64
            )
        """.trimIndent())

        // Daily app inspired by journal entries
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS APP_INSPIRED_BY(
                FROM DailyApp TO JournalEntry,
                relevance FLOAT
            )
        """.trimIndent())

        // Summary covers journal entries
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS SUMMARIZES(
                FROM Summary TO JournalEntry,
                weight FLOAT
            )
        """.trimIndent())
    }
}
