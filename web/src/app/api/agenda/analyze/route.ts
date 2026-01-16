import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'

const ANALYZE_SYSTEM_PROMPT = `You are an AI assistant that analyzes journal entries to detect mentions of events, meetings, appointments, or scheduled activities. Your task is to identify any upcoming or past events mentioned in the journal text and extract structured information about them.

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

If no events are detected, respond with: { "suggestions": [] }`

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { journalEntryId, content } = body

  if (!content) {
    return NextResponse.json({ error: 'Content is required' }, { status: 400 })
  }

  try {
    const today = new Date().toISOString().split('T')[0]

    const response = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 1024,
      system: ANALYZE_SYSTEM_PROMPT,
      messages: [
        {
          role: 'user',
          content: `Today's date is ${today}. Please analyze this journal entry for any events or scheduled activities:\n\n${content}`
        }
      ]
    })

    // Extract the text content from the response
    const textContent = response.content.find(block => block.type === 'text')
    if (!textContent || textContent.type !== 'text') {
      return NextResponse.json({ suggestions: [] })
    }

    // Parse the JSON response
    try {
      const result = JSON.parse(textContent.text)
      return NextResponse.json(result)
    } catch {
      // If parsing fails, return empty suggestions
      return NextResponse.json({ suggestions: [] })
    }
  } catch (error) {
    console.error('Error analyzing journal entry:', error)
    return NextResponse.json(
      { error: 'Failed to analyze journal entry', suggestions: [] },
      { status: 500 }
    )
  }
}
