import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

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

  // If this is a new conversation with an initial assistant message (from notification),
  // save it to the database
  if (initialAssistantMessage && conversation.messages.length === 0) {
    await prisma.message.create({
      data: {
        conversationId: conversation.id,
        role: 'assistant',
        content: initialAssistantMessage,
        status: 'completed',
        notificationSent: true // Already seen via notification
      }
    })
  }

  // Save user message
  const userMessage = await prisma.message.create({
    data: {
      conversationId: conversation.id,
      role: 'user',
      content: message,
      status: 'completed',
      notificationSent: true // User messages don't need notifications
    }
  })

  // Create pending assistant message (will be filled by process-pending cron job)
  const pendingAssistantMessage = await prisma.message.create({
    data: {
      conversationId: conversation.id,
      role: 'assistant',
      content: '', // Will be filled by process-pending
      status: 'pending',
      notificationSent: false // May need notification if user leaves
    }
  })

  // Update conversation timestamp
  await prisma.conversation.update({
    where: { id: conversation.id },
    data: { updatedAt: new Date() }
  })

  // Return immediately - AI processing happens in background via cron
  return NextResponse.json({
    conversationId: conversation.id,
    userMessage: {
      id: userMessage.id,
      role: userMessage.role,
      content: userMessage.content,
      createdAt: userMessage.createdAt.toISOString()
    },
    pendingMessage: {
      id: pendingAssistantMessage.id,
      role: pendingAssistantMessage.role,
      status: 'pending',
      createdAt: pendingAssistantMessage.createdAt.toISOString()
    },
    processing: true
  })
}
