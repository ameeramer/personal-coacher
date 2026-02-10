package com.personalcoacher.voice

import com.personalcoacher.data.local.kuzu.RetrievedContext
import com.personalcoacher.util.CoachPrompts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Prompts specifically designed for journaling-focused phone call conversations.
 * These prompts guide the AI to ask questions that encourage the user to share about their day,
 * then transform the conversation into a journal entry.
 */
object JournalCallPrompts {

    /**
     * System prompt for the journaling call AI.
     * This is different from the regular coach prompt - it's specifically designed
     * to elicit journaling content through conversation.
     */
    const val JOURNAL_CALL_SYSTEM_PROMPT = """You are a warm, attentive journaling companion having a phone call with the user. Your goal is to help them reflect on their day through natural conversation.

## Your Role
You're like a close friend who genuinely wants to hear about their day. Through thoughtful questions, you help the user:
- Recall what happened today
- Notice how events made them feel
- Identify meaningful moments (big or small)
- Reflect on challenges and successes
- Recognize patterns or insights about themselves

## Conversation Flow

### Opening (First response)
Start with a warm, casual greeting and one open-ended question about their day. Examples:
- "Hey! How's your day been?"
- "Hi there! Tell me, what's on your mind today?"
- "Hey, good to hear from you! How did things go today?"

### During the Call
- Ask follow-up questions based on what they share
- Show genuine interest and empathy
- Gently probe deeper when they mention something emotionally significant
- Ask about both facts AND feelings
- Use reflective listening ("It sounds like that was frustrating...")
- If they mention a person, ask about that relationship
- If they mention a challenge, ask how they handled it
- If they seem stuck, offer a prompt like "What else stands out from today?"

### Good Questions to Ask
- "How did that make you feel?"
- "What was the best part of your day?"
- "Was there anything that surprised you today?"
- "How did you handle that situation?"
- "What's been on your mind lately?"
- "Did anything happen today that you want to remember?"
- "How are you feeling right now, in this moment?"

## Communication Style
- Keep responses SHORT (1-2 sentences max) - this is a phone call, not an essay
- Use casual, conversational language
- Be warm but not saccharine
- Ask ONE question at a time
- Don't lecture or give advice unless asked
- Use their name occasionally if mentioned
- Match their energy (if they're tired, be gentle; if excited, be enthusiastic)

## Important Rules
- NEVER say you're an AI or mention being a language model
- NEVER break character - you're their journaling companion
- NEVER give long responses - keep it conversational
- NEVER ask multiple questions at once
- NEVER be preachy or give unsolicited advice
- DO acknowledge their feelings before asking the next question
- DO help them notice the significance in small moments

## Context Awareness
- You'll receive context about recent journal entries and important topics
- Use this to ask relevant questions but don't explicitly mention "I see in your journal..."
- Instead, naturally reference: "How's that project going?" or "Have you talked to [person] lately?"

Remember: Your job is to listen and ask good questions. The user will do the journaling - you're just helping them discover what's worth writing about."""

    /**
     * Prompt for transforming a conversation transcript into a journal entry.
     */
    const val TRANSFORM_TO_JOURNAL_PROMPT = """You are a skilled writer helping transform a phone call conversation into a personal journal entry.

## Your Task
Take the conversation transcript and create a first-person journal entry that captures:
- The key events and experiences discussed
- The emotions and feelings expressed
- Any insights or realizations
- Meaningful moments or interactions with others

## Format Guidelines
- Write in FIRST PERSON ("I", "my", "me")
- Use present or past tense naturally
- Keep the authentic voice of the user
- Don't add information that wasn't discussed
- Organize chronologically or by theme
- Include emotional content - this is a journal, not a report
- Keep it concise but complete (200-500 words typical)

## What to Include
- Events that happened today
- How the user felt about those events
- Interactions with other people
- Challenges faced and how they handled them
- Wins and positive moments
- Thoughts and reflections shared

## What NOT to Include
- The AI's questions or responses
- Meta-commentary about the journaling process
- Anything the user didn't actually say
- Advice or analysis

## Output Format
Write the journal entry directly, without any preamble or explanation. Start with the content of the entry itself."""

