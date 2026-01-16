package com.personalcoacher.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.remote.dto.EventSuggestionDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for analyzing journal entries to detect events using local Claude API calls.
 * This replaces the previous Vercel backend dependency.
 */
@Singleton
class EventAnalysisService @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val tokenManager: TokenManager,
    private val gson: Gson
) {
    companion object {
        private const val ANALYZE_SYSTEM_PROMPT = """You are an AI assistant that analyzes journal entries to detect mentions of events, meetings, appointments, or scheduled activities. Your task is to identify any upcoming or past events mentioned in the journal text and extract structured information about them.

When analyzing the journal entry, look for:
- Scheduled meetings or appointments
- Social events or gatherings
- Work-related events or deadlines
- Personal commitments or plans
- Recurring activities with specific times
- Travel plans or trips

For each event detected, extract:
- title: A concise title for the event
- description: A brief description (optional, only if there's additional context)
- startTime: The date/time of the event in ISO 8601 format. If no year is specified, assume the current year or next occurrence
- endTime: The end time if mentioned (optional)
- isAllDay: Whether this is an all-day event (no specific time mentioned)
- location: The location if mentioned (optional)

Important:
- Today's date is provided in the user message
- For relative dates like "tomorrow", "next week", "this Friday", calculate the actual date
- If only a date is mentioned without a time, set isAllDay to true
- If a specific time is mentioned, set isAllDay to false
- Return an empty array if no events are detected
- Be conservative - only extract events that are clearly mentioned, not implied activities

Respond ONLY with a JSON object in this exact format:
{
  "suggestions": [
    {
      "title": "Event title",
      "description": "Optional description",
      "startTime": "2024-01-15T14:00:00.000Z",
      "endTime": "2024-01-15T15:00:00.000Z",
      "isAllDay": false,
      "location": "Optional location"
    }
  ]
}

If no events are detected, respond with: { "suggestions": [] }"""
    }

    /**
     * Analyzes a journal entry for events using Claude API directly.
     * @return EventAnalysisResult with suggestions or error message
     */
    suspend fun analyzeJournalEntry(content: String): EventAnalysisResult {
        // Check for API key first
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            return EventAnalysisResult.Error("Please configure your Claude API key in Settings to use event analysis")
        }

        return try {
            val today = LocalDate.now().toString()
            val userPrompt = "Today's date is $today. Please analyze this journal entry for any events or scheduled activities:\n\n$content"

            val response = claudeApi.sendMessage(
                apiKey = apiKey,
                request = ClaudeMessageRequest(
                    system = ANALYZE_SYSTEM_PROMPT,
                    messages = listOf(ClaudeMessage(role = "user", content = userPrompt)),
                    maxTokens = 1024
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val textContent = result.content.firstOrNull()?.text

                if (textContent.isNullOrBlank()) {
                    return EventAnalysisResult.Success(emptyList())
                }

                // Parse the JSON response
                try {
                    val analysisResponse = gson.fromJson(textContent, AnalysisResponse::class.java)
                    EventAnalysisResult.Success(analysisResponse.suggestions ?: emptyList())
                } catch (e: Exception) {
                    // If parsing fails, return empty suggestions
                    EventAnalysisResult.Success(emptyList())
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = when {
                    errorBody?.contains("invalid_api_key") == true ->
                        "Invalid API key. Please check your Claude API key in Settings."
                    response.code() == 401 ->
                        "Authentication failed. Please check your Claude API key in Settings."
                    response.code() == 429 ->
                        "Rate limit exceeded. Please try again later."
                    else ->
                        "Failed to analyze journal entry: ${response.message()}"
                }
                EventAnalysisResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            EventAnalysisResult.Error("Failed to analyze journal entry: ${e.localizedMessage ?: "Network error"}")
        }
    }
}

/**
 * Result of event analysis operation
 */
sealed class EventAnalysisResult {
    data class Success(val suggestions: List<EventSuggestionDto>) : EventAnalysisResult()
    data class Error(val message: String) : EventAnalysisResult()
}

/**
 * Response from Claude for event analysis
 */
data class AnalysisResponse(
    @SerializedName("suggestions") val suggestions: List<EventSuggestionDto>?
)
