import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { buildCoachContext } from '@/lib/prompts/coach'

// POST /api/chat/local - Process chat locally (AI only, no DB persistence)
// This endpoint is for mobile apps that want to store messages locally
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  let body
  try {
    body = await request.json()
  } catch {
    return NextResponse.json({ error: 'Invalid JSON' }, { status: 400 })
  }

  const { message, conversationHistory } = body

  if (!message) {
    return NextResponse.json({ error: 'Message is required' }, { status: 400 })
  }

  try {
    // Get recent journal entries for context (read-only, from server DB)
    const recentEntries = await prisma.journalEntry.findMany({
      where: { userId: session.user.id },
      orderBy: { date: 'desc' },
      take: 5
    })

    const entryContents = recentEntries.map(e =>
      `[${e.date.toLocaleDateString()}]${e.mood ? ` (Mood: ${e.mood})` : ''}\n${e.content}`
    )

    // Build conversation history from client-provided messages
    // Must start with user message for Claude API
    const messages: Array<{ role: 'user' | 'assistant'; content: string }> = []

    if (conversationHistory && Array.isArray(conversationHistory)) {
      // Find first user message index
      const firstUserIdx = conversationHistory.findIndex(
        (m: { role: string }) => m.role === 'user'
      )

      // Only include messages from first user message onwards
      if (firstUserIdx >= 0) {
        for (let i = firstUserIdx; i < conversationHistory.length; i++) {
          const m = conversationHistory[i] as { role: string; content: string }
          if (m.content && (m.role === 'user' || m.role === 'assistant')) {
            messages.push({
              role: m.role as 'user' | 'assistant',
              content: m.content
            })
          }
        }
      }
    }

    // Add the new user message
    messages.push({ role: 'user', content: message })

    // Build system prompt with journal context
    const systemPrompt = buildCoachContext(entryContents)

    // Call Claude API directly (no DB persistence)
    const response = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 1024,
      system: systemPrompt,
      messages
    })

    // Extract assistant response
    let assistantContent = ''
    if (response.content && response.content.length > 0) {
      const textBlock = response.content.find(block => block.type === 'text')
      if (textBlock && textBlock.type === 'text') {
        assistantContent = textBlock.text
      }
    }

    if (!assistantContent) {
      assistantContent = "I'm sorry, I wasn't able to generate a response. Please try again."
    }

    // Return the response without saving to DB
    // Client is responsible for local storage
    return NextResponse.json({
      message: assistantContent,
      timestamp: new Date().toISOString()
    })
  } catch (error) {
    console.error('Error processing local chat:', error)
    return NextResponse.json(
      { error: 'Failed to process message' },
      { status: 500 }
    )
  }
}
