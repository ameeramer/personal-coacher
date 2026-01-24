package com.personalcoacher.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.personalcoacher.data.local.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomic Thought Extractor
 *
 * Uses Claude API to extract structured atomic thoughts from journal entries.
 * This follows the "Atomic Thought Protocol" for knowledge graph construction:
 *
 * - Each thought is a singular concept that can be independently vectorized
 * - Thoughts have metadata (type, confidence, sentiment, importance)
 * - Entity references (people, places, topics) are extracted
 * - Goal references are identified
 *
 * This extraction happens asynchronously in the background after journal entries
 * are saved, building up the knowledge graph over time.
 */
@Singleton
class AtomicThoughtExtractor @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val tokenManager: TokenManager,
    private val gson: Gson
) {
    companion object {
        private const val EXTRACTION_PROMPT_JOURNAL = """You are an expert at extracting structured knowledge from personal journal entries. Your task is to identify and extract:

1. **Atomic Thoughts**: Singular concepts, beliefs, insights, or patterns expressed in the entry. Each thought should be self-contained and independently meaningful.

2. **People Mentioned**: Names of people referenced, with their apparent relationship to the writer.

3. **Topics/Themes**: Main themes or topics discussed (e.g., work, health, relationships, hobbies).

4. **Goal References**: Any goals, aspirations, or intentions mentioned.

For each atomic thought, classify its type:
- `belief`: A belief or value the person holds
- `insight`: A realization or new understanding
- `pattern`: A recurring behavior or tendency
- `memory`: A specific memory or event
- `goal`: An intention or aspiration
- `concern`: A worry or anxiety
- `gratitude`: Something they're thankful for

Also rate:
- `confidence`: How clearly this is expressed (0.0-1.0)
- `sentiment`: Emotional valence (-1.0 negative to 1.0 positive)
- `importance`: Significance to the person (1-5)

Respond ONLY with valid JSON in this exact format:
{
  "thoughts": [
    {
      "content": "The extracted thought as a complete sentence",
      "type": "belief|insight|pattern|memory|goal|concern|gratitude",
      "confidence": 0.0-1.0,
      "sentiment": -1.0 to 1.0,
      "importance": 1-5
    }
  ],
  "people": [
    {
      "name": "Person's name",
      "relationship": "family|friend|colleague|acquaintance|other",
      "sentiment": -1.0 to 1.0
    }
  ],
  "topics": [
    {
      "name": "Topic name",
      "category": "work|health|relationships|hobbies|personal_growth|finances|other",
      "relevance": 0.0-1.0
    }
  ],
  "goalReferences": [
    {
      "description": "The goal or intention",
      "type": "progress|obstacle|new_goal|completed",
      "mentioned": "How it was referenced"
    }
  ]
}"""

        private const val EXTRACTION_PROMPT_NOTE = """You are an expert at extracting structured knowledge from personal notes. Your task is to identify and extract key insights, topics, and any referenced people or goals from this note.

For each atomic thought, classify its type:
- `belief`: A belief or value the person holds
- `insight`: A realization or new understanding
- `pattern`: A recurring behavior or tendency
- `note`: A recorded fact or piece of information
- `goal`: An intention or aspiration
- `idea`: A creative concept or possibility

Also rate:
- `confidence`: How clearly this is expressed (0.0-1.0)
- `sentiment`: Emotional valence (-1.0 negative to 1.0 positive)
- `importance`: Significance to the person (1-5)

Respond ONLY with valid JSON in this exact format:
{
  "thoughts": [
    {
      "content": "The extracted thought as a complete sentence",
      "type": "belief|insight|pattern|note|goal|idea",
      "confidence": 0.0-1.0,
      "sentiment": -1.0 to 1.0,
      "importance": 1-5
    }
  ],
  "people": [
    {
      "name": "Person's name",
      "relationship": "family|friend|colleague|acquaintance|other",
      "sentiment": -1.0 to 1.0
    }
  ],
  "topics": [
    {
      "name": "Topic name",
      "category": "work|health|relationships|hobbies|personal_growth|finances|other",
      "relevance": 0.0-1.0
    }
  ],
  "goalReferences": []
}"""

        private const val EXTRACTION_PROMPT_GOAL = """You are an expert at extracting structured knowledge from personal goals. Your task is to analyze this goal and extract insights about the person's values, motivations, and related topics.

For each atomic thought, classify its type:
- `goal`: The main goal intention
- `belief`: A belief or value that motivates this goal
- `insight`: An understanding about why this goal matters
- `pattern`: Any patterns related to this goal area

Also rate:
- `confidence`: How clearly this is expressed (0.0-1.0)
- `sentiment`: Emotional valence (-1.0 negative to 1.0 positive)
- `importance`: Significance to the person (1-5)

Respond ONLY with valid JSON in this exact format:
{
  "thoughts": [
    {
      "content": "The extracted thought as a complete sentence",
      "type": "goal|belief|insight|pattern",
      "confidence": 0.0-1.0,
      "sentiment": -1.0 to 1.0,
      "importance": 1-5
    }
  ],
  "people": [],
  "topics": [
    {
      "name": "Topic name",
      "category": "work|health|relationships|hobbies|personal_growth|finances|other",
      "relevance": 0.0-1.0
    }
  ],
  "goalReferences": [
    {
      "description": "The goal or intention",
      "type": "new_goal",
      "mentioned": "Direct goal entry"
    }
  ]
}"""

        private const val EXTRACTION_PROMPT_TASK = """You are an expert at extracting structured knowledge from personal tasks. Your task is to analyze this task and extract insights about priorities, work patterns, and related topics.

For each atomic thought, classify its type:
- `task`: The task intention
- `priority`: What this task's priority reveals about values
- `pattern`: Any patterns related to task management
- `concern`: Any implied concerns or pressures

Also rate:
- `confidence`: How clearly this is expressed (0.0-1.0)
- `sentiment`: Emotional valence (-1.0 negative to 1.0 positive)
- `importance`: Significance to the person (1-5)

Respond ONLY with valid JSON in this exact format:
{
  "thoughts": [
    {
      "content": "The extracted thought as a complete sentence",
      "type": "task|priority|pattern|concern",
      "confidence": 0.0-1.0,
      "sentiment": -1.0 to 1.0,
      "importance": 1-5
    }
  ],
  "people": [],
  "topics": [
    {
      "name": "Topic name",
      "category": "work|health|relationships|hobbies|personal_growth|finances|other",
      "relevance": 0.0-1.0
    }
  ],
  "goalReferences": []
}"""
    }

    /**
     * Extract atomic thoughts and entities from a journal entry.
     *
     * @param entryContent The journal entry text
     * @param entryDate The date of the entry (for context)
     * @return ExtractionResult containing structured knowledge
     */
    suspend fun extract(entryContent: String, entryDate: String? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val contextNote = entryDate?.let { "Journal entry from $it:" } ?: "Journal entry:"
        extractWithPrompt(EXTRACTION_PROMPT_JOURNAL, "$contextNote\n\n$entryContent")
    }

    /**
     * Extract atomic thoughts from a note.
     *
     * @param noteTitle The note title
     * @param noteContent The note content
     * @return ExtractionResult containing structured knowledge
     */
    suspend fun extractFromNote(noteTitle: String, noteContent: String): ExtractionResult = withContext(Dispatchers.IO) {
        extractWithPrompt(EXTRACTION_PROMPT_NOTE, "Note: $noteTitle\n\n$noteContent")
    }

    /**
     * Extract atomic thoughts from a user goal.
     *
     * @param goalTitle The goal title
     * @param goalDescription The goal description
     * @param priority The goal priority
     * @param targetDate Optional target date
     * @return ExtractionResult containing structured knowledge
     */
    suspend fun extractFromGoal(
        goalTitle: String,
        goalDescription: String,
        priority: String,
        targetDate: String? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val content = buildString {
            appendLine("Goal: $goalTitle")
            appendLine("Description: $goalDescription")
            appendLine("Priority: $priority")
            targetDate?.let { appendLine("Target Date: $it") }
        }
        extractWithPrompt(EXTRACTION_PROMPT_GOAL, content)
    }

    /**
     * Extract atomic thoughts from a user task.
     *
     * @param taskTitle The task title
     * @param taskDescription The task description
     * @param priority The task priority
     * @param dueDate Optional due date
     * @param linkedGoalTitle Optional linked goal title for context
     * @return ExtractionResult containing structured knowledge
     */
    suspend fun extractFromTask(
        taskTitle: String,
        taskDescription: String,
        priority: String,
        dueDate: String? = null,
        linkedGoalTitle: String? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val content = buildString {
            appendLine("Task: $taskTitle")
            appendLine("Description: $taskDescription")
            appendLine("Priority: $priority")
            dueDate?.let { appendLine("Due Date: $it") }
            linkedGoalTitle?.let { appendLine("Related Goal: $it") }
        }
        extractWithPrompt(EXTRACTION_PROMPT_TASK, content)
    }

    /**
     * Generic extraction with a specific prompt.
     */
    private suspend fun extractWithPrompt(systemPrompt: String, userContent: String): ExtractionResult {
        val apiKey = tokenManager.getClaudeApiKeySync()
            ?: throw ExtractionException("Claude API key not configured")

        val request = ClaudeMessageRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 2048,
            system = systemPrompt,
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = userContent
                )
            )
        )

        val response = claudeApi.sendMessage(apiKey = apiKey, request = request)

        if (!response.isSuccessful || response.body() == null) {
            throw ExtractionException("Failed to extract: ${response.message()}")
        }

        val responseText = response.body()!!.content.firstOrNull()?.text
            ?: throw ExtractionException("Empty response from Claude")

        // Parse the JSON response
        try {
            // Extract JSON from potential markdown code blocks
            val jsonText = extractJson(responseText)
            gson.fromJson(jsonText, ExtractionResult::class.java)
        } catch (e: Exception) {
            throw ExtractionException("Failed to parse extraction result: ${e.message}")
        }
    }

    /**
     * Extract JSON from response that might be wrapped in markdown code blocks.
     */
    private fun extractJson(text: String): String {
        // Try to extract JSON from code blocks
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = codeBlockRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // If no code block, try to find JSON object directly
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1)
        }

        return text.trim()
    }
}

// Data classes for extraction results

data class ExtractionResult(
    val thoughts: List<ExtractedThought> = emptyList(),
    val people: List<ExtractedPerson> = emptyList(),
    val topics: List<ExtractedTopic> = emptyList(),
    @SerializedName("goalReferences")
    val goalReferences: List<ExtractedGoalReference> = emptyList()
)

data class ExtractedThought(
    val content: String,
    val type: String,
    val confidence: Float,
    val sentiment: Float,
    val importance: Int
)

data class ExtractedPerson(
    val name: String,
    val relationship: String?,
    val sentiment: Float?
)

data class ExtractedTopic(
    val name: String,
    val category: String?,
    val relevance: Float
)

data class ExtractedGoalReference(
    val description: String,
    val type: String,
    val mentioned: String?
)

class ExtractionException(message: String) : Exception(message)
