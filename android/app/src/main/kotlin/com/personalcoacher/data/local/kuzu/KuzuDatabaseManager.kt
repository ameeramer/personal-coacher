package com.personalcoacher.data.local.kuzu

import android.content.Context
import android.net.Uri
import com.kuzudb.Connection
import com.kuzudb.Database
import com.kuzudb.QueryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
        // Note: Kuzu v0.11.0+ uses single-file format (like SQLite), not a directory
        private const val DATABASE_FILE = "kuzu_rag.db"
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

            val dbFile = getDatabaseFile()
            android.util.Log.d("KuzuDatabaseManager", "Initializing database at: ${dbFile.absolutePath}")
            database = Database(dbFile.absolutePath)
            connection = Connection(database)

            // Create schema
            createSchema()
            android.util.Log.d("KuzuDatabaseManager", "Database initialized, file exists: ${dbFile.exists()}, size: ${dbFile.length()} bytes")
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
     * Force a checkpoint to flush all WAL data to the main database files.
     * This ensures data durability and should be called before export or after major migrations.
     */
    suspend fun checkpoint() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: throw IllegalStateException("Database not initialized")
            conn.query("CHECKPOINT")
            android.util.Log.d("KuzuDatabaseManager", "Checkpoint completed - WAL data flushed to disk")
        }
    }

    /**
     * Close the database connection.
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.close()
            database?.close()
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
            val dbFile = getDatabaseFile()
            if (dbFile.exists()) {
                dbFile.delete()
            }
            // Also delete any WAL or lock files that Kuzu may have created
            val walFile = File(context.filesDir, "$DATABASE_FILE.wal")
            val lockFile = File(context.filesDir, "$DATABASE_FILE.lock")
            walFile.delete()
            lockFile.delete()
        }
    }

    /**
     * Get the database file (single file format in Kuzu v0.11.0+).
     */
    fun getDatabaseFile(): File = File(context.filesDir, DATABASE_FILE)

    /**
     * Get the path to the database directory (for backward compatibility).
     * @deprecated Use getDatabaseFile() instead
     */
    @Deprecated("Kuzu v0.11.0+ uses single-file format. Use getDatabaseFile() instead.")
    fun getDatabasePath(): File = getDatabaseFile()

    /**
     * Check if the database exists (synchronous version for init blocks).
     * Note: This may not be reliable due to threading issues with file system access.
     * Prefer using databaseExistsAsync() when possible.
     */
    fun databaseExists(): Boolean {
        val dbFile = getDatabaseFile()
        val exists = dbFile.exists() && dbFile.isFile && dbFile.length() > 0
        return exists
    }

    /**
     * Check if the database exists (async version that runs on IO dispatcher).
     * This is more reliable than the synchronous version.
     */
    suspend fun databaseExistsAsync(): Boolean = withContext(Dispatchers.IO) {
        val dbFile = getDatabaseFile()
        val exists = dbFile.exists()
        val isFile = dbFile.isFile
        val size = dbFile.length()
        val result = exists && isFile && size > 0
        android.util.Log.d("KuzuDatabaseManager", "databaseExistsAsync: path=${dbFile.absolutePath}, exists=$exists, isFile=$isFile, size=$size bytes, result=$result")
        result
    }

    /**
     * Export the database to a zip file at the specified URI using Kuzu's native EXPORT DATABASE.
     *
     * Uses Kuzu's built-in export mechanism which creates portable CSV/Parquet files
     * and Cypher schema files. This is more reliable than raw file copy because:
     * - Handles WAL data properly
     * - Creates platform-independent exports
     * - Works reliably across different Kuzu versions
     *
     * @param outputUri The URI to write the backup zip file to
     * @return Result indicating success or failure
     */
    suspend fun exportToUri(outputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val conn = connection ?: throw IllegalStateException("Database not initialized")

                // Create a temporary directory for the export
                val exportDir = File(context.cacheDir, "kuzu_export_${System.currentTimeMillis()}")
                if (exportDir.exists()) {
                    exportDir.deleteRecursively()
                }
                exportDir.mkdirs()

                android.util.Log.d("KuzuDatabaseManager", "Exporting database to: ${exportDir.absolutePath}")

                // Use Kuzu's native EXPORT DATABASE command (exports to CSV by default)
                // This properly handles all WAL data and creates a portable format
                try {
                    conn.query("EXPORT DATABASE '${exportDir.absolutePath}'")
                    android.util.Log.d("KuzuDatabaseManager", "EXPORT DATABASE command completed")
                } catch (e: Exception) {
                    exportDir.deleteRecursively()
                    throw IllegalStateException("EXPORT DATABASE failed: ${e.message}", e)
                }

                // Verify export created files
                val exportedFiles = exportDir.listFiles() ?: emptyArray()
                if (exportedFiles.isEmpty()) {
                    exportDir.deleteRecursively()
                    throw IllegalStateException("EXPORT DATABASE created no files")
                }

                android.util.Log.d("KuzuDatabaseManager", "Export created ${exportedFiles.size} files/dirs")

                // Zip all exported files
                var filesZipped = 0
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        zipDirectory(exportDir, exportDir, zipOut) { count ->
                            filesZipped = count
                        }
                    }
                } ?: throw IllegalStateException("Could not open output stream")

                android.util.Log.d("KuzuDatabaseManager", "Export completed: $filesZipped files zipped")

                // Clean up temporary export directory
                exportDir.deleteRecursively()

                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("KuzuDatabaseManager", "Export failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Recursively zip a directory and its contents.
     */
    private fun zipDirectory(
        rootDir: File,
        currentDir: File,
        zipOut: ZipOutputStream,
        countCallback: (Int) -> Unit
    ) {
        var count = 0
        currentDir.listFiles()?.forEach { file ->
            val relativePath = file.relativeTo(rootDir).path
            if (file.isDirectory) {
                // Add directory entry
                zipOut.putNextEntry(ZipEntry("$relativePath/"))
                zipOut.closeEntry()
                // Recursively add directory contents
                file.listFiles()?.forEach { child ->
                    zipFileOrDir(rootDir, child, zipOut) { count++ }
                }
            } else {
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(relativePath)
                    zipOut.putNextEntry(zipEntry)
                    val bytesCopied = fis.copyTo(zipOut)
                    zipOut.closeEntry()
                    android.util.Log.d("KuzuDatabaseManager", "Zipped: $relativePath ($bytesCopied bytes)")
                    count++
                }
            }
        }
        countCallback(count)
    }

    /**
     * Zip a single file or directory recursively.
     */
    private fun zipFileOrDir(
        rootDir: File,
        file: File,
        zipOut: ZipOutputStream,
        countCallback: () -> Unit
    ) {
        val relativePath = file.relativeTo(rootDir).path
        if (file.isDirectory) {
            zipOut.putNextEntry(ZipEntry("$relativePath/"))
            zipOut.closeEntry()
            file.listFiles()?.forEach { child ->
                zipFileOrDir(rootDir, child, zipOut, countCallback)
            }
        } else {
            FileInputStream(file).use { fis ->
                val zipEntry = ZipEntry(relativePath)
                zipOut.putNextEntry(zipEntry)
                val bytesCopied = fis.copyTo(zipOut)
                zipOut.closeEntry()
                android.util.Log.d("KuzuDatabaseManager", "Zipped: $relativePath ($bytesCopied bytes)")
                countCallback()
            }
        }
    }

    /**
     * Import the database from a zip file at the specified URI using Kuzu's native IMPORT DATABASE.
     *
     * The zip file should contain files created by EXPORT DATABASE (schema.cypher, copy.cypher,
     * and data files). This creates a new database and imports the data.
     *
     * @param inputUri The URI to read the backup zip file from
     * @return Result indicating success or failure
     */
    suspend fun importFromUri(inputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Close the existing database
                connection?.close()
                database?.close()
                connection = null
                database = null

                val dbFile = getDatabaseFile()

                // Delete existing database file and any WAL/lock files
                if (dbFile.exists()) {
                    dbFile.delete()
                }
                File(context.filesDir, "$DATABASE_FILE.wal").delete()
                File(context.filesDir, "$DATABASE_FILE.lock").delete()

                // Create a temporary directory to extract the zip
                val importDir = File(context.cacheDir, "kuzu_import_${System.currentTimeMillis()}")
                if (importDir.exists()) {
                    importDir.deleteRecursively()
                }
                importDir.mkdirs()

                // Extract zip contents to temp directory
                var filesExtracted = 0
                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val destFile = File(importDir, entry.name)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { fos ->
                                    val bytesCopied = zipIn.copyTo(fos)
                                    android.util.Log.d("KuzuDatabaseManager", "Extracted: ${entry!!.name} ($bytesCopied bytes)")
                                }
                                filesExtracted++
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                } ?: run {
                    importDir.deleteRecursively()
                    return@withContext Result.failure(IllegalStateException("Could not open input stream"))
                }

                android.util.Log.d("KuzuDatabaseManager", "Extracted $filesExtracted files to ${importDir.absolutePath}")

                // Check if this is a native export (has schema.cypher) or raw file export
                val schemaFile = File(importDir, "schema.cypher")
                val rawDbFile = File(importDir, DATABASE_FILE)

                if (schemaFile.exists()) {
                    // Native EXPORT DATABASE format - use IMPORT DATABASE
                    android.util.Log.d("KuzuDatabaseManager", "Detected native export format, using IMPORT DATABASE")

                    // Create a fresh database
                    database = Database(dbFile.absolutePath)
                    connection = Connection(database)

                    // Use IMPORT DATABASE to restore
                    try {
                        connection!!.query("IMPORT DATABASE '${importDir.absolutePath}'")
                        android.util.Log.d("KuzuDatabaseManager", "IMPORT DATABASE command completed")
                    } catch (e: Exception) {
                        connection?.close()
                        database?.close()
                        connection = null
                        database = null
                        dbFile.delete()
                        importDir.deleteRecursively()
                        throw IllegalStateException("IMPORT DATABASE failed: ${e.message}", e)
                    }
                } else if (rawDbFile.exists()) {
                    // Legacy raw file export - copy directly (for backward compatibility)
                    android.util.Log.d("KuzuDatabaseManager", "Detected legacy raw file format, copying directly")
                    rawDbFile.copyTo(dbFile, overwrite = true)

                    // Also copy WAL file if present
                    val rawWalFile = File(importDir, "$DATABASE_FILE.wal")
                    if (rawWalFile.exists()) {
                        rawWalFile.copyTo(File(context.filesDir, "$DATABASE_FILE.wal"), overwrite = true)
                    }

                    // Initialize the database
                    database = Database(dbFile.absolutePath)
                    connection = Connection(database)
                } else {
                    importDir.deleteRecursively()
                    return@withContext Result.failure(IllegalStateException("Invalid backup: no schema.cypher or $DATABASE_FILE found"))
                }

                // Clean up temp directory
                importDir.deleteRecursively()

                android.util.Log.d("KuzuDatabaseManager", "Import completed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("KuzuDatabaseManager", "Import failed", e)
                // Try to clean up
                try {
                    val dbFile = getDatabaseFile()
                    if (dbFile.exists()) {
                        dbFile.delete()
                    }
                } catch (_: Exception) {}

                Result.failure(e)
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
