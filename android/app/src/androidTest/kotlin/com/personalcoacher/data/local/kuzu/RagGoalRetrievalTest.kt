package com.personalcoacher.data.local.kuzu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kuzudb.Connection
import com.kuzudb.Database
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Integration test for the RAG goal retrieval pipeline.
 *
 * This test verifies that when a UserGoal and its AtomicThought are added to the Kùzu
 * graph database, the keyword/BM25 search queries correctly find and return the goal.
 *
 * The test directly executes the same Cypher queries that RagEngine.retrieveUserGoals()
 * and RagEngine.retrieveAtomicThoughts() use, to verify the pipeline works.
 *
 * This test does NOT require API keys because:
 * - It directly inserts data into Kùzu (bypassing VoyageEmbeddingService)
 * - It tests keyword search which doesn't require embeddings
 * - It uses the exact same queries as the production code
 */
@RunWith(AndroidJUnit4::class)
class RagGoalRetrievalTest {

    private lateinit var context: Context
    private lateinit var database: Database
    private lateinit var connection: Connection
    private lateinit var dbFile: File

    companion object {
        private const val TEST_USER_ID = "test-user-123"
        private const val TEST_GOAL_ID = "test-goal-456"
        private const val DATABASE_FILE = "test_kuzu_rag.db"
        const val EMBEDDING_DIMENSIONS = 1024
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Clean up any existing test database
        dbFile = File(context.filesDir, DATABASE_FILE)
        dbFile.delete()
        File(context.filesDir, "$DATABASE_FILE.wal").delete()
        File(context.filesDir, "$DATABASE_FILE.lock").delete()

        try {
            // Initialize Kuzu database directly (not through KuzuDatabaseManager)
            android.util.Log.d("RagGoalRetrievalTest", "Initializing Kuzu at: ${dbFile.absolutePath}")
            database = Database(dbFile.absolutePath)
            android.util.Log.d("RagGoalRetrievalTest", "Database created, creating connection...")
            connection = Connection(database)
            android.util.Log.d("RagGoalRetrievalTest", "Connection created, creating schema...")

            // Create minimal schema for testing
            createSchema()
            android.util.Log.d("RagGoalRetrievalTest", "Schema created successfully")
        } catch (e: Exception) {
            android.util.Log.e("RagGoalRetrievalTest", "Setup failed: ${e.message}", e)
            throw e
        }
    }

    @After
    fun teardown() {
        // Defensive teardown - only close if initialized
        if (::connection.isInitialized) {
            try {
                connection.close()
            } catch (e: Exception) {
                // Ignore close errors in teardown
            }
        }
        if (::database.isInitialized) {
            try {
                database.close()
            } catch (e: Exception) {
                // Ignore close errors in teardown
            }
        }
        if (::dbFile.isInitialized && dbFile.exists()) {
            dbFile.delete()
        }
        if (::context.isInitialized) {
            File(context.filesDir, "$DATABASE_FILE.wal").delete()
            File(context.filesDir, "$DATABASE_FILE.lock").delete()
        }
    }

