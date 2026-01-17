package com.personalcoacher.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.util.Resource
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating AI-powered dynamic web apps using Claude API.
 * Each generated app is a complete, self-contained HTML/CSS/JS application
 * tailored to the user's emotional state and journal content.
 */
@Singleton
class DailyAppGenerationService @Inject constructor(
    private val claudeApiService: ClaudeApiService
) {
    private val gson = Gson()

    companion object {
        private const val TAG = "DailyAppGeneration"
        private const val MAX_TOKENS = 8192 // Enough for a complete web app
    }

    /**
     * Result of app generation containing all app metadata and code.
     */
    data class GeneratedApp(
        val title: String,
        val description: String,
        val htmlCode: String,
        val journalContext: String?
    )

    /**
     * Generate a new daily app based on recent journal entries.
     */
    suspend fun generateApp(
        apiKey: String,
        recentEntries: List<JournalEntry>
    ): Resource<GeneratedApp> {
        return try {
            val systemPrompt = buildSystemPrompt()
            val userPrompt = buildUserPrompt(recentEntries)

            val request = ClaudeMessageRequest(
                model = "claude-sonnet-4-20250514",
                maxTokens = MAX_TOKENS,
                system = systemPrompt,
                messages = listOf(
                    ClaudeMessage(role = "user", content = userPrompt)
                ),
                stream = false
            )

            val response = claudeApiService.sendMessage(
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val content = responseBody.content.firstOrNull()?.text
                    ?: return Resource.error("Empty response from Claude")

                parseGeneratedApp(content, recentEntries)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "API error: $errorBody")
                Resource.error("Failed to generate app: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Resource.error("Generation failed: ${e.localizedMessage}")
        }
    }

    private fun buildSystemPrompt(): String {
        return """
You are a creative personal growth tool designer. Your task is to generate a UNIQUE, interactive web app that helps the user based on their recent journal entries.

CRITICAL REQUIREMENTS:
1. Generate a complete, self-contained HTML file with ALL CSS and JavaScript inline
2. The app should be interactive and engaging
3. It should take 2-5 minutes to use meaningfully
4. It MUST be relevant to the user's current emotional state and journal content
5. Be CREATIVE and SURPRISING - avoid generic patterns

TECHNICAL REQUIREMENTS:
1. Include all CSS in a <style> tag in the <head>
2. Include all JavaScript in a <script> tag before </body>
3. Use modern CSS (flexbox, grid, animations, gradients)
4. Make it mobile-friendly (use viewport meta tag, responsive design)
5. DO NOT use any external resources (no CDN links, no external fonts, no images)
6. DO NOT use localStorage, cookies, or sessionStorage - use the Android bridge for persistence

DATA PERSISTENCE (IMPORTANT):
The app runs in an Android WebView with these JavaScript methods available:
- Android.saveData(key, value) - Save a string value persistently
- Android.loadData(key) - Load a saved value (returns "" if not found)
- Android.getAllData() - Get all saved data as JSON string
- Android.clearData() - Clear all saved data for this app
- Android.getAppInfo() - Get app metadata as JSON (title, createdAt)

For complex data, use JSON.stringify() and JSON.parse():
  // Save
  Android.saveData("state", JSON.stringify(yourObject));
  // Load
  let state = JSON.parse(Android.loadData("state") || "{}");

DESIGN PRINCIPLES:
1. Match the visual style to the emotional tone (calm = soft colors, energetic = bold colors)
2. Use smooth animations and micro-interactions
3. Make the experience feel premium and crafted
4. Include encouraging, personalized messages
5. Design should feel modern (2024+), not dated

RESPONSE FORMAT:
Respond with ONLY a JSON object (no markdown, no explanation):
{
  "title": "Short, catchy title (3-5 words)",
  "description": "Why this app will help the user (2-3 sentences based on their journal)",
  "journalContext": "Brief quote or insight from the journal that inspired this",
  "htmlCode": "<!DOCTYPE html>..."
}

AVOID:
- Generic breathing exercises (unless truly unique)
- Simple form/checklist UIs
- Boring corporate aesthetics
- Apps that feel like templates
- Using the word "journey" excessively
""".trimIndent()
    }

    private fun buildUserPrompt(entries: List<JournalEntry>): String {
        val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

        val formattedEntries = entries.mapIndexed { index, entry ->
            val cleanContent = entry.content
                .replace(Regex("<[^>]*>"), "") // Remove HTML tags
                .take(500) // Limit length

            """
Entry ${index + 1} (${entry.date.atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)}):
Mood: ${entry.mood?.displayName ?: "Not specified"}
Tags: ${entry.tags.joinToString(", ").ifEmpty { "None" }}
Content: $cleanContent
""".trimIndent()
        }.joinToString("\n\n")

        // Analyze mood trends
        val moods = entries.mapNotNull { it.mood }
        val moodSummary = if (moods.isNotEmpty()) {
            val moodCounts = moods.groupingBy { it }.eachCount()
            val dominantMood = moodCounts.maxByOrNull { it.value }?.key
            "Dominant mood: ${dominantMood?.displayName ?: "Mixed"}"
        } else {
            "Mood data not available"
        }

        return """
Based on the user's recent journal entries, create a personalized interactive web app that will genuinely help them.

RECENT JOURNAL ENTRIES:
$formattedEntries

MOOD ANALYSIS:
$moodSummary

Generate a unique, creative app that addresses what this user is experiencing. Make it beautiful, interactive, and meaningful.
""".trimIndent()
    }

    private fun parseGeneratedApp(
        response: String,
        entries: List<JournalEntry>
    ): Resource<GeneratedApp> {
        return try {
            // Try to extract JSON from the response (handle potential markdown wrapping)
            val jsonString = extractJson(response)

            val json = JsonParser.parseString(jsonString).asJsonObject
            val title = json.get("title")?.asString ?: "Daily Tool"
            val description = json.get("description")?.asString ?: "A personalized tool for you"
            val htmlCode = json.get("htmlCode")?.asString
                ?: return Resource.error("No HTML code in response")
            val journalContext = json.get("journalContext")?.asString

            // Validate HTML code
            if (!htmlCode.contains("<!DOCTYPE html>") && !htmlCode.contains("<html")) {
                return Resource.error("Invalid HTML code generated")
            }

            Resource.success(
                GeneratedApp(
                    title = title,
                    description = description,
                    htmlCode = htmlCode,
                    journalContext = journalContext
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $response", e)
            Resource.error("Failed to parse generated app: ${e.localizedMessage}")
        }
    }

    private fun extractJson(response: String): String {
        // If response is already valid JSON, return it
        if (response.trimStart().startsWith("{")) {
            return response
        }

        // Try to extract JSON from markdown code blocks
        val jsonPattern = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = jsonPattern.find(response)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Try to find JSON object in the response
        val startIndex = response.indexOf('{')
        val endIndex = response.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1)
        }

        return response
    }
}
