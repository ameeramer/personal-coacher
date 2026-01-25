package com.personalcoacher.data.local.kuzu

import com.kuzudb.FlatTuple
import com.personalcoacher.data.remote.VoyageEmbeddingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * RAG (Retrieval-Augmented Generation) Engine
 *
 * Implements hybrid retrieval combining:
 * 1. Vector Search (Semantic) - Uses Voyage AI embeddings for conceptual similarity
 * 2. BM25 Search (Keyword) - Full-text search for exact term matching
 * 3. Graph Traversal - Relationship-based context discovery
 *
 * Scoring weights:
 * - Vector (semantic): 40%
 * - BM25 (keyword): 30%
 * - Graph (relationships): 20%
 * - Recency: 10%
 */
@Singleton
class RagEngine @Inject constructor(
    private val kuzuDb: KuzuDatabaseManager,
    private val voyageService: VoyageEmbeddingService
) {
    companion object {
        // Retrieval weights
        private const val WEIGHT_VECTOR = 0.4f
        private const val WEIGHT_BM25 = 0.3f
        private const val WEIGHT_GRAPH = 0.2f
        private const val WEIGHT_RECENCY = 0.1f

        // Retrieval limits
        private const val VECTOR_CANDIDATES = 30
        private const val BM25_CANDIDATES = 30
        private const val FINAL_RESULTS = 10

        // Recency decay (half-life in days)
        private const val RECENCY_HALF_LIFE_DAYS = 14.0
    }

    /**
     * Retrieve relevant context for a user query.
     *
     * @param userId The user's ID
     * @param query The user's query text
     * @param includeAtomicThoughts Whether to include extracted atomic thoughts
     * @param includeGoals Whether to include user goals
     * @param includeNotes Whether to include user notes
     * @param includeUserGoals Whether to include user-created goals
     * @param includeUserTasks Whether to include user-created tasks
     * @return RetrievedContext containing ranked relevant documents
     */
    suspend fun retrieveContext(
        userId: String,
        query: String,
        includeAtomicThoughts: Boolean = true,
        includeGoals: Boolean = true,
        includeNotes: Boolean = true,
        includeUserGoals: Boolean = true,
        includeUserTasks: Boolean = true
    ): RetrievedContext = withContext(Dispatchers.IO) {
        // Generate query embedding
        val queryEmbedding = try {
            voyageService.embed(query, inputType = "query")
        } catch (e: Exception) {
            // Fall back to keyword-only search if embedding fails
            return@withContext retrieveKeywordOnly(userId, query)
        }

        // Parallel retrieval from different sources
        val journalResults = retrieveJournalEntries(userId, query, queryEmbedding)
        val thoughtResults = if (includeAtomicThoughts) {
            retrieveAtomicThoughts(userId, query, queryEmbedding)
        } else emptyList()
        val goalResults = if (includeGoals) {
            retrieveGoals(userId, queryEmbedding)
        } else emptyList()

        // Use hybrid (semantic + keyword) retrieval for user goals, tasks, and notes (same as journal entries)
        val noteResults = if (includeNotes) {
            retrieveNotes(userId, query, queryEmbedding)
        } else emptyList()
        val userGoalResults = if (includeUserGoals) {
            retrieveUserGoals(userId, query, queryEmbedding)
        } else emptyList()
        val userTaskResults = if (includeUserTasks) {
            retrieveUserTasks(userId, query, queryEmbedding)
        } else emptyList()

        // Get graph-connected context
        val graphContext = retrieveGraphContext(userId, journalResults.map { it.id })

        RetrievedContext(
            journalEntries = journalResults.take(FINAL_RESULTS),
            atomicThoughts = thoughtResults.take(FINAL_RESULTS / 2),
            goals = goalResults.take(3),
            notes = noteResults.take(5),
            userGoals = userGoalResults.take(5),
            userTasks = userTaskResults.take(5),
            relatedPeople = graphContext.people,
            relatedTopics = graphContext.topics,
            queryEmbedding = queryEmbedding
        )
    }

    /**
     * Retrieve journal entries using hybrid search.
     */
    private suspend fun retrieveJournalEntries(
        userId: String,
        query: String,
        queryEmbedding: FloatArray
    ): List<RankedDocument> {
        val now = Instant.now().toEpochMilli()
        val results = mutableMapOf<String, ScoredDocument>()

        // 1. Vector search
        try {
            val vectorQuery = """
                MATCH (j:JournalEntry)
                WHERE j.userId = '$userId' AND j.embedding IS NOT NULL
                RETURN j.id AS id, j.content AS content, j.date AS date, j.mood AS mood,
                       array_cosine_similarity(j.embedding, ${queryEmbedding.toKuzuArray()}) AS similarity
                ORDER BY similarity DESC
                LIMIT $VECTOR_CANDIDATES
            """.trimIndent()

            val vectorResult = kuzuDb.execute(vectorQuery)
            while (vectorResult.hasNext()) {
                val row: FlatTuple = vectorResult.getNext()
                val id = row.getValue(0).getValue<String>()
                val content = row.getValue(1).getValue<String>()
                val date = row.getValue(2).getValue<Long>()
                val mood = row.getValue(3).getValue<String?>()
                val similarity = row.getValue(4).getValue<Double>().toFloat()

                results[id] = ScoredDocument(
                    id = id,
                    content = content,
                    date = date,
                    mood = mood,
                    vectorScore = similarity,
                    bm25Score = 0f,
                    graphScore = 0f
                )
            }
        } catch (e: Exception) {
            // Vector search not available, continue with BM25
        }

        // 2. BM25 keyword search (using CONTAINS for basic text matching)
        // Note: Full BM25 requires the FTS extension to be enabled
        try {
            val keywords = query.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .take(5)

            if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "LOWER(j.content) CONTAINS LOWER('$it')"
                }

                val bm25Query = """
                    MATCH (j:JournalEntry)
                    WHERE j.userId = '$userId' AND ($keywordConditions)
                    RETURN j.id AS id, j.content AS content, j.date AS date, j.mood AS mood
                    LIMIT $BM25_CANDIDATES
                """.trimIndent()

                val bm25Result = kuzuDb.execute(bm25Query)
                while (bm25Result.hasNext()) {
                    val row: FlatTuple = bm25Result.getNext()
                    val id = row.getValue(0).getValue<String>()
                    val content = row.getValue(1).getValue<String>()
                    val date = row.getValue(2).getValue<Long>()
                    val mood = row.getValue(3).getValue<String?>()

                    // Calculate simple keyword match score
                    val matchCount = keywords.count {
                        content.lowercase().contains(it.lowercase())
                    }
                    val bm25Score = matchCount.toFloat() / keywords.size

                    val existing = results[id]
                    if (existing != null) {
                        results[id] = existing.copy(bm25Score = bm25Score)
                    } else {
                        results[id] = ScoredDocument(
                            id = id,
                            content = content,
                            date = date,
                            mood = mood,
                            vectorScore = 0f,
                            bm25Score = bm25Score,
                            graphScore = 0f
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // BM25 search failed, continue with vector results
        }

        // 3. Calculate final scores with recency weighting
        return results.values.map { doc ->
            val recencyScore = calculateRecencyScore(doc.date, now)
            val finalScore = (doc.vectorScore * WEIGHT_VECTOR) +
                    (doc.bm25Score * WEIGHT_BM25) +
                    (doc.graphScore * WEIGHT_GRAPH) +
                    (recencyScore * WEIGHT_RECENCY)

            RankedDocument(
                id = doc.id,
                content = doc.content,
                date = doc.date,
                mood = doc.mood,
                score = finalScore,
                vectorScore = doc.vectorScore,
                bm25Score = doc.bm25Score,
                graphScore = doc.graphScore,
                recencyScore = recencyScore
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Retrieve atomic thoughts using hybrid search (semantic + keyword).
     * This mirrors how journal entries are retrieved, combining:
     * 1. Vector search (semantic similarity)
     * 2. BM25/keyword search (exact term matching)
     *
     * This allows queries like "what are my goals" to match AtomicThoughts
     * containing "Goal: run 15km..." via keyword search on "goal".
     */
    private suspend fun retrieveAtomicThoughts(
        userId: String,
        query: String,
        queryEmbedding: FloatArray
    ): List<RankedThought> {
        val results = mutableMapOf<String, ScoredThought>()

        // 1. Vector search (semantic similarity)
        try {
            val thoughtQuery = """
                MATCH (t:AtomicThought)
                WHERE t.userId = '$userId' AND t.embedding IS NOT NULL
                RETURN t.id AS id, t.content AS content, t.thoughtType AS type,
                       t.confidence AS confidence, t.importance AS importance,
                       array_cosine_similarity(t.embedding, ${queryEmbedding.toKuzuArray()}) AS similarity
                ORDER BY similarity DESC
                LIMIT 20
            """.trimIndent()

            val result = kuzuDb.execute(thoughtQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val id = row.getValue(0).getValue<String>()
                results[id] = ScoredThought(
                    id = id,
                    content = row.getValue(1).getValue<String>(),
                    type = row.getValue(2).getValue<String>(),
                    confidence = row.getValue(3).getValue<Double>().toFloat(),
                    importance = row.getValue(4).getValue<Long>().toInt(),
                    vectorScore = row.getValue(5).getValue<Double>().toFloat(),
                    bm25Score = 0f
                )
            }
        } catch (e: Exception) {
            // Atomic thoughts not available yet - continue with keyword search
        }

        // 2. BM25/Keyword search (same as journal entries)
        // This allows queries like "what are my goals" to match "Goal: run 15km..." thoughts
        try {
            val keywords = query.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .take(5)

            if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "LOWER(t.content) CONTAINS LOWER('$it')"
                }

                val bm25Query = """
                    MATCH (t:AtomicThought)
                    WHERE t.userId = '$userId' AND ($keywordConditions)
                    RETURN t.id AS id, t.content AS content, t.thoughtType AS type,
                           t.confidence AS confidence, t.importance AS importance
                    LIMIT 20
                """.trimIndent()

                val bm25Result = kuzuDb.execute(bm25Query)
                while (bm25Result.hasNext()) {
                    val row: FlatTuple = bm25Result.getNext()
                    val id = row.getValue(0).getValue<String>()
                    val content = row.getValue(1).getValue<String>()

                    // Calculate keyword match score
                    val matchCount = keywords.count {
                        content.lowercase().contains(it.lowercase())
                    }
                    val bm25Score = matchCount.toFloat() / keywords.size

                    val existing = results[id]
                    if (existing != null) {
                        results[id] = existing.copy(bm25Score = bm25Score)
                    } else {
                        results[id] = ScoredThought(
                            id = id,
                            content = content,
                            type = row.getValue(2).getValue<String>(),
                            confidence = row.getValue(3).getValue<Double>().toFloat(),
                            importance = row.getValue(4).getValue<Long>().toInt(),
                            vectorScore = 0f,
                            bm25Score = bm25Score
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Keyword search failed, continue with vector results
        }

        // 3. Calculate final scores combining vector and keyword
        return results.values.map { thought ->
            val finalScore = (thought.vectorScore * WEIGHT_VECTOR) + (thought.bm25Score * WEIGHT_BM25)
            RankedThought(
                id = thought.id,
                content = thought.content,
                type = thought.type,
                confidence = thought.confidence,
                importance = thought.importance,
                score = finalScore
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Retrieve user goals related to the query.
     */
    private suspend fun retrieveGoals(
        userId: String,
        queryEmbedding: FloatArray
    ): List<RankedGoal> {
        val results = mutableListOf<RankedGoal>()

        try {
            val goalQuery = """
                MATCH (g:Goal)
                WHERE g.userId = '$userId' AND g.status = 'active' AND g.embedding IS NOT NULL
                RETURN g.id AS id, g.description AS description, g.status AS status,
                       array_cosine_similarity(g.embedding, ${queryEmbedding.toKuzuArray()}) AS similarity
                ORDER BY similarity DESC
                LIMIT 5
            """.trimIndent()

            val result = kuzuDb.execute(goalQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                results.add(
                    RankedGoal(
                        id = row.getValue(0).getValue<String>(),
                        description = row.getValue(1).getValue<String>(),
                        status = row.getValue(2).getValue<String>(),
                        score = row.getValue(3).getValue<Double>().toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            // Goals not available yet
        }

        return results
    }

    /**
     * Retrieve user notes using hybrid search (semantic + keyword).
     * This mirrors how journal entries are retrieved, combining:
     * 1. Vector search (semantic similarity)
     * 2. BM25/keyword search (exact term matching)
     */
    private suspend fun retrieveNotes(
        userId: String,
        query: String,
        queryEmbedding: FloatArray
    ): List<RankedNote> {
        val now = Instant.now().toEpochMilli()
        val results = mutableMapOf<String, ScoredNote>()

        // 1. Vector search (semantic similarity)
        try {
            val noteQuery = """
                MATCH (n:Note)
                WHERE n.userId = '$userId' AND n.embedding IS NOT NULL
                RETURN n.id AS id, n.title AS title, n.content AS content, n.createdAt AS createdAt,
                       array_cosine_similarity(n.embedding, ${queryEmbedding.toKuzuArray()}) AS similarity
                ORDER BY similarity DESC
                LIMIT 10
            """.trimIndent()

            val result = kuzuDb.execute(noteQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val id = row.getValue(0).getValue<String>()
                results[id] = ScoredNote(
                    id = id,
                    title = row.getValue(1).getValue<String>(),
                    content = row.getValue(2).getValue<String>(),
                    createdAt = row.getValue(3).getValue<Long>(),
                    vectorScore = row.getValue(4).getValue<Double>().toFloat(),
                    bm25Score = 0f
                )
            }
        } catch (e: Exception) {
            // Notes not available yet - continue with keyword search
        }

        // 2. BM25/Keyword search (same as journal entries)
        try {
            val keywords = query.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .take(5)

            if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "(LOWER(n.title) CONTAINS LOWER('$it') OR LOWER(n.content) CONTAINS LOWER('$it'))"
                }

                val bm25Query = """
                    MATCH (n:Note)
                    WHERE n.userId = '$userId' AND ($keywordConditions)
                    RETURN n.id AS id, n.title AS title, n.content AS content, n.createdAt AS createdAt
                    LIMIT 10
                """.trimIndent()

                val bm25Result = kuzuDb.execute(bm25Query)
                while (bm25Result.hasNext()) {
                    val row: FlatTuple = bm25Result.getNext()
                    val id = row.getValue(0).getValue<String>()
                    val title = row.getValue(1).getValue<String>()
                    val content = row.getValue(2).getValue<String>()

                    // Calculate keyword match score
                    val matchCount = keywords.count {
                        title.lowercase().contains(it.lowercase()) ||
                        content.lowercase().contains(it.lowercase())
                    }
                    val bm25Score = matchCount.toFloat() / keywords.size

                    val existing = results[id]
                    if (existing != null) {
                        results[id] = existing.copy(bm25Score = bm25Score)
                    } else {
                        results[id] = ScoredNote(
                            id = id,
                            title = title,
                            content = content,
                            createdAt = row.getValue(3).getValue<Long>(),
                            vectorScore = 0f,
                            bm25Score = bm25Score
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Keyword search failed, continue with vector results
        }

        // 3. Calculate final scores combining vector, keyword, and recency
        return results.values.map { note ->
            val recencyScore = calculateRecencyScore(note.createdAt, now)
            val finalScore = (note.vectorScore * WEIGHT_VECTOR) +
                           (note.bm25Score * WEIGHT_BM25) +
                           (recencyScore * WEIGHT_RECENCY)
            RankedNote(
                id = note.id,
                title = note.title,
                content = note.content,
                createdAt = note.createdAt,
                score = finalScore
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Retrieve user-created goals using hybrid search (semantic + keyword).
     * This mirrors how journal entries are retrieved, combining:
     * 1. Vector search (semantic similarity)
     * 2. BM25/keyword search (exact term matching)
     */
    private suspend fun retrieveUserGoals(
        userId: String,
        query: String,
        queryEmbedding: FloatArray
    ): List<RankedUserGoal> {
        val results = mutableMapOf<String, ScoredUserGoal>()

        // 1. Vector search (semantic similarity)
        try {
            val goalQuery = """
                MATCH (g:UserGoal)
                WHERE g.userId = '$userId' AND g.status = 'ACTIVE' AND g.embedding IS NOT NULL
                RETURN g.id AS id, g.title AS title, g.description AS description,
                       g.status AS status, g.priority AS priority, g.targetDate AS targetDate,
                       array_cosine_similarity(g.embedding, ${queryEmbedding.toKuzuArray()}) AS similarity
                ORDER BY similarity DESC
                LIMIT 10
            """.trimIndent()

            val result = kuzuDb.execute(goalQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val id = row.getValue(0).getValue<String>()
                results[id] = ScoredUserGoal(
                    id = id,
                    title = row.getValue(1).getValue<String>(),
                    description = row.getValue(2).getValue<String>(),
                    status = row.getValue(3).getValue<String>(),
                    priority = row.getValue(4).getValue<String>(),
                    targetDate = row.getValue(5).getValue<String?>(),
                    vectorScore = row.getValue(6).getValue<Double>().toFloat(),
                    bm25Score = 0f
                )
            }
        } catch (e: Exception) {
            // User goals not available yet - continue with keyword search
        }

        // 2. BM25/Keyword search (same as journal entries)
        try {
            val keywords = query.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .take(5)

            if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "(LOWER(g.title) CONTAINS LOWER('$it') OR LOWER(g.description) CONTAINS LOWER('$it'))"
                }

                val bm25Query = """
                    MATCH (g:UserGoal)
                    WHERE g.userId = '$userId' AND g.status = 'ACTIVE' AND ($keywordConditions)
                    RETURN g.id AS id, g.title AS title, g.description AS description,
                           g.status AS status, g.priority AS priority, g.targetDate AS targetDate
                    LIMIT 10
                """.trimIndent()

                val bm25Result = kuzuDb.execute(bm25Query)
                while (bm25Result.hasNext()) {
                    val row: FlatTuple = bm25Result.getNext()
                    val id = row.getValue(0).getValue<String>()
                    val title = row.getValue(1).getValue<String>()
                    val description = row.getValue(2).getValue<String>()

                    // Calculate keyword match score
                    val matchCount = keywords.count {
                        title.lowercase().contains(it.lowercase()) ||
                        description.lowercase().contains(it.lowercase())
                    }
                    val bm25Score = matchCount.toFloat() / keywords.size

                    val existing = results[id]
                    if (existing != null) {
                        results[id] = existing.copy(bm25Score = bm25Score)
                    } else {
                        results[id] = ScoredUserGoal(
                            id = id,
                            title = title,
                            description = description,
                            status = row.getValue(3).getValue<String>(),
                            priority = row.getValue(4).getValue<String>(),
                            targetDate = row.getValue(5).getValue<String?>(),
                            vectorScore = 0f,
                            bm25Score = bm25Score
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Keyword search failed, continue with vector results
        }

        // 3. Calculate final scores combining vector and keyword
        return results.values.map { goal ->
            val finalScore = (goal.vectorScore * WEIGHT_VECTOR) + (goal.bm25Score * WEIGHT_BM25)
            RankedUserGoal(
                id = goal.id,
                title = goal.title,
                description = goal.description,
                status = goal.status,
                priority = goal.priority,
                targetDate = goal.targetDate,
                score = finalScore
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Retrieve user-created tasks using hybrid search (semantic + keyword).
     * This mirrors how journal entries are retrieved, combining:
     * 1. Vector search (semantic similarity)
     * 2. BM25/keyword search (exact term matching)
     */
    private suspend fun retrieveUserTasks(
        userId: String,
        query: String,
        queryEmbedding: FloatArray
    ): List<RankedUserTask> {
        val results = mutableMapOf<String, ScoredUserTask>()

        // 1. Vector search (semantic similarity)
        try {
            val taskQuery = """
                MATCH (t:UserTask)
                WHERE t.userId = '$userId' AND t.isCompleted = false AND t.embedding IS NOT NULL
                RETURN t.id AS id, t.title AS title, t.description AS description,
                       t.isCompleted AS isCompleted, t.priority AS priority,
                       t.dueDate AS dueDate, t.linkedGoalId AS linkedGoalId,
                       array_cosine_similarity(t.embedding, ${queryEmbedding.toKuzuArray()}) AS similarity
                ORDER BY similarity DESC
                LIMIT 10
            """.trimIndent()

            val result = kuzuDb.execute(taskQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val id = row.getValue(0).getValue<String>()
                results[id] = ScoredUserTask(
                    id = id,
                    title = row.getValue(1).getValue<String>(),
                    description = row.getValue(2).getValue<String>(),
                    isCompleted = row.getValue(3).getValue<Boolean>(),
                    priority = row.getValue(4).getValue<String>(),
                    dueDate = row.getValue(5).getValue<String?>(),
                    linkedGoalId = row.getValue(6).getValue<String?>(),
                    vectorScore = row.getValue(7).getValue<Double>().toFloat(),
                    bm25Score = 0f
                )
            }
        } catch (e: Exception) {
            // User tasks not available yet - continue with keyword search
        }

        // 2. BM25/Keyword search (same as journal entries)
        try {
            val keywords = query.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .take(5)

            if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "(LOWER(t.title) CONTAINS LOWER('$it') OR LOWER(t.description) CONTAINS LOWER('$it'))"
                }

                val bm25Query = """
                    MATCH (t:UserTask)
                    WHERE t.userId = '$userId' AND t.isCompleted = false AND ($keywordConditions)
                    RETURN t.id AS id, t.title AS title, t.description AS description,
                           t.isCompleted AS isCompleted, t.priority AS priority,
                           t.dueDate AS dueDate, t.linkedGoalId AS linkedGoalId
                    LIMIT 10
                """.trimIndent()

                val bm25Result = kuzuDb.execute(bm25Query)
                while (bm25Result.hasNext()) {
                    val row: FlatTuple = bm25Result.getNext()
                    val id = row.getValue(0).getValue<String>()
                    val title = row.getValue(1).getValue<String>()
                    val description = row.getValue(2).getValue<String>()

                    // Calculate keyword match score
                    val matchCount = keywords.count {
                        title.lowercase().contains(it.lowercase()) ||
                        description.lowercase().contains(it.lowercase())
                    }
                    val bm25Score = matchCount.toFloat() / keywords.size

                    val existing = results[id]
                    if (existing != null) {
                        results[id] = existing.copy(bm25Score = bm25Score)
                    } else {
                        results[id] = ScoredUserTask(
                            id = id,
                            title = title,
                            description = description,
                            isCompleted = row.getValue(3).getValue<Boolean>(),
                            priority = row.getValue(4).getValue<String>(),
                            dueDate = row.getValue(5).getValue<String?>(),
                            linkedGoalId = row.getValue(6).getValue<String?>(),
                            vectorScore = 0f,
                            bm25Score = bm25Score
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Keyword search failed, continue with vector results
        }

        // 3. Calculate final scores combining vector and keyword
        return results.values.map { task ->
            val finalScore = (task.vectorScore * WEIGHT_VECTOR) + (task.bm25Score * WEIGHT_BM25)
            RankedUserTask(
                id = task.id,
                title = task.title,
                description = task.description,
                isCompleted = task.isCompleted,
                priority = task.priority,
                dueDate = task.dueDate,
                linkedGoalId = task.linkedGoalId,
                score = finalScore
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Retrieve ALL active user goals (not filtered by semantic similarity).
     * This ensures the coach always knows about user goals regardless of the query.
     */
    suspend fun retrieveAllUserGoals(userId: String): List<AllUserGoal> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AllUserGoal>()

        try {
            val goalQuery = """
                MATCH (g:UserGoal)
                WHERE g.userId = '$userId' AND g.status = 'ACTIVE'
                RETURN g.id AS id, g.title AS title, g.description AS description,
                       g.status AS status, g.priority AS priority, g.targetDate AS targetDate
                ORDER BY CASE g.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END
                LIMIT 20
            """.trimIndent()

            val result = kuzuDb.execute(goalQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                results.add(
                    AllUserGoal(
                        id = row.getValue(0).getValue<String>(),
                        title = row.getValue(1).getValue<String>(),
                        description = row.getValue(2).getValue<String>(),
                        status = row.getValue(3).getValue<String>(),
                        priority = row.getValue(4).getValue<String>(),
                        targetDate = row.getValue(5).getValue<String?>()
                    )
                )
            }
        } catch (e: Exception) {
            // User goals not available yet
        }

        results
    }

    /**
     * Retrieve ALL pending user tasks (not filtered by semantic similarity).
     * This ensures the coach always knows about user tasks regardless of the query.
     */
    suspend fun retrieveAllUserTasks(userId: String): List<AllUserTask> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AllUserTask>()

        try {
            val taskQuery = """
                MATCH (t:UserTask)
                WHERE t.userId = '$userId' AND t.isCompleted = false
                RETURN t.id AS id, t.title AS title, t.description AS description,
                       t.isCompleted AS isCompleted, t.priority AS priority,
                       t.dueDate AS dueDate, t.linkedGoalId AS linkedGoalId
                ORDER BY CASE t.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END
                LIMIT 30
            """.trimIndent()

            val result = kuzuDb.execute(taskQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                results.add(
                    AllUserTask(
                        id = row.getValue(0).getValue<String>(),
                        title = row.getValue(1).getValue<String>(),
                        description = row.getValue(2).getValue<String>(),
                        isCompleted = row.getValue(3).getValue<Boolean>(),
                        priority = row.getValue(4).getValue<String>(),
                        dueDate = row.getValue(5).getValue<String?>(),
                        linkedGoalId = row.getValue(6).getValue<String?>()
                    )
                )
            }
        } catch (e: Exception) {
            // User tasks not available yet
        }

        results
    }

    /**
     * Retrieve ALL recent notes (not filtered by semantic similarity).
     * This ensures the coach always knows about user notes regardless of the query.
     */
    suspend fun retrieveAllNotes(userId: String, limit: Int = 10): List<AllNote> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AllNote>()

        try {
            val noteQuery = """
                MATCH (n:Note)
                WHERE n.userId = '$userId'
                RETURN n.id AS id, n.title AS title, n.content AS content, n.createdAt AS createdAt
                ORDER BY n.createdAt DESC
                LIMIT $limit
            """.trimIndent()

            val result = kuzuDb.execute(noteQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                results.add(
                    AllNote(
                        id = row.getValue(0).getValue<String>(),
                        title = row.getValue(1).getValue<String>(),
                        content = row.getValue(2).getValue<String>(),
                        createdAt = row.getValue(3).getValue<Long>()
                    )
                )
            }
        } catch (e: Exception) {
            // Notes not available yet
        }

        results
    }

    /**
     * Retrieve graph-connected context (people, topics) from journal entries.
     */
    private suspend fun retrieveGraphContext(
        userId: String,
        journalEntryIds: List<String>
    ): GraphContext {
        val people = mutableListOf<RelatedPerson>()
        val topics = mutableListOf<RelatedTopic>()

        if (journalEntryIds.isEmpty()) {
            return GraphContext(people, topics)
        }

        val idList = journalEntryIds.joinToString(",") { "'$it'" }

        // Get related people
        try {
            val peopleQuery = """
                MATCH (j:JournalEntry)-[r:MENTIONS_PERSON]->(p:Person)
                WHERE j.id IN [$idList]
                RETURN p.name AS name, p.relationship AS relationship,
                       COUNT(*) AS mentions, AVG(r.sentiment) AS avgSentiment
                ORDER BY mentions DESC
                LIMIT 5
            """.trimIndent()

            val result = kuzuDb.execute(peopleQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                people.add(
                    RelatedPerson(
                        name = row.getValue(0).getValue<String>(),
                        relationship = row.getValue(1).getValue<String?>(),
                        mentionCount = row.getValue(2).getValue<Long>().toInt(),
                        averageSentiment = row.getValue(3).getValue<Double?>()?.toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            // Graph traversal not available
        }

        // Get related topics
        try {
            val topicsQuery = """
                MATCH (j:JournalEntry)-[r:RELATES_TO_TOPIC]->(t:Topic)
                WHERE j.id IN [$idList]
                RETURN t.name AS name, t.category AS category,
                       SUM(r.relevance) AS totalRelevance
                ORDER BY totalRelevance DESC
                LIMIT 5
            """.trimIndent()

            val result = kuzuDb.execute(topicsQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                topics.add(
                    RelatedTopic(
                        name = row.getValue(0).getValue<String>(),
                        category = row.getValue(1).getValue<String?>(),
                        relevance = row.getValue(2).getValue<Double>().toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            // Graph traversal not available
        }

        return GraphContext(people, topics)
    }

    /**
     * Fallback: keyword-only search when embeddings are unavailable.
     * Now includes all active goals, pending tasks, and recent notes to ensure
     * the coach is always aware of them even when semantic search is unavailable.
     */
    private suspend fun retrieveKeywordOnly(
        userId: String,
        query: String
    ): RetrievedContext {
        val keywords = query.split(Regex("\\s+"))
            .filter { it.length > 2 }
            .take(5)

        val results = mutableListOf<RankedDocument>()
        val now = Instant.now().toEpochMilli()

        // Retrieve journal entries using keyword search
        if (keywords.isNotEmpty()) {
            try {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "LOWER(j.content) CONTAINS LOWER('$it')"
                }

                val searchQuery = """
                    MATCH (j:JournalEntry)
                    WHERE j.userId = '$userId' AND ($keywordConditions)
                    RETURN j.id AS id, j.content AS content, j.date AS date, j.mood AS mood
                    LIMIT 20
                """.trimIndent()

                val result = kuzuDb.execute(searchQuery)
                while (result.hasNext()) {
                    val row: FlatTuple = result.getNext()
                    val content = row.getValue(1).getValue<String>()
                    val date = row.getValue(2).getValue<Long>()

                    val matchCount = keywords.count {
                        content.lowercase().contains(it.lowercase())
                    }
                    val bm25Score = matchCount.toFloat() / keywords.size
                    val recencyScore = calculateRecencyScore(date, now)

                    results.add(
                        RankedDocument(
                            id = row.getValue(0).getValue<String>(),
                            content = content,
                            date = date,
                            mood = row.getValue(3).getValue<String?>(),
                            score = bm25Score * 0.7f + recencyScore * 0.3f,
                            vectorScore = 0f,
                            bm25Score = bm25Score,
                            graphScore = 0f,
                            recencyScore = recencyScore
                        )
                    )
                }
            } catch (e: Exception) {
                // Search failed
            }
        }

        // Use keyword-based retrieval (no semantic search since embedding failed)
        val userGoals = retrieveUserGoalsKeywordOnly(userId, keywords)

        val userTasks = retrieveUserTasksKeywordOnly(userId, keywords)

        val notes = retrieveNotesKeywordOnly(userId, keywords)

        return RetrievedContext(
            journalEntries = results.sortedByDescending { it.score }.take(FINAL_RESULTS),
            atomicThoughts = emptyList(),
            goals = emptyList(),
            notes = notes,
            userGoals = userGoals,
            userTasks = userTasks,
            relatedPeople = emptyList(),
            relatedTopics = emptyList(),
            queryEmbedding = null
        )
    }

    /**
     * Retrieve user goals using keyword search (no embeddings required).
     * Used in keyword-only fallback mode when semantic search is unavailable.
     */
    private suspend fun retrieveUserGoalsKeywordOnly(userId: String, keywords: List<String>): List<RankedUserGoal> {
        val results = mutableListOf<RankedUserGoal>()

        try {
            val goalQuery = if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "(LOWER(g.title) CONTAINS LOWER('$it') OR LOWER(g.description) CONTAINS LOWER('$it'))"
                }
                """
                    MATCH (g:UserGoal)
                    WHERE g.userId = '$userId' AND g.status = 'ACTIVE' AND ($keywordConditions)
                    RETURN g.id AS id, g.title AS title, g.description AS description,
                           g.status AS status, g.priority AS priority, g.targetDate AS targetDate
                    ORDER BY CASE g.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END
                    LIMIT 10
                """.trimIndent()
            } else {
                // No keywords - return empty to match journal entry behavior
                return emptyList()
            }

            val result = kuzuDb.execute(goalQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val title = row.getValue(1).getValue<String>()
                val description = row.getValue(2).getValue<String>()

                // Calculate keyword match score
                val matchCount = keywords.count {
                    title.lowercase().contains(it.lowercase()) ||
                    description.lowercase().contains(it.lowercase())
                }
                val keywordScore = if (keywords.isNotEmpty()) matchCount.toFloat() / keywords.size else 0f

                results.add(
                    RankedUserGoal(
                        id = row.getValue(0).getValue<String>(),
                        title = title,
                        description = description,
                        status = row.getValue(3).getValue<String>(),
                        priority = row.getValue(4).getValue<String>(),
                        targetDate = row.getValue(5).getValue<String?>(),
                        score = keywordScore
                    )
                )
            }
        } catch (e: Exception) {
            // User goals not available yet
        }

        return results
    }

    /**
     * Retrieve user tasks using keyword search (no embeddings required).
     * Used in keyword-only fallback mode when semantic search is unavailable.
     */
    private suspend fun retrieveUserTasksKeywordOnly(userId: String, keywords: List<String>): List<RankedUserTask> {
        val results = mutableListOf<RankedUserTask>()

        try {
            val taskQuery = if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "(LOWER(t.title) CONTAINS LOWER('$it') OR LOWER(t.description) CONTAINS LOWER('$it'))"
                }
                """
                    MATCH (t:UserTask)
                    WHERE t.userId = '$userId' AND t.isCompleted = false AND ($keywordConditions)
                    RETURN t.id AS id, t.title AS title, t.description AS description,
                           t.isCompleted AS isCompleted, t.priority AS priority,
                           t.dueDate AS dueDate, t.linkedGoalId AS linkedGoalId
                    ORDER BY CASE t.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END
                    LIMIT 10
                """.trimIndent()
            } else {
                // No keywords - return empty to match journal entry behavior
                return emptyList()
            }

            val result = kuzuDb.execute(taskQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val title = row.getValue(1).getValue<String>()
                val description = row.getValue(2).getValue<String>()

                // Calculate keyword match score
                val matchCount = keywords.count {
                    title.lowercase().contains(it.lowercase()) ||
                    description.lowercase().contains(it.lowercase())
                }
                val keywordScore = if (keywords.isNotEmpty()) matchCount.toFloat() / keywords.size else 0f

                results.add(
                    RankedUserTask(
                        id = row.getValue(0).getValue<String>(),
                        title = title,
                        description = description,
                        isCompleted = row.getValue(3).getValue<Boolean>(),
                        priority = row.getValue(4).getValue<String>(),
                        dueDate = row.getValue(5).getValue<String?>(),
                        linkedGoalId = row.getValue(6).getValue<String?>(),
                        score = keywordScore
                    )
                )
            }
        } catch (e: Exception) {
            // User tasks not available yet
        }

        return results
    }

    /**
     * Retrieve notes using keyword search (no embeddings required).
     * Used in keyword-only fallback mode when semantic search is unavailable.
     */
    private suspend fun retrieveNotesKeywordOnly(userId: String, keywords: List<String>): List<RankedNote> {
        val results = mutableListOf<RankedNote>()

        try {
            val noteQuery = if (keywords.isNotEmpty()) {
                val keywordConditions = keywords.joinToString(" OR ") {
                    "(LOWER(n.title) CONTAINS LOWER('$it') OR LOWER(n.content) CONTAINS LOWER('$it'))"
                }
                """
                    MATCH (n:Note)
                    WHERE n.userId = '$userId' AND ($keywordConditions)
                    RETURN n.id AS id, n.title AS title, n.content AS content, n.createdAt AS createdAt
                    ORDER BY n.createdAt DESC
                    LIMIT 5
                """.trimIndent()
            } else {
                // No keywords - return empty to match journal entry behavior
                return emptyList()
            }

            val result = kuzuDb.execute(noteQuery)
            while (result.hasNext()) {
                val row: FlatTuple = result.getNext()
                val title = row.getValue(1).getValue<String>()
                val content = row.getValue(2).getValue<String>()

                // Calculate keyword match score
                val matchCount = keywords.count {
                    title.lowercase().contains(it.lowercase()) ||
                    content.lowercase().contains(it.lowercase())
                }
                val keywordScore = if (keywords.isNotEmpty()) matchCount.toFloat() / keywords.size else 0f

                results.add(
                    RankedNote(
                        id = row.getValue(0).getValue<String>(),
                        title = title,
                        content = content,
                        createdAt = row.getValue(3).getValue<Long>(),
                        score = keywordScore
                    )
                )
            }
        } catch (e: Exception) {
            // Notes not available yet
        }

        return results
    }

    /**
     * Calculate recency score using exponential decay.
     */
    private fun calculateRecencyScore(documentDate: Long, now: Long): Float {
        val daysAgo = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(documentDate),
            Instant.ofEpochMilli(now)
        ).toDouble()

        // Exponential decay: score = 2^(-daysAgo / halfLife)
        val decay = Math.pow(2.0, -daysAgo / RECENCY_HALF_LIFE_DAYS)
        return max(0f, min(1f, decay.toFloat()))
    }

    /**
     * Convert FloatArray to Kuzu array literal.
     */
    private fun FloatArray.toKuzuArray(): String {
        return "[${this.joinToString(",")}]"
    }
}

// Data classes for RAG results

data class RetrievedContext(
    val journalEntries: List<RankedDocument>,
    val atomicThoughts: List<RankedThought>,
    val goals: List<RankedGoal>,
    val notes: List<RankedNote>,
    val userGoals: List<RankedUserGoal>,
    val userTasks: List<RankedUserTask>,
    val relatedPeople: List<RelatedPerson>,
    val relatedTopics: List<RelatedTopic>,
    val queryEmbedding: FloatArray?
) {
    companion object {
        fun empty() = RetrievedContext(
            journalEntries = emptyList(),
            atomicThoughts = emptyList(),
            goals = emptyList(),
            notes = emptyList(),
            userGoals = emptyList(),
            userTasks = emptyList(),
            relatedPeople = emptyList(),
            relatedTopics = emptyList(),
            queryEmbedding = null
        )
    }
}

data class RankedDocument(
    val id: String,
    val content: String,
    val date: Long,
    val mood: String?,
    val score: Float,
    val vectorScore: Float,
    val bm25Score: Float,
    val graphScore: Float,
    val recencyScore: Float
)

data class RankedThought(
    val id: String,
    val content: String,
    val type: String,
    val confidence: Float,
    val importance: Int,
    val score: Float
)

data class RankedGoal(
    val id: String,
    val description: String,
    val status: String,
    val score: Float
)

data class RankedNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val score: Float
)

data class RankedUserGoal(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val targetDate: String?,
    val score: Float
)

data class RankedUserTask(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val priority: String,
    val dueDate: String?,
    val linkedGoalId: String?,
    val score: Float
)

data class RelatedPerson(
    val name: String,
    val relationship: String?,
    val mentionCount: Int,
    val averageSentiment: Float?
)

data class RelatedTopic(
    val name: String,
    val category: String?,
    val relevance: Float
)

private data class ScoredDocument(
    val id: String,
    val content: String,
    val date: Long,
    val mood: String?,
    val vectorScore: Float,
    val bm25Score: Float,
    val graphScore: Float
)

private data class ScoredThought(
    val id: String,
    val content: String,
    val type: String,
    val confidence: Float,
    val importance: Int,
    val vectorScore: Float,
    val bm25Score: Float
)

private data class ScoredUserGoal(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val targetDate: String?,
    val vectorScore: Float,
    val bm25Score: Float
)

private data class ScoredUserTask(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val priority: String,
    val dueDate: String?,
    val linkedGoalId: String?,
    val vectorScore: Float,
    val bm25Score: Float
)

private data class ScoredNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val vectorScore: Float,
    val bm25Score: Float
)

private data class GraphContext(
    val people: List<RelatedPerson>,
    val topics: List<RelatedTopic>
)

// Data classes for all (non-semantic) retrieval

data class AllUserGoal(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val targetDate: String?
)

data class AllUserTask(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val priority: String,
    val dueDate: String?,
    val linkedGoalId: String?
)

data class AllNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long
)
