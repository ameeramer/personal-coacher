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
     * Export result containing success status and logs for debugging.
     */
    data class ExportResult(
        val success: Boolean,
        val logs: String,
        val error: String? = null
    )

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
     * @return ExportResult containing success status, logs, and optional error message
     */
    suspend fun exportToUri(outputUri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val logs = StringBuilder()
        logs.appendLine("=== KUZU EXPORT LOG ===")
        logs.appendLine("Time: ${java.time.LocalDateTime.now()}")

        mutex.withLock {
            try {
                val conn = connection
                if (conn == null) {
                    logs.appendLine("ERROR: Database not initialized")
                    return@withContext ExportResult(success = false, logs = logs.toString(), error = "Database not initialized")
                }
                logs.appendLine("Database connection: OK")

                // First, log database statistics to understand what data exists
                logs.appendLine("\n--- Database Statistics ---")
                logDatabaseStats(conn, logs)

                // Create a path for the export (but DON'T create the directory!)
                // Kuzu's EXPORT DATABASE command requires the target directory to NOT exist - it creates it itself
                val exportDir = File(context.cacheDir, "kuzu_export_${System.currentTimeMillis()}")
                if (exportDir.exists()) {
                    exportDir.deleteRecursively()
                }
                // NOTE: Do NOT call exportDir.mkdirs() - Kuzu will create the directory

                logs.appendLine("\n--- Export Setup ---")
                logs.appendLine("Export directory path: ${exportDir.absolutePath}")
                logs.appendLine("Export dir exists (should be false): ${exportDir.exists()}")
                android.util.Log.d("KuzuDatabaseManager", "Exporting database to: ${exportDir.absolutePath}")
                android.util.Log.d("KuzuDatabaseManager", "Export dir exists (should be false): ${exportDir.exists()}")

                // Use Kuzu's native EXPORT DATABASE command with CSV format
                // NOTE: We MUST use CSV format because Parquet doesn't support fixed-list types
                // (our embedding columns are FLOAT[1024] which are fixed-length arrays)
                // See: https://docs.kuzudb.com/export/parquet/ - "Exporting fixed list data types to Parquet is not yet supported"
                val exportQuery = "EXPORT DATABASE '${exportDir.absolutePath}' (format=\"csv\", header=true)"
                logs.appendLine("\n--- Export Query ---")
                logs.appendLine("Query: $exportQuery")
                android.util.Log.d("KuzuDatabaseManager", "Running export query: $exportQuery")

                val result = conn.query(exportQuery)

                // Check if the query succeeded using isSuccess()
                if (!result.isSuccess) {
                    val errorMsg = result.errorMessage ?: "Unknown error"
                    logs.appendLine("EXPORT DATABASE query FAILED: $errorMsg")
                    android.util.Log.e("KuzuDatabaseManager", "EXPORT DATABASE query failed: $errorMsg")
                    exportDir.deleteRecursively()
                    return@withContext ExportResult(success = false, logs = logs.toString(), error = "EXPORT DATABASE query failed: $errorMsg")
                }

                logs.appendLine("EXPORT DATABASE query succeeded")
                android.util.Log.d("KuzuDatabaseManager", "EXPORT DATABASE command succeeded")

                // List all files in export directory for debugging
                logs.appendLine("\n--- Files in Export Directory ---")
                fun listFilesRecursively(dir: File, prefix: String = ""): List<String> {
                    val files = mutableListOf<String>()
                    dir.listFiles()?.forEach { file ->
                        val path = "$prefix${file.name}"
                        if (file.isDirectory) {
                            files.add("$path/")
                            files.addAll(listFilesRecursively(file, "$path/"))
                        } else {
                            files.add("$path (${file.length()} bytes)")
                        }
                    }
                    return files
                }

                val allFiles = listFilesRecursively(exportDir)
                allFiles.forEach { logs.appendLine("  $it") }
                if (allFiles.isEmpty()) {
                    logs.appendLine("  (no files)")
                }
                android.util.Log.d("KuzuDatabaseManager", "Files in export dir: ${allFiles.joinToString(", ")}")

                // Verify export created files
                var exportedFiles = exportDir.listFiles() ?: emptyArray()
                if (exportedFiles.isEmpty()) {
                    logs.appendLine("\n--- Fallback: Manual COPY TO Export ---")
                    logs.appendLine("EXPORT DATABASE created no files - trying manual COPY TO export")
                    android.util.Log.w("KuzuDatabaseManager", "EXPORT DATABASE created no files - trying manual COPY TO export")

                    // Fallback: manually export each table using COPY TO
                    val fallbackResult = manualExportToDirectory(conn, exportDir, logs)
                    if (!fallbackResult) {
                        exportDir.deleteRecursively()
                        logs.appendLine("ERROR: Both EXPORT DATABASE and manual export failed")
                        return@withContext ExportResult(success = false, logs = logs.toString(), error = "Both EXPORT DATABASE and manual export failed")
                    }

                    // Refresh the file list after manual export
                    exportedFiles = exportDir.listFiles() ?: emptyArray()
                    if (exportedFiles.isEmpty()) {
                        exportDir.deleteRecursively()
                        logs.appendLine("ERROR: Manual export also created no files - database may be empty")
                        return@withContext ExportResult(success = false, logs = logs.toString(), error = "Manual export also created no files - database may be empty")
                    }
                }

                logs.appendLine("\n--- Export Summary ---")
                logs.appendLine("Export created ${exportedFiles.size} files/dirs at top level")
                android.util.Log.d("KuzuDatabaseManager", "Export created ${exportedFiles.size} files/dirs at top level")

                // Zip all exported files
                logs.appendLine("\n--- Zipping Files ---")
                var filesZipped = 0
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        zipDirectory(exportDir, exportDir, zipOut, logs) { count ->
                            filesZipped = count
                        }
                    }
                } ?: run {
                    logs.appendLine("ERROR: Could not open output stream")
                    return@withContext ExportResult(success = false, logs = logs.toString(), error = "Could not open output stream")
                }

                logs.appendLine("Total files zipped: $filesZipped")
                android.util.Log.d("KuzuDatabaseManager", "Export completed: $filesZipped files zipped")

                // Clean up temporary export directory
                exportDir.deleteRecursively()

                logs.appendLine("\n=== EXPORT COMPLETED SUCCESSFULLY ===")
                ExportResult(success = true, logs = logs.toString())
            } catch (e: Exception) {
                logs.appendLine("\n=== EXPORT FAILED ===")
                logs.appendLine("Exception: ${e.javaClass.simpleName}")
                logs.appendLine("Message: ${e.message}")
                logs.appendLine("Stack trace: ${e.stackTraceToString().take(1000)}")
                android.util.Log.e("KuzuDatabaseManager", "Export failed", e)
                ExportResult(success = false, logs = logs.toString(), error = e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Log database statistics to understand what data exists.
     */
    private fun logDatabaseStats(conn: Connection, logs: StringBuilder? = null) {
        try {
            val tables = listOf(
                "JournalEntry", "AtomicThought", "ChatMessage", "Person",
                "Topic", "Goal", "AgendaItem", "Summary", "DailyApp",
                "Note", "UserGoal", "UserTask"
            )

            for (table in tables) {
                try {
                    val countResult = conn.query("MATCH (n:$table) RETURN count(n) AS cnt")
                    if (countResult.isSuccess && countResult.hasNext()) {
                        val tuple = countResult.getNext()
                        val count = tuple.getValue(0)
                        val logMsg = "Table $table: $count rows"
                        logs?.appendLine("  $logMsg")
                        android.util.Log.d("KuzuDatabaseManager", logMsg)
                    }
                } catch (e: Exception) {
                    val logMsg = "Could not count $table: ${e.message}"
                    logs?.appendLine("  $logMsg")
                    android.util.Log.w("KuzuDatabaseManager", logMsg)
                }
            }
        } catch (e: Exception) {
            val logMsg = "Failed to log database stats: ${e.message}"
            logs?.appendLine("  $logMsg")
            android.util.Log.w("KuzuDatabaseManager", "Failed to log database stats", e)
        }
    }

    /**
     * Manual export using COPY TO for each table.
     * This is a fallback when EXPORT DATABASE doesn't work.
     *
     * @return true if at least one table was exported successfully
     */
    private fun manualExportToDirectory(conn: Connection, exportDir: File, logs: StringBuilder? = null): Boolean {
        logs?.appendLine("Starting manual COPY TO export to ${exportDir.absolutePath}")
        android.util.Log.d("KuzuDatabaseManager", "Starting manual COPY TO export to ${exportDir.absolutePath}")

        // Node tables to export
        val nodeTables = listOf(
            "JournalEntry", "AtomicThought", "ChatMessage", "Person",
            "Topic", "Goal", "AgendaItem", "Summary", "DailyApp",
            "Note", "UserGoal", "UserTask"
        )

        // Relationship tables to export
        val relTables = listOf(
            "EXTRACTED_FROM", "RELATES_TO", "MENTIONS_PERSON", "RELATES_TO_TOPIC",
            "THOUGHT_TOPIC", "SUPPORTS_GOAL", "TRACKS_GOAL", "APP_INSPIRED_BY", "SUMMARIZES",
            "SOURCED_FROM", "TASK_LINKED_TO_GOAL", "EXTRACTED_FROM_NOTE", "EXTRACTED_FROM_GOAL",
            "EXTRACTED_FROM_TASK", "NOTE_RELATES_TO_TOPIC", "GOAL_RELATES_TO_TOPIC",
            "TASK_RELATES_TO_TOPIC", "NOTE_MENTIONS_PERSON", "GOAL_MENTIONS_PERSON",
            "TASK_MENTIONS_PERSON"
        )

        var exportedCount = 0

        // Export each node table
        for (table in nodeTables) {
            try {
                val csvFile = File(exportDir, "$table.csv")
                val copyQuery = "COPY (MATCH (n:$table) RETURN n.*) TO '${csvFile.absolutePath}' (header=true)"
                logs?.appendLine("  Running: $copyQuery")
                android.util.Log.d("KuzuDatabaseManager", "Running: $copyQuery")

                val result = conn.query(copyQuery)
                if (result.isSuccess) {
                    if (csvFile.exists() && csvFile.length() > 0) {
                        logs?.appendLine("    -> Exported $table: ${csvFile.length()} bytes")
                        android.util.Log.d("KuzuDatabaseManager", "Exported $table: ${csvFile.length()} bytes")
                        exportedCount++
                    } else {
                        logs?.appendLine("    -> COPY TO for $table succeeded but file is empty/missing")
                        android.util.Log.w("KuzuDatabaseManager", "COPY TO for $table succeeded but file is empty/missing")
                    }
                } else {
                    val errorMsg = result.errorMessage ?: "Unknown error"
                    logs?.appendLine("    -> Failed to export $table: $errorMsg")
                    android.util.Log.w("KuzuDatabaseManager", "Failed to export $table: $errorMsg")
                }
            } catch (e: Exception) {
                logs?.appendLine("    -> Exception exporting $table: ${e.message}")
                android.util.Log.w("KuzuDatabaseManager", "Exception exporting $table: ${e.message}")
            }
        }

        // Export each relationship table
        for (rel in relTables) {
            try {
                val csvFile = File(exportDir, "$rel.csv")
                // For relationships, we need to export the edge data with source and target IDs
                val copyQuery = "COPY (MATCH ()-[r:$rel]->() RETURN r.*) TO '${csvFile.absolutePath}' (header=true)"
                logs?.appendLine("  Running: $copyQuery")
                android.util.Log.d("KuzuDatabaseManager", "Running: $copyQuery")

                val result = conn.query(copyQuery)
                if (result.isSuccess) {
                    if (csvFile.exists() && csvFile.length() > 0) {
                        logs?.appendLine("    -> Exported $rel: ${csvFile.length()} bytes")
                        android.util.Log.d("KuzuDatabaseManager", "Exported $rel: ${csvFile.length()} bytes")
                        exportedCount++
                    } else {
                        logs?.appendLine("    -> COPY TO for $rel succeeded but no data (empty table)")
                        android.util.Log.d("KuzuDatabaseManager", "COPY TO for $rel succeeded but no data (empty table)")
                    }
                } else {
                    val errorMsg = result.errorMessage ?: "Unknown error"
                    logs?.appendLine("    -> Failed to export $rel: $errorMsg")
                    android.util.Log.w("KuzuDatabaseManager", "Failed to export $rel: $errorMsg")
                }
            } catch (e: Exception) {
                logs?.appendLine("    -> Exception exporting $rel: ${e.message}")
                android.util.Log.w("KuzuDatabaseManager", "Exception exporting $rel: ${e.message}")
            }
        }

        // Also create a schema.cypher file for import compatibility
        try {
            val schemaFile = File(exportDir, "schema.cypher")
            val schemaContent = generateSchemaCypher()
            schemaFile.writeText(schemaContent)
            logs?.appendLine("  Created schema.cypher: ${schemaFile.length()} bytes")
            android.util.Log.d("KuzuDatabaseManager", "Created schema.cypher: ${schemaFile.length()} bytes")
        } catch (e: Exception) {
            logs?.appendLine("  Failed to create schema.cypher: ${e.message}")
            android.util.Log.w("KuzuDatabaseManager", "Failed to create schema.cypher: ${e.message}")
        }

        logs?.appendLine("Manual export completed: $exportedCount tables exported")
        android.util.Log.d("KuzuDatabaseManager", "Manual export completed: $exportedCount tables exported")
        return exportedCount > 0
    }

    /**
     * Generate Cypher schema statements for manual export.
     */
    private fun generateSchemaCypher(): String {
        return buildString {
            // Node tables
            appendLine("CREATE NODE TABLE IF NOT EXISTS JournalEntry(id STRING PRIMARY KEY, userId STRING, content STRING, mood STRING, tags STRING, date INT64, createdAt INT64, updatedAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS AtomicThought(id STRING PRIMARY KEY, userId STRING, content STRING, thoughtType STRING, confidence FLOAT, sentiment FLOAT, importance INT8, sourceType STRING, sourceId STRING, createdAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS ChatMessage(id STRING PRIMARY KEY, conversationId STRING, userId STRING, role STRING, content STRING, createdAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS Person(id STRING PRIMARY KEY, userId STRING, name STRING, normalizedName STRING, relationship STRING, firstMentioned INT64, lastMentioned INT64, mentionCount INT32);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS Topic(id STRING PRIMARY KEY, userId STRING, name STRING, normalizedName STRING, category STRING, createdAt INT64, mentionCount INT32);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS Goal(id STRING PRIMARY KEY, userId STRING, description STRING, status STRING, createdAt INT64, updatedAt INT64, targetDate INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS AgendaItem(id STRING PRIMARY KEY, userId STRING, title STRING, description STRING, startTime INT64, endTime INT64, isAllDay BOOLEAN, location STRING, createdAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS Summary(id STRING PRIMARY KEY, userId STRING, summaryType STRING, content STRING, periodStart INT64, periodEnd INT64, createdAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS DailyApp(id STRING PRIMARY KEY, userId STRING, date INT64, title STRING, description STRING, htmlCode STRING, journalContext STRING, status STRING, usedAt INT64, createdAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS Note(id STRING PRIMARY KEY, userId STRING, title STRING, content STRING, createdAt INT64, updatedAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS UserGoal(id STRING PRIMARY KEY, userId STRING, title STRING, description STRING, targetDate STRING, status STRING, priority STRING, createdAt INT64, updatedAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")
            appendLine("CREATE NODE TABLE IF NOT EXISTS UserTask(id STRING PRIMARY KEY, userId STRING, title STRING, description STRING, dueDate STRING, isCompleted BOOLEAN, priority STRING, linkedGoalId STRING, createdAt INT64, updatedAt INT64, embedding FLOAT[$EMBEDDING_DIMENSIONS], embeddingModel STRING);")

            // Relationship tables
            appendLine("CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM(FROM AtomicThought TO JournalEntry, extractedAt INT64, confidence FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS RELATES_TO(FROM AtomicThought TO AtomicThought, relationType STRING, strength FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS MENTIONS_PERSON(FROM JournalEntry TO Person, mentionedAt INT64, sentiment FLOAT, context STRING);")
            appendLine("CREATE REL TABLE IF NOT EXISTS RELATES_TO_TOPIC(FROM JournalEntry TO Topic, relevance FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS THOUGHT_TOPIC(FROM AtomicThought TO Topic, relevance FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS SUPPORTS_GOAL(FROM AtomicThought TO Goal, supportType STRING, createdAt INT64);")
            appendLine("CREATE REL TABLE IF NOT EXISTS TRACKS_GOAL(FROM JournalEntry TO Goal, progressNote STRING, createdAt INT64);")
            appendLine("CREATE REL TABLE IF NOT EXISTS APP_INSPIRED_BY(FROM DailyApp TO JournalEntry, relevance FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS SUMMARIZES(FROM Summary TO JournalEntry, weight FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS SOURCED_FROM(FROM AgendaItem TO JournalEntry, createdAt INT64);")
            appendLine("CREATE REL TABLE IF NOT EXISTS TASK_LINKED_TO_GOAL(FROM UserTask TO UserGoal, createdAt INT64);")
            appendLine("CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_NOTE(FROM AtomicThought TO Note, extractedAt INT64, confidence FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_GOAL(FROM AtomicThought TO UserGoal, extractedAt INT64, confidence FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_TASK(FROM AtomicThought TO UserTask, extractedAt INT64, confidence FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS NOTE_RELATES_TO_TOPIC(FROM Note TO Topic, relevance FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS GOAL_RELATES_TO_TOPIC(FROM UserGoal TO Topic, relevance FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS TASK_RELATES_TO_TOPIC(FROM UserTask TO Topic, relevance FLOAT);")
            appendLine("CREATE REL TABLE IF NOT EXISTS NOTE_MENTIONS_PERSON(FROM Note TO Person, mentionedAt INT64, sentiment FLOAT, context STRING);")
            appendLine("CREATE REL TABLE IF NOT EXISTS GOAL_MENTIONS_PERSON(FROM UserGoal TO Person, mentionedAt INT64, sentiment FLOAT, context STRING);")
            appendLine("CREATE REL TABLE IF NOT EXISTS TASK_MENTIONS_PERSON(FROM UserTask TO Person, mentionedAt INT64, sentiment FLOAT, context STRING);")
        }
    }

    /**
     * Recursively zip a directory and its contents.
     */
    private fun zipDirectory(
        rootDir: File,
        currentDir: File,
        zipOut: ZipOutputStream,
        logs: StringBuilder? = null,
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
                    zipFileOrDir(rootDir, child, zipOut, logs) { count++ }
                }
            } else {
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(relativePath)
                    zipOut.putNextEntry(zipEntry)
                    val bytesCopied = fis.copyTo(zipOut)
                    zipOut.closeEntry()
                    logs?.appendLine("  Zipped: $relativePath ($bytesCopied bytes)")
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
        logs: StringBuilder? = null,
        countCallback: () -> Unit
    ) {
        val relativePath = file.relativeTo(rootDir).path
        if (file.isDirectory) {
            zipOut.putNextEntry(ZipEntry("$relativePath/"))
            zipOut.closeEntry()
            file.listFiles()?.forEach { child ->
                zipFileOrDir(rootDir, child, zipOut, logs, countCallback)
            }
        } else {
            FileInputStream(file).use { fis ->
                val zipEntry = ZipEntry(relativePath)
                zipOut.putNextEntry(zipEntry)
                val bytesCopied = fis.copyTo(zipOut)
                zipOut.closeEntry()
                logs?.appendLine("  Zipped: $relativePath ($bytesCopied bytes)")
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
                sourceType STRING,
                sourceId STRING,
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

        // User-created notes (quick memories)
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS Note(
                id STRING PRIMARY KEY,
                userId STRING,
                title STRING,
                content STRING,
                createdAt INT64,
                updatedAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // User-created goals (distinct from AI-extracted Goal)
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS UserGoal(
                id STRING PRIMARY KEY,
                userId STRING,
                title STRING,
                description STRING,
                targetDate STRING,
                status STRING,
                priority STRING,
                createdAt INT64,
                updatedAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // User-created tasks
        conn.query("""
            CREATE NODE TABLE IF NOT EXISTS UserTask(
                id STRING PRIMARY KEY,
                userId STRING,
                title STRING,
                description STRING,
                dueDate STRING,
                isCompleted BOOLEAN,
                priority STRING,
                linkedGoalId STRING,
                createdAt INT64,
                updatedAt INT64,
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

        // Agenda item sourced from journal entry
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS SOURCED_FROM(
                FROM AgendaItem TO JournalEntry,
                createdAt INT64
            )
        """.trimIndent())

        // Task linked to a user goal
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS TASK_LINKED_TO_GOAL(
                FROM UserTask TO UserGoal,
                createdAt INT64
            )
        """.trimIndent())

        // Atomic thoughts extracted from notes
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_NOTE(
                FROM AtomicThought TO Note,
                extractedAt INT64,
                confidence FLOAT
            )
        """.trimIndent())

        // Atomic thoughts extracted from user goals
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_GOAL(
                FROM AtomicThought TO UserGoal,
                extractedAt INT64,
                confidence FLOAT
            )
        """.trimIndent())

        // Atomic thoughts extracted from user tasks
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_TASK(
                FROM AtomicThought TO UserTask,
                extractedAt INT64,
                confidence FLOAT
            )
        """.trimIndent())

        // Note relates to a topic
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS NOTE_RELATES_TO_TOPIC(
                FROM Note TO Topic,
                relevance FLOAT
            )
        """.trimIndent())

        // UserGoal relates to a topic
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS GOAL_RELATES_TO_TOPIC(
                FROM UserGoal TO Topic,
                relevance FLOAT
            )
        """.trimIndent())

        // UserTask relates to a topic
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS TASK_RELATES_TO_TOPIC(
                FROM UserTask TO Topic,
                relevance FLOAT
            )
        """.trimIndent())

        // Note mentions a person (same as journal entries)
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS NOTE_MENTIONS_PERSON(
                FROM Note TO Person,
                mentionedAt INT64,
                sentiment FLOAT,
                context STRING
            )
        """.trimIndent())

        // UserGoal mentions a person (same as journal entries)
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS GOAL_MENTIONS_PERSON(
                FROM UserGoal TO Person,
                mentionedAt INT64,
                sentiment FLOAT,
                context STRING
            )
        """.trimIndent())

        // UserTask mentions a person (same as journal entries)
        conn.query("""
            CREATE REL TABLE IF NOT EXISTS TASK_MENTIONS_PERSON(
                FROM UserTask TO Person,
                mentionedAt INT64,
                sentiment FLOAT,
                context STRING
            )
        """.trimIndent())
    }
}
