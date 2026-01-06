export const COACH_SYSTEM_PROMPT = `You are a supportive and insightful personal coach and journaling companion. Your role is to:

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

Never:
- Provide medical, legal, or financial advice
- Be judgmental about the user's choices or feelings
- Push the user to share more than they're comfortable with
- Make assumptions about the user's life or circumstances`

export const SUMMARY_SYSTEM_PROMPT = `You are a skilled summarizer helping users reflect on their journal entries. Your task is to:

1. **Capture Key Themes**: Identify the main topics, emotions, and events from the entries.

2. **Note Patterns**: Highlight any recurring themes, moods, or situations.

3. **Acknowledge Growth**: Point out any progress, insights, or positive developments.

4. **Gentle Observations**: Offer thoughtful observations that might help the user see their experiences from a new perspective.

5. **Forward Looking**: End with an encouraging note or gentle question for reflection.

Format your summary in a readable way with clear sections. Keep it concise but meaningful.`

export function buildCoachContext(recentEntries: string[]): string {
  let context = COACH_SYSTEM_PROMPT

  if (recentEntries.length > 0) {
    context += `\n\n## Recent Journal Entries (for context)\n${recentEntries.join('\n\n---\n\n')}`
  }

  return context
}

export function buildSummaryPrompt(entries: string[], periodType: 'daily' | 'weekly' | 'monthly'): string {
  const periodLabel = {
    daily: 'day',
    weekly: 'week',
    monthly: 'month'
  }[periodType]

  return `Please provide a thoughtful summary of this ${periodLabel}'s journal entries:\n\n${entries.join('\n\n---\n\n')}`
}

export type TimeOfDay = 'morning' | 'afternoon' | 'evening' | 'night'

export function getTimeOfDay(date: Date = new Date()): TimeOfDay {
  const hour = date.getHours()
  if (hour >= 5 && hour < 12) return 'morning'
  if (hour >= 12 && hour < 17) return 'afternoon'
  if (hour >= 17 && hour < 21) return 'evening'
  return 'night'
}

export const NOTIFICATION_SYSTEM_PROMPT = `You are a supportive personal coach generating a brief push notification message.

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
}`

export interface NotificationContext {
  recentEntries: Array<{
    content: string
    mood?: string | null
    tags: string[]
    date: Date
  }>
  recentNotifications: Array<{
    body: string
    topicReference?: string | null
    timeOfDay: string
    sentAt: Date
  }>
  timeOfDay: TimeOfDay
  userName?: string | null
}

export function buildNotificationPrompt(context: NotificationContext): string {
  const { recentEntries, recentNotifications, timeOfDay, userName } = context

  let prompt = `Current time of day: ${timeOfDay}\n`

  if (userName) {
    prompt += `User's name: ${userName}\n`
  }

  prompt += `\n## Recent Journal Entries (newest first)\n`

  if (recentEntries.length === 0) {
    prompt += `No recent entries. Generate a gentle reminder to journal.\n`
  } else {
    recentEntries.forEach((entry, index) => {
      const dateStr = entry.date.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric'
      })
      prompt += `\n### Entry ${index + 1} (${dateStr})\n`
      if (entry.mood) prompt += `Mood: ${entry.mood}\n`
      if (entry.tags.length > 0) prompt += `Tags: ${entry.tags.join(', ')}\n`
      prompt += `Content: ${entry.content.substring(0, 500)}${entry.content.length > 500 ? '...' : ''}\n`
    })
  }

  if (recentNotifications.length > 0) {
    prompt += `\n## Recent Notifications Sent (avoid repeating these topics)\n`
    recentNotifications.forEach((notif) => {
      const dateStr = notif.sentAt.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        hour: 'numeric'
      })
      prompt += `- "${notif.body}" (${notif.timeOfDay}, ${dateStr})`
      if (notif.topicReference) prompt += ` [Topic: ${notif.topicReference}]`
      prompt += `\n`
    })
  }

  prompt += `\nGenerate a unique, personalized notification that references their journal content but doesn't repeat topics from recent notifications.`

  return prompt
}
