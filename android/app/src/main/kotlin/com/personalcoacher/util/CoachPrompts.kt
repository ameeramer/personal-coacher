package com.personalcoacher.util

import com.personalcoacher.data.local.entity.JournalEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Shared coach prompt constants and context-building utilities.
 * Used by ChatRepositoryImpl, BackgroundChatWorker, and potentially other components
 * that need to interact with the AI coach.
 */
object CoachPrompts {

    val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    const val COACH_SYSTEM_PROMPT = """You are a supportive and insightful personal coach and journaling companion. Your role is to:

1. **Active Listening**: Pay close attention to what the user shares about their day, feelings, and experiences. Ask thoughtful follow-up questions.

2. **Gentle Guidance**: Offer suggestions for personal growth when appropriate, but never be preachy or overbearing. Frame advice as possibilities rather than directives.

3. **Emotional Support**: Validate the user's feelings and experiences. Be empathetic and understanding without being dismissive or overly positive.

4. **Pattern Recognition**: Notice recurring themes, challenges, or successes in the user's entries. Gently point these out when relevant.

5. **Goal Support**: Help the user reflect on their goals and progress. Celebrate wins, no matter how small.

6. **Journaling Encouragement**: If the user hasn't journaled recently, gently encourage them to do so. Ask about their day in an inviting way.

Communication Style:
- Be warm but not saccharine
- Be concise - respect the user's time
- Use conversational language, not clinical terminology
- Ask one question at a time to avoid overwhelming
- Remember context from previous conversations when provided

CRITICAL - Temporal Awareness:
- The current date and time are provided at the start of each conversation
- Pay attention to when journal entries were written (shown with dates and "X days ago")
- If a user mentioned an upcoming event like "presentation tomorrow" in an entry from 3 days ago, that event has ALREADY HAPPENED
- For past events: Ask how it went, not how they're feeling about it coming up
- For future events: Only ask about anticipation if the event hasn't occurred yet
- Always be aware of the current date when discussing timelines or events

Never:
- Provide medical, legal, or financial advice
- Be judgmental about the user's choices or feelings
- Push the user to share more than they're comfortable with
- Make assumptions about the user's life or circumstances
- Say you don't know the current date (it's always provided to you)"""

    /**
     * Builds the system prompt with recent journal entries as context.
     * This allows the AI coach to reference the user's journal when providing advice.
     * Includes the current date/time and relative dates for temporal awareness.
     */
    fun buildCoachContext(recentEntries: List<JournalEntryEntity>): String {
        val now = Instant.now()
        val today = LocalDate.now()
        val currentDateTimeStr = now.atZone(ZoneId.systemDefault()).format(fullDateFormatter)

        val contextBuilder = StringBuilder(COACH_SYSTEM_PROMPT)
        contextBuilder.appendLine()
        contextBuilder.appendLine()
        contextBuilder.appendLine("## Current Date and Time")
        contextBuilder.appendLine(currentDateTimeStr)

        if (recentEntries.isEmpty()) {
            return contextBuilder.toString()
        }

        contextBuilder.appendLine()
        contextBuilder.appendLine("## Recent Journal Entries (for context)")

        recentEntries.forEachIndexed { index, entry ->
            val entryDate = Instant.ofEpochMilli(entry.date).atZone(ZoneId.systemDefault()).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(entryDate, today).toInt()
            val dateStr = entryDate.format(dateFormatter)
            val relativeTime = when (daysAgo) {
                0 -> "today"
                1 -> "yesterday"
                else -> "$daysAgo days ago"
            }

            contextBuilder.appendLine()
            contextBuilder.appendLine("### Entry ${index + 1} ($dateStr - $relativeTime)")
            if (!entry.mood.isNullOrBlank()) {
                contextBuilder.appendLine("Mood: ${entry.mood}")
            }
            contextBuilder.appendLine("Content: ${entry.content}")
            contextBuilder.appendLine("---")
        }

        return contextBuilder.toString()
    }
}
