package com.personalcoacher.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.util.Resource
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating AI-powered dynamic web apps using Claude API.
 * Each generated app is a complete, self-contained HTML/CSS/JS application
 * tailored to the user's emotional state and journal content.
 *
 * Uses non-streaming API for reliable background execution.
 * Streaming was causing hangs when the app was backgrounded due to connection stalls.
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
     * Uses non-streaming API for reliable background execution.
     * Streaming was causing hangs when app was backgrounded due to connection stalls.
     *
     * @param apiKey The Claude API key
     * @param recentEntries Recent journal entries for context
     * @param previousTools Previously created tools to avoid duplicates
     */
    suspend fun generateApp(
        apiKey: String,
        recentEntries: List<JournalEntry>,
        previousTools: List<DailyApp> = emptyList()
    ): Resource<GeneratedApp> {
        return try {
            val systemPrompt = buildSystemPrompt()
            val userPrompt = buildUserPrompt(recentEntries, previousTools)

            Log.d(TAG, "Generating daily app with ${recentEntries.size} journal entries and ${previousTools.size} previous tools")

            val request = ClaudeMessageRequest(
                model = "claude-sonnet-4-20250514",
                maxTokens = MAX_TOKENS,
                system = systemPrompt,
                messages = listOf(
                    ClaudeMessage(role = "user", content = userPrompt)
                ),
                stream = false // Non-streaming for reliable background execution
            )

            // Make the API call using non-streaming endpoint
            val response = claudeApiService.sendMessage(
                apiKey = apiKey,
                request = request
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API error: ${response.code()} - $errorBody")

                val errorMessage = when {
                    errorBody?.contains("invalid_api_key") == true ->
                        "Invalid API key. Please check your Claude API key in Settings."
                    response.code() == 529 ->
                        "Claude API is overloaded. Please try again in a few minutes."
                    response.code() == 429 ->
                        "Rate limited. Please try again in a few minutes."
                    else ->
                        "API error: ${response.code()} - ${response.message()}"
                }
                return Resource.error(errorMessage)
            }

            val responseBody = response.body()
            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return Resource.error("Empty response from Claude")
            }

            val fullResponse = responseBody.content.firstOrNull()?.text
            if (fullResponse.isNullOrBlank()) {
                Log.e(TAG, "No text content in response")
                return Resource.error("Empty response from Claude")
            }

            Log.d(TAG, "Received response with ${fullResponse.length} characters")
            parseGeneratedApp(fullResponse, recentEntries)
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
6. NEVER create the same or similar tool that the user has already received - check the "PREVIOUSLY CREATED TOOLS" section and make something completely different

TECHNICAL REQUIREMENTS:
1. Include all CSS in a <style> tag in the <head>
2. Include all JavaScript in a <script> tag before </body>
3. Use modern CSS (flexbox, grid, animations, gradients)
4. Make it mobile-friendly (use viewport meta tag, responsive design)
5. DO NOT use any external resources (no CDN links, no external fonts, no images)
6. DO NOT use localStorage, cookies, or sessionStorage - use the Android bridge for persistence

CRITICAL - SCROLLING & KEYBOARD HANDLING:
The app runs in a WebView on mobile. You MUST ensure proper scrolling and keyboard handling:

1. ALWAYS make the page scrollable:
   - Use height: auto or min-height: 100vh on body, NEVER height: 100vh
   - Container should use min-height, not fixed height
   - Use overflow-y: auto on scrollable containers

2. Required CSS for proper scrolling:
   html, body {
     min-height: 100vh;
     height: auto;
     overflow-x: hidden;
     overflow-y: auto;
     -webkit-overflow-scrolling: touch;
   }

3. Input fields MUST be visible when keyboard opens:
   - Add padding-bottom: 300px to the main container to ensure space for keyboard
   - Use scroll-padding-bottom: 300px on body
   - When input is focused, scroll it into view using JavaScript:
     input.addEventListener('focus', () => {
       setTimeout(() => input.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
     });

4. For forms with multiple inputs:
   - Add margin-bottom: 16px between inputs
   - Add extra bottom padding (at least 300px) at the end of the form
   - Use flex-direction: column and allow natural document flow

5. AVOID these patterns that break scrolling:
   - position: fixed on containers (except for headers)
   - height: 100vh on body or main containers
   - overflow: hidden on body
   - vh units for container heights (use min-height instead)

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

DESIGN REQUIREMENTS - MUST MATCH THE PARENT APP'S iOS-STYLE DESIGN:
The generated app MUST follow these exact design specifications to match the parent app:

1. COLOR PALETTE:
   - Light mode background: #F2F2F7 (iOS system background)
   - Card/surface background: #FFFFFF
   - Primary color (amber): #D97706
   - Primary container: #FEF3C7 (amber100)
   - Text primary: #171717
   - Text secondary: #525252
   - Border color: rgba(0,0,0,0.1)
   - For dark mode support, use CSS media query @media (prefers-color-scheme: dark)
   - Dark mode background: #000000
   - Dark mode surface: #1C1C1E
   - Dark mode primary (lavender): #8B82D1
   - Dark mode text: #F5F5F5

2. TYPOGRAPHY:
   - Use system fonts: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif
   - Headings: Use Georgia, serif font-family with bold weight
   - Body text: 16px base, line-height 1.5
   - Labels: 14px, slightly muted color

3. SPACING & LAYOUT:
   - Screen padding: 20px
   - Card padding: 20px
   - Section spacing: 24px
   - Item spacing: 16px
   - Use flexbox or CSS grid for layouts

4. COMPONENTS STYLE:
   - Cards: background white, border-radius 16px, subtle shadow (0 1px 3px rgba(0,0,0,0.1)), thin border (0.5px solid rgba(0,0,0,0.1))
   - Buttons: border-radius 12px, padding 16px 24px, primary uses amber #D97706 with white text
   - Input fields: border-radius 12px, padding 16px, subtle border
   - Use soft, rounded corners everywhere (12-20px border-radius)

5. ANIMATIONS:
   - Use subtle transitions (0.2s ease)
   - Gentle scale on button press (transform: scale(0.98))
   - Fade in for content (opacity animation)

6. VISUAL STYLE:
   - Clean, minimal iOS-like aesthetic
   - Generous whitespace
   - Soft shadows, not harsh
   - No harsh borders - use subtle separators
   - Icons should be simple line icons or emoji

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
- Harsh colors or high contrast that doesn't match the soft aesthetic
- Apps that feel like templates
- Using the word "journey" excessively
- Dark/heavy UI - keep it light and airy
- Fixed heights (height: 100vh) - always use min-height
- overflow: hidden on body or containers
- Layouts that don't scroll on mobile
- Input fields at the bottom without keyboard padding
""".trimIndent()
    }

    private fun buildUserPrompt(entries: List<JournalEntry>, previousTools: List<DailyApp>): String {
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

        // Format previous tools to avoid duplicates
        val previousToolsSection = if (previousTools.isNotEmpty()) {
            val toolsList = previousTools.mapIndexed { index, tool ->
                val toolDate = tool.date.atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)
                "- \"${tool.title}\" ($toolDate): ${tool.description}"
            }.joinToString("\n")

            """
PREVIOUSLY CREATED TOOLS (DO NOT CREATE SIMILAR ONES):
$toolsList

IMPORTANT: Create something COMPLETELY DIFFERENT from the tools listed above. Do not use the same concepts, exercises, or app types. Be creative and explore new approaches.
"""
        } else {
            ""
        }

        return """
Based on the user's recent journal entries, create a personalized interactive web app that will genuinely help them.

RECENT JOURNAL ENTRIES:
$formattedEntries

MOOD ANALYSIS:
$moodSummary
$previousToolsSection
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