    private fun createSchema() {
        // Create UserGoal node table
        connection.query("""
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

        // Create AtomicThought node table
        connection.query("""
            CREATE NODE TABLE IF NOT EXISTS AtomicThought(
                id STRING PRIMARY KEY,
                userId STRING,
                content STRING,
                thoughtType STRING,
                confidence DOUBLE,
                sentiment DOUBLE,
                importance INT64,
                sourceType STRING,
                sourceId STRING,
                createdAt INT64,
                embedding FLOAT[$EMBEDDING_DIMENSIONS],
                embeddingModel STRING
            )
        """.trimIndent())

        // Create relationship table
        connection.query("""
            CREATE REL TABLE IF NOT EXISTS EXTRACTED_FROM_GOAL(
                FROM AtomicThought TO UserGoal,
                extractedAt INT64,
                confidence DOUBLE
            )
        """.trimIndent())
    }

    /**
     * Test: Verify Kuzu database is initialized and schema is created.
     */
    @Test
    fun testDatabaseInitialization() {
        // Verify we can query the schema
        val result = connection.query("MATCH (g:UserGoal) RETURN count(*)")
        assertTrue("Should be able to query UserGoal table", result.hasNext())
    }

    /**
     * Test: Adding a UserGoal and verifying it exists in the database.
     */
    @Test
    fun testUserGoalInsertionAndQuery() {
        val now = Instant.now().toEpochMilli()

        // Insert a UserGoal
        connection.query("""
            CREATE (g:UserGoal {
                id: '$TEST_GOAL_ID',
                userId: '$TEST_USER_ID',
                title: 'Run 15km',
                description: 'I want to be able to run 15 kilometers without stopping',
                targetDate: '2026-06-01',
                status: 'ACTIVE',
                priority: 'HIGH',
                createdAt: $now,
                updatedAt: $now,
                embedding: NULL,
                embeddingModel: NULL
            })
        """.trimIndent())

        // Verify with simple query
        val result = connection.query("""
            MATCH (g:UserGoal {userId: '$TEST_USER_ID', status: 'ACTIVE'})
            RETURN g.id AS id, g.title AS title
        """.trimIndent())

        assertTrue("Query should return results", result.hasNext())
        val row = result.getNext()
        assertEquals("ID should match", TEST_GOAL_ID, row.getValue(0).getValue<String>())
        assertEquals("Title should match", "Run 15km", row.getValue(1).getValue<String>())
    }

    /**
     * Test: Keyword search on UserGoal using the same query pattern as RagEngine.
     *
     * This verifies that when user asks "what are my goals", the BM25/keyword
     * search finds goals with matching keywords in title or description.
     */
    @Test
    fun testUserGoalKeywordSearch() {
        val now = Instant.now().toEpochMilli()

        // Insert a UserGoal
        connection.query("""
            CREATE (g:UserGoal {
                id: '$TEST_GOAL_ID',
                userId: '$TEST_USER_ID',
                title: 'Run 15km marathon',
                description: 'I want to train for and complete a marathon by end of year',
                targetDate: '2026-12-31',
                status: 'ACTIVE',
                priority: 'HIGH',
                createdAt: $now,
                updatedAt: $now,
                embedding: NULL,
                embeddingModel: NULL
            })
        """.trimIndent())

        // Test with a keyword that IS in the content
        val marathonKeywords = listOf("marathon", "train")
        val marathonConditions = marathonKeywords.joinToString(" OR ") {
            "(LOWER(g.title) CONTAINS LOWER('$it') OR LOWER(g.description) CONTAINS LOWER('$it'))"
        }

        val marathonQuery = """
            MATCH (g:UserGoal)
            WHERE g.userId = '$TEST_USER_ID' AND g.status = 'ACTIVE' AND ($marathonConditions)
            RETURN g.id AS id, g.title AS title
            LIMIT 10
        """.trimIndent()

        val marathonResult = connection.query(marathonQuery)
        assertTrue(
            "Keyword search for 'marathon' should find the goal",
            marathonResult.hasNext()
        )
    }

    /**
     * Test: AtomicThought creation and keyword search.
     *
     * When a UserGoal is synced, KuzuSyncService creates an AtomicThought with content
     * like "Goal: Run 15km. Description... Priority: HIGH (Target: 2026-06-01)"
     *
     * This AtomicThought should be findable via keyword search when user asks
     * "what are my goals" because it contains the word "Goal".
     */
    @Test
    fun testAtomicThoughtFromGoalKeywordSearch() {
        val now = Instant.now().toEpochMilli()
        val thoughtId = "goal_thought_$TEST_GOAL_ID"

        // Create the AtomicThought exactly as KuzuSyncService.createDirectAtomicThoughtFromGoal() does
        val directContent = "Goal: Run 15km. I want to be able to run 15 kilometers without stopping Priority: HIGH (Target: 2026-06-01)"

        connection.query("""
            CREATE (t:AtomicThought {
                id: '$thoughtId',
                userId: '$TEST_USER_ID',
                content: '${escapeString(directContent)}',
                thoughtType: 'goal',
                confidence: 1.0,
                sentiment: 0.5,
                importance: 4,
                sourceType: 'goal',
                sourceId: '$TEST_GOAL_ID',
                createdAt: $now,
                embedding: NULL,
                embeddingModel: NULL
            })
        """.trimIndent())

        // Execute the EXACT same keyword search query as RagEngine.retrieveAtomicThoughts()
        // Query: "what are my goals" -> keywords: ["what", "are", "goals"]
        val keywords = listOf("what", "are", "goals")
        val keywordConditions = keywords.joinToString(" OR ") {
            "LOWER(t.content) CONTAINS LOWER('$it')"
        }

        val bm25Query = """
            MATCH (t:AtomicThought)
            WHERE t.userId = '$TEST_USER_ID' AND ($keywordConditions)
            RETURN t.id AS id, t.content AS content, t.thoughtType AS type,
                   t.confidence AS confidence, t.importance AS importance
            LIMIT 20
        """.trimIndent()

        val result = connection.query(bm25Query)

        assertTrue(
            "Keyword search for 'goals' should find AtomicThought starting with 'Goal:'",
            result.hasNext()
        )

        val row = result.getNext()
        val content = row.getValue(1).getValue<String>()
        assertTrue(
            "Content should start with 'Goal:'",
            content.startsWith("Goal:")
        )
    }

    /**
     * Test: Full pipeline simulation - UserGoal + AtomicThought + keyword search.
     *
     * This is the critical integration test that simulates what should happen
     * when a user creates a goal and then asks "what are my goals".
     */
    @Test
    fun testFullGoalRetrievalPipeline() {
        val now = Instant.now().toEpochMilli()
        val thoughtId = "goal_thought_$TEST_GOAL_ID"

        // 1. Create UserGoal (as GoalRepository does)
        connection.query("""
            CREATE (g:UserGoal {
                id: '$TEST_GOAL_ID',
                userId: '$TEST_USER_ID',
                title: 'Run 15km',
                description: 'I want to be able to run 15 kilometers without stopping',
                targetDate: '2026-06-01',
                status: 'ACTIVE',
                priority: 'HIGH',
                createdAt: $now,
                updatedAt: $now,
                embedding: NULL,
                embeddingModel: NULL
            })
        """.trimIndent())

        // 2. Create AtomicThought (as KuzuSyncService.createDirectAtomicThoughtFromGoal does)
        val directContent = "Goal: Run 15km. I want to be able to run 15 kilometers without stopping Priority: HIGH (Target: 2026-06-01)"

        connection.query("""
            CREATE (t:AtomicThought {
                id: '$thoughtId',
                userId: '$TEST_USER_ID',
                content: '${escapeString(directContent)}',
                thoughtType: 'goal',
                confidence: 1.0,
                sentiment: 0.5,
                importance: 4,
                sourceType: 'goal',
                sourceId: '$TEST_GOAL_ID',
                createdAt: $now,
                embedding: NULL,
                embeddingModel: NULL
            })
        """.trimIndent())

        // 3. Create EXTRACTED_FROM_GOAL relationship
        connection.query("""
            MATCH (t:AtomicThought {id: '$thoughtId'}), (g:UserGoal {id: '$TEST_GOAL_ID'})
            CREATE (t)-[:EXTRACTED_FROM_GOAL {extractedAt: $now, confidence: 1.0}]->(g)
        """.trimIndent())

        // 4. Simulate RagEngine.retrieveContext with query "what are my goals"
        val keywords = "what are my goals".split(Regex("\\s+")).filter { it.length > 2 }
        // Keywords: ["what", "are", "goals"]

        // 4a. Search AtomicThoughts (as RagEngine.retrieveAtomicThoughts does)
        val thoughtKeywordConditions = keywords.joinToString(" OR ") {
            "LOWER(t.content) CONTAINS LOWER('$it')"
        }
        val thoughtQuery = """
            MATCH (t:AtomicThought)
            WHERE t.userId = '$TEST_USER_ID' AND ($thoughtKeywordConditions)
            RETURN t.id AS id, t.content AS content, t.thoughtType AS type
            LIMIT 20
        """.trimIndent()

        val thoughtResult = connection.query(thoughtQuery)
        var foundGoalThought = false
        while (thoughtResult.hasNext()) {
            val row = thoughtResult.getNext()
            val content = row.getValue(1).getValue<String>()
            if (content.contains("Goal:") && content.contains("Run 15km")) {
                foundGoalThought = true
                break
            }
        }

        // 4b. Search UserGoals (as RagEngine.retrieveUserGoals does)
        // Note: keyword "goals" won't match title "Run 15km" or description directly
        // But we should verify the query works
        val goalKeywordConditions = keywords.joinToString(" OR ") {
            "(LOWER(g.title) CONTAINS LOWER('$it') OR LOWER(g.description) CONTAINS LOWER('$it'))"
        }
        val goalQuery = """
            MATCH (g:UserGoal)
            WHERE g.userId = '$TEST_USER_ID' AND g.status = 'ACTIVE' AND ($goalKeywordConditions)
            RETURN g.id AS id, g.title AS title
            LIMIT 10
        """.trimIndent()

        val goalResult = connection.query(goalQuery)
        val foundGoalDirect = goalResult.hasNext()

        // 5. Assert: The goal should be found through the AtomicThought
        assertTrue(
            "Goal should be retrievable via AtomicThought keyword search. " +
            "foundGoalThought=$foundGoalThought, foundGoalDirect=$foundGoalDirect. " +
            "The AtomicThought contains 'Goal:' which should match keyword 'goals'.",
            foundGoalThought || foundGoalDirect
        )
    }

    /**
     * Test: Verify the CONTAINS operator works as expected in Kùzu.
     * This is a low-level test to ensure our keyword matching logic is correct.
     */
    @Test
    fun testKuzuContainsOperator() {
        val now = Instant.now().toEpochMilli()

        // Create test data
        connection.query("""
            CREATE (t:AtomicThought {
                id: 'test-contains',
                userId: '$TEST_USER_ID',
                content: 'Goal: Test the CONTAINS operator with Goals keyword',
                thoughtType: 'test',
                confidence: 1.0,
                sentiment: 0.0,
                importance: 1,
                sourceType: 'test',
                sourceId: 'test',
                createdAt: $now,
                embedding: NULL,
                embeddingModel: NULL
            })
        """.trimIndent())

        // Test case-insensitive CONTAINS
        val result = connection.query("""
            MATCH (t:AtomicThought)
            WHERE t.userId = '$TEST_USER_ID' AND LOWER(t.content) CONTAINS LOWER('goals')
            RETURN t.id AS id, t.content AS content
        """.trimIndent())

        assertTrue("CONTAINS should find 'Goals' when searching for 'goals' (case-insensitive)", result.hasNext())

        val row = result.getNext()
        assertTrue(
            "Content should contain 'Goals'",
            row.getValue(1).getValue<String>().contains("Goals")
        )
    }

    private fun escapeString(s: String): String {
        return s.replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\"", "\\\"")
    }
}
