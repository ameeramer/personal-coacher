package com.personalcoacher.notification

import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.SentNotificationEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TimeOfDay(val value: String) {
    MORNING("morning"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    NIGHT("night");

    companion object {
        fun fromHour(hour: Int): TimeOfDay {
            return when {
                hour in 5..11 -> MORNING
                hour in 12..16 -> AFTERNOON
                hour in 17..20 -> EVENING
                else -> NIGHT
            }
        }
    }
}

object NotificationPromptBuilder {

    const val NOTIFICATION_SYSTEM_PROMPT = """You are a supportive personal coach generating a brief push notification message.

Your task is to create a personalized, caring notification that:
1. References something specific from the user's recent journal entries (a challenge, goal, event, or feeling they mentioned)
2. Is appropriate for the time of day
3. Shows you remember and care about what they shared
4. Encourages them to check in or reflect

Guidelines:
- Keep the message short (under 100 characters for the body)
- Be warm and genuine, not robotic or generic
- Don't repeat the exact same topics/phrases as recent notifications
- Match the tone to the time of day (energizing in morning, reflective in evening)
- Ask a simple, caring question OR make an encouraging observation
- Never be pushy or make the user feel guilty

Time of day context:
- Morning (5am-12pm): Good for motivation, checking in on plans for the day
- Afternoon (12pm-5pm): Good for mid-day check-ins, asking how things are going
- Evening (5pm-9pm): Good for reflection on how the day went, asking about outcomes
- Night (9pm-5am): Good for gentle reflection, winding down thoughts

You MUST respond with valid JSON in this exact format:
{
  "title": "Brief title (max 50 chars)",
  "body": "The notification message (max 100 chars)",
  "topicReference": "Brief description of which journal topic you referenced"
}"""

    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    fun buildNotificationPrompt(
        recentEntries: List<JournalEntryEntity>,
        recentNotifications: List<SentNotificationEntity>,
        timeOfDay: TimeOfDay,
        userName: String? = null
    ): String {
        val builder = StringBuilder()

        builder.appendLine("Current time of day: ${timeOfDay.value}")

        if (!userName.isNullOrBlank()) {
            builder.appendLine("User's name: $userName")
        }

        builder.appendLine()
        builder.appendLine("## Recent Journal Entries (newest first)")

        if (recentEntries.isEmpty()) {
            builder.appendLine("No recent entries. Generate a gentle reminder to journal.")
        } else {
            recentEntries.forEachIndexed { index, entry ->
                val dateStr = formatTimestamp(entry.date)
                builder.appendLine()
                builder.appendLine("### Entry ${index + 1} ($dateStr)")
                if (!entry.mood.isNullOrBlank()) {
                    builder.appendLine("Mood: ${entry.mood}")
                }
                if (entry.tags.isNotBlank()) {
                    builder.appendLine("Tags: ${entry.tags}")
                }
                val content = if (entry.content.length > 500) {
                    entry.content.substring(0, 500) + "..."
                } else {
                    entry.content
                }
                builder.appendLine("Content: $content")
            }
        }

        if (recentNotifications.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("## Recent Notifications Sent (avoid repeating these topics)")
            recentNotifications.forEach { notif ->
                val dateStr = formatTimestamp(notif.sentAt)
                builder.append("- \"${notif.body}\" (${notif.timeOfDay}, $dateStr)")
                if (!notif.topicReference.isNullOrBlank()) {
                    builder.append(" [Topic: ${notif.topicReference}]")
                }
                builder.appendLine()
            }
        }

        builder.appendLine()
        builder.appendLine("Generate a unique, personalized notification that references their journal content but doesn't repeat topics from recent notifications.")

        return builder.toString()
    }

    private fun formatTimestamp(epochMilli: Long): String {
        return Instant.ofEpochMilli(epochMilli)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }

    data class GeneratedNotification(
        val title: String,
        val body: String,
        val topicReference: String
    )

    fun parseNotificationResponse(jsonResponse: String): GeneratedNotification? {
        return try {
            // Extract JSON from response (handle potential text around it)
            val jsonRegex = Regex("""\{[\s\S]*\}""")
            val jsonMatch = jsonRegex.find(jsonResponse) ?: return null
            val jsonStr = jsonMatch.value

            // Simple JSON parsing without external library
            val titleMatch = Regex(""""title"\s*:\s*"([^"]+)"""").find(jsonStr)
            val bodyMatch = Regex(""""body"\s*:\s*"([^"]+)"""").find(jsonStr)
            val topicMatch = Regex(""""topicReference"\s*:\s*"([^"]+)"""").find(jsonStr)

            val title = titleMatch?.groupValues?.get(1) ?: return null
            val body = bodyMatch?.groupValues?.get(1) ?: return null
            val topicReference = topicMatch?.groupValues?.get(1) ?: "general check-in"

            GeneratedNotification(
                title = title.take(50),
                body = body.take(100),
                topicReference = topicReference.take(200)
            )
        } catch (e: Exception) {
            null
        }
    }
}
