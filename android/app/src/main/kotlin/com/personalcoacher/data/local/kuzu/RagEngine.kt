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
     * @return RetrievedContext containing ranked relevant documents
     */
    suspend fun retrieveContext(
        userId: String,
        query: String,
        includeAtomicThoughts: Boolean = true,
        includeGoals: Boolean = true
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

        // Get graph-connected context
        val graphContext = retrieveGraphContext(userId, journalResults.map { it.id })

        RetrievedContext(
            journalEntries = journalResults.take(FINAL_RESULTS),
            atomicThoughts = thoughtResults.take(FINAL_RESULTS / 2),
            goals = goalResults.take(3),
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
     * Retrieve atomic thoughts using semantic search.
     */
    private suspend fun retrieveAtomicThoughts(
        userId: String,
        query: String,
        queryEmbedding: FloatArray
    ): List<RankedThought> {
        val results = mutableListOf<RankedThought>()

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
                results.add(
                    RankedThought(
                        id = row.getValue(0).getValue<String>(),
                        content = row.getValue(1).getValue<String>(),
                        type = row.getValue(2).getValue<String>(),
                        confidence = row.getValue(3).getValue<Double>().toFloat(),
                        importance = row.getValue(4).getValue<Long>().toInt(),
                        score = row.getValue(5).getValue<Double>().toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            // Atomic thoughts not available yet
        }

        return results
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
     */
    private suspend fun retrieveKeywordOnly(
        userId: String,
        query: String
    ): RetrievedContext {
        val keywords = query.split(Regex("\\s+"))
            .filter { it.length > 2 }
            .take(5)

        if (keywords.isEmpty()) {
            return RetrievedContext.empty()
        }

        val results = mutableListOf<RankedDocument>()
        val now = Instant.now().toEpochMilli()

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

        return RetrievedContext(
            journalEntries = results.sortedByDescending { it.score }.take(FINAL_RESULTS),
            atomicThoughts = emptyList(),
            goals = emptyList(),
            relatedPeople = emptyList(),
            relatedTopics = emptyList(),
            queryEmbedding = null
        )
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
    val relatedPeople: List<RelatedPerson>,
    val relatedTopics: List<RelatedTopic>,
    val queryEmbedding: FloatArray?
) {
    companion object {
        fun empty() = RetrievedContext(
            journalEntries = emptyList(),
            atomicThoughts = emptyList(),
            goals = emptyList(),
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

private data class GraphContext(
    val people: List<RelatedPerson>,
    val topics: List<RelatedTopic>
)
