package com.personalcoacher.util

import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.JournalEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.US)

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
- Say you don't know the current date (it's always provided to you)

Agenda Awareness:
- You have access to the user's upcoming calendar events/agenda items
- Use this information to provide contextual advice and ask about upcoming events
- If the user mentions preparing for something, check if it's in their agenda
- Remind them of relevant upcoming events when appropriate
- Ask how they're feeling about upcoming commitments"""

    /**
     * Builds the system prompt with recent journal entries and agenda items as context.
     * This allows the AI coach to reference the user's journal and calendar when providing advice.
     * Includes the current date/time and relative dates for temporal awareness.
     */
    fun buildCoachContext(
        recentEntries: List<JournalEntryEntity>,
        upcomingAgendaItems: List<AgendaItemEntity> = emptyList()
    ): String {
        val now = Instant.now()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val currentDateTimeStr = now.atZone(zone).format(fullDateFormatter)

        val contextBuilder = StringBuilder(COACH_SYSTEM_PROMPT)
        contextBuilder.appendLine()
        contextBuilder.appendLine()
        contextBuilder.appendLine("## Current Date and Time")
        contextBuilder.appendLine(currentDateTimeStr)

        // Add agenda items section
        if (upcomingAgendaItems.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## User's Upcoming Agenda/Calendar Events")
            contextBuilder.appendLine("The following events are scheduled in the user's calendar:")
            contextBuilder.appendLine()

            upcomingAgendaItems.forEach { item ->
                val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.startTime), zone)
                val startDate = startDateTime.toLocalDate()
                val daysUntil = ChronoUnit.DAYS.between(today, startDate).toInt()

                val relativeTime = when {
                    daysUntil == 0 -> "today"
                    daysUntil == 1 -> "tomorrow"
                    daysUntil < 7 -> "in $daysUntil days"
                    else -> "in ${daysUntil / 7} weeks"
                }

                val timeStr = if (item.isAllDay) {
                    startDate.format(dateFormatter) + " (all day)"
                } else {
                    startDateTime.format(dateTimeFormatter)
                }

                contextBuilder.appendLine("- **${item.title}** ($relativeTime)")
                contextBuilder.appendLine("  - When: $timeStr")

                if (item.endTime != null && !item.isAllDay) {
                    val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.endTime), zone)
                    contextBuilder.appendLine("  - Until: ${endDateTime.format(timeFormatter)}")
                }

                if (!item.location.isNullOrBlank()) {
                    contextBuilder.appendLine("  - Location: ${item.location}")
                }

                if (!item.description.isNullOrBlank()) {
                    contextBuilder.appendLine("  - Details: ${item.description}")
                }
            }
            contextBuilder.appendLine()
        }

        // Add journal entries section
        if (recentEntries.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Recent Journal Entries (for context)")

            recentEntries.forEachIndexed { index, entry ->
                val entryDate = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()
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
        }

        return contextBuilder.toString()
    }
}
