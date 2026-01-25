package com.personalcoacher.util

import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.kuzu.RetrievedContext
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

    /**
     * Builds the system prompt using RAG-retrieved context.
     * This provides semantically relevant context instead of just recent entries.
     * Goals, tasks, and notes are retrieved via semantic search just like journal entries.
     */
    fun buildCoachContextFromRag(
        ragContext: RetrievedContext,
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

        // Add agenda items section (same as before)
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

        // Add user goals if available (extracted from journal entries)
        if (ragContext.goals.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Goals Mentioned in Journal")
            contextBuilder.appendLine("Goals extracted from the user's journal entries:")
            contextBuilder.appendLine()

            ragContext.goals.forEach { goal ->
                contextBuilder.appendLine("- ${goal.description}")
            }
            contextBuilder.appendLine()
        }

        // Add user-created goals (from Goals tab) - semantically retrieved via RAG
        if (ragContext.userGoals.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## User's Active Goals")
            contextBuilder.appendLine("The user has set these explicit goals (semantically relevant to the conversation):")
            contextBuilder.appendLine()

            ragContext.userGoals.forEach { goal ->
                val priorityLabel = when (goal.priority) {
                    "HIGH" -> "🔴 High Priority"
                    "MEDIUM" -> "🟡 Medium Priority"
                    "LOW" -> "🟢 Low Priority"
                    else -> ""
                }
                val targetDateStr = goal.targetDate?.let { " (Target: $it)" } ?: ""
                contextBuilder.appendLine("- **${goal.title}**$targetDateStr $priorityLabel")
                if (goal.description.isNotBlank()) {
                    contextBuilder.appendLine("  ${goal.description}")
                }
            }
            contextBuilder.appendLine()
        }

        // Add user-created tasks (from Tasks tab) - semantically retrieved via RAG
        if (ragContext.userTasks.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## User's Pending Tasks")
            contextBuilder.appendLine("Tasks the user needs to complete (semantically relevant to the conversation):")
            contextBuilder.appendLine()

            ragContext.userTasks.forEach { task ->
                val statusIcon = if (task.isCompleted) "✅" else "⬜"
                val priorityLabel = when (task.priority) {
                    "HIGH" -> "🔴"
                    "MEDIUM" -> "🟡"
                    "LOW" -> "🟢"
                    else -> ""
                }
                val dueDateStr = task.dueDate?.let { " (Due: $it)" } ?: ""
                contextBuilder.appendLine("- $statusIcon $priorityLabel **${task.title}**$dueDateStr")
                if (task.description.isNotBlank()) {
                    contextBuilder.appendLine("  ${task.description}")
                }
            }
            contextBuilder.appendLine()
        }

        // Add user notes (from Notes tab) - semantically retrieved via RAG
        if (ragContext.notes.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## User's Notes")
            contextBuilder.appendLine("Notes the user has saved (semantically relevant to the conversation):")
            contextBuilder.appendLine()

            ragContext.notes.forEach { note ->
                val entryDate = Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate()
                val daysAgo = ChronoUnit.DAYS.between(entryDate, today).toInt()
                val relativeTime = when (daysAgo) {
                    0 -> "today"
                    1 -> "yesterday"
                    else -> "$daysAgo days ago"
                }
                contextBuilder.appendLine("### ${note.title} ($relativeTime)")
                // Truncate very long notes
                val content = if (note.content.length > 300) {
                    note.content.take(300) + "..."
                } else {
                    note.content
                }
                contextBuilder.appendLine(content)
                contextBuilder.appendLine()
            }
        }

        // Add related people context
        if (ragContext.relatedPeople.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Important People in Context")
            ragContext.relatedPeople.forEach { person ->
                val relationship = person.relationship?.let { " ($it)" } ?: ""
                contextBuilder.appendLine("- **${person.name}**$relationship")
            }
            contextBuilder.appendLine()
        }

        // Add related topics
        if (ragContext.relatedTopics.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Relevant Topics")
            contextBuilder.appendLine("Based on the conversation, these topics are relevant:")
            ragContext.relatedTopics.forEach { topic ->
                val category = topic.category?.let { " [$it]" } ?: ""
                contextBuilder.appendLine("- ${topic.name}$category")
            }
            contextBuilder.appendLine()
        }

        // Add atomic thoughts/insights if available
        if (ragContext.atomicThoughts.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Key Insights from User's Journal")
            contextBuilder.appendLine("These are important patterns and insights extracted from the user's past entries:")
            contextBuilder.appendLine()

            ragContext.atomicThoughts.forEach { thought ->
                val typeLabel = when (thought.type) {
                    "belief" -> "Belief"
                    "insight" -> "Insight"
                    "pattern" -> "Pattern"
                    "goal" -> "Goal"
                    "emotion" -> "Emotion"
                    "relationship" -> "Relationship"
                    else -> thought.type.replaceFirstChar { it.uppercase() }
                }
                contextBuilder.appendLine("- [$typeLabel] ${thought.content}")
            }
            contextBuilder.appendLine()
        }

        // Add semantically relevant journal entries
        if (ragContext.journalEntries.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Relevant Journal Entries (semantically retrieved)")
            contextBuilder.appendLine("These entries are most relevant to the current conversation:")

            ragContext.journalEntries.forEachIndexed { index, entry ->
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
                // Truncate very long entries to avoid context overload
                val content = if (entry.content.length > 500) {
                    entry.content.take(500) + "..."
                } else {
                    entry.content
                }
                contextBuilder.appendLine("Content: $content")
                contextBuilder.appendLine("---")
            }
        }

        return contextBuilder.toString()
    }
}
