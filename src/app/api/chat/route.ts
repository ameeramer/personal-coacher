import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { buildCoachContext } from '@/lib/prompts/coach'

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { message, conversationId, initialAssistantMessage } = body

  if (!message) {
    return NextResponse.json({ error: 'Message is required' }, { status: 400 })
  }

  // Get or create conversation
  let conversation
  if (conversationId) {
    conversation = await prisma.conversation.findFirst({
      where: { id: conversationId, userId: session.user.id },
      include: { messages: { orderBy: { createdAt: 'asc' } } }
    })
    if (!conversation) {
      return NextResponse.json({ error: 'Conversation not found' }, { status: 404 })
    }
  } else {
    conversation = await prisma.conversation.create({
      data: {
        userId: session.user.id,
        title: message.slice(0, 50) + (message.length > 50 ? '...' : '')
      },
      include: { messages: true }
    })
  }

  // Get recent journal entries for context
  const recentEntries = await prisma.journalEntry.findMany({
    where: { userId: session.user.id },
    orderBy: { date: 'desc' },
    take: 5
  })

  const entryContents = recentEntries.map(e =>
    `[${e.date.toLocaleDateString()}]${e.mood ? ` (Mood: ${e.mood})` : ''}\n${e.content}`
  )

  // Build conversation history for Claude
  const conversationHistory = conversation.messages.map(m => ({
    role: m.role as 'user' | 'assistant',
    content: m.content
  }))

  // If this is a new conversation with an initial assistant message (from notification),
  // save it to the database and add it to the history so the AI is aware of its own message
  if (initialAssistantMessage && conversation.messages.length === 0) {
    // Save the notification message to the database
    await prisma.message.create({
      data: {
        conversationId: conversation.id,
        role: 'assistant',
        content: initialAssistantMessage
      }
    })
    conversationHistory.push({ role: 'assistant', content: initialAssistantMessage })
  }

  // Add new user message
  conversationHistory.push({ role: 'user', content: message })

  // Save user message
  await prisma.message.create({
    data: {
      conversationId: conversation.id,
      role: 'user',
      content: message
    }
  })

  // Call Claude API
  const systemPrompt = buildCoachContext(entryContents)

  const response = await anthropic.messages.create({
    model: CLAUDE_MODEL,
    max_tokens: 1024,
    system: systemPrompt,
    messages: conversationHistory
  })

  const assistantMessage = response.content[0].type === 'text'
    ? response.content[0].text
    : ''

  // Save assistant message
  const savedMessage = await prisma.message.create({
    data: {
      conversationId: conversation.id,
      role: 'assistant',
      content: assistantMessage
    }
  })

  // Update conversation timestamp
  await prisma.conversation.update({
    where: { id: conversation.id },
    data: { updatedAt: new Date() }
  })

  return NextResponse.json({
    conversationId: conversation.id,
    message: savedMessage
  })
}