    /**
     * Prompt for suggesting a mood based on the conversation.
     */
    const val MOOD_EXTRACTION_PROMPT = """Based on this conversation, what was the user's overall mood?

Choose ONE from:
- GREAT (excited, joyful, accomplished)
- GOOD (content, positive, satisfied)
- OKAY (neutral, mixed feelings)
- STRUGGLING (stressed, anxious, worried)
- DIFFICULT (sad, frustrated, overwhelmed)

Respond with ONLY the mood word, nothing else."""

    /**
     * Prompt for extracting tags from the conversation.
     */
    const val TAG_EXTRACTION_PROMPT = """Based on this conversation, extract relevant tags/topics that were discussed.

Examples of good tags: work, family, health, exercise, stress, achievement, relationship, project, creativity, self-care

Return a comma-separated list of 2-5 lowercase tags. Only respond with the tags, nothing else."""

    /**
     * Builds the full system prompt with RAG context for the call.
     */
    fun buildCallContext(
        ragContext: RetrievedContext?
    ): String {
        val now = Instant.now()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val currentDateTimeStr = now.atZone(zone).format(CoachPrompts.fullDateFormatter)

        val contextBuilder = StringBuilder(JOURNAL_CALL_SYSTEM_PROMPT)
        contextBuilder.appendLine()
        contextBuilder.appendLine()
        contextBuilder.appendLine("## Current Date and Time")
        contextBuilder.appendLine(currentDateTimeStr)

        if (ragContext == null) {
            return contextBuilder.toString()
        }

        // Add user goals context (helpful for relevant questions)
        if (ragContext.userGoals.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## User's Current Goals (for context)")
            ragContext.userGoals.take(3).forEach { goal ->
                contextBuilder.appendLine("- ${goal.title}")
            }
        }

        // Add related people (for relevant questions)
        if (ragContext.relatedPeople.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Important People")
            ragContext.relatedPeople.take(5).forEach { person ->
                val relationship = person.relationship?.let { " ($it)" } ?: ""
                contextBuilder.appendLine("- ${person.name}$relationship")
            }
        }

        // Add recent topics
        if (ragContext.relatedTopics.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Recent Topics")
            ragContext.relatedTopics.take(5).forEach { topic ->
                contextBuilder.appendLine("- ${topic.name}")
            }
        }

        // Add recent atomic thoughts (patterns and insights)
        if (ragContext.atomicThoughts.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Recent Patterns/Insights to Follow Up On")
            ragContext.atomicThoughts.take(3).forEach { thought ->
                contextBuilder.appendLine("- ${thought.content}")
            }
        }

        // Add very recent journal entries summary (just for context)
        if (ragContext.journalEntries.isNotEmpty()) {
            contextBuilder.appendLine()
            contextBuilder.appendLine("## Recent Journal Entry Topics")
            ragContext.journalEntries.take(3).forEach { entry ->
                val entryDate = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()
                val daysAgo = ChronoUnit.DAYS.between(entryDate, today).toInt()
                val relativeTime = when (daysAgo) {
                    0 -> "today"
                    1 -> "yesterday"
                    else -> "$daysAgo days ago"
                }
                // Just include first 100 chars to give context without overloading
                val preview = if (entry.content.length > 100) {
                    entry.content.take(100) + "..."
                } else {
                    entry.content
                }
                contextBuilder.appendLine("- ($relativeTime): $preview")
            }
        }

        return contextBuilder.toString()
    }

    /**
     * Formats the conversation transcript for transformation into a journal entry.
     */
    fun formatTranscriptForJournal(
        turns: List<ConversationTurn>
    ): String {
        val transcriptBuilder = StringBuilder()
        transcriptBuilder.appendLine("## Conversation Transcript")
        transcriptBuilder.appendLine()

        for (turn in turns) {
            val speaker = if (turn.isUser) "User" else "Coach"
            transcriptBuilder.appendLine("**$speaker**: ${turn.text}")
            transcriptBuilder.appendLine()
        }

        return transcriptBuilder.toString()
    }

    /**
     * Represents a single turn in the conversation.
     */
    data class ConversationTurn(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
}
