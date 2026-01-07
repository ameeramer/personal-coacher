import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

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

  const { message, conversationId, initialAssistantMessage } = body

  if (!message) {
    return NextResponse.json({ error: 'Message is required' }, { status: 400 })
  }

  try {
    // Use a transaction to ensure all database writes succeed or fail together
    const result = await prisma.$transaction(async (tx) => {
      // Get or create conversation
      let conversation
      if (conversationId) {
        conversation = await tx.conversation.findFirst({
          where: { id: conversationId, userId: session.user.id },
          include: { messages: { orderBy: { createdAt: 'asc' } } }
        })
        if (!conversation) {
          throw new Error('Conversation not found')
        }
      } else {
        conversation = await tx.conversation.create({
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
        await tx.message.create({
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
      const userMessage = await tx.message.create({
        data: {
          conversationId: conversation.id,
          role: 'user',
          content: message,
          status: 'completed',
          notificationSent: true // User messages don't need notifications
        }
      })

      // Create pending assistant message (will be filled by process-pending cron job)
      const pendingAssistantMessage = await tx.message.create({
        data: {
          conversationId: conversation.id,
          role: 'assistant',
          content: '', // Will be filled by process-pending
          status: 'pending',
          notificationSent: false // May need notification if user leaves
        }
      })

      // Update conversation timestamp
      await tx.conversation.update({
        where: { id: conversation.id },
        data: { updatedAt: new Date() }
      })

      return { conversation, userMessage, pendingAssistantMessage }
    })

    // Return immediately - AI processing happens in background via cron
    return NextResponse.json({
      conversationId: result.conversation.id,
      userMessage: {
        id: result.userMessage.id,
        role: result.userMessage.role,
        content: result.userMessage.content,
        createdAt: result.userMessage.createdAt.toISOString()
      },
      pendingMessage: {
        id: result.pendingAssistantMessage.id,
        role: result.pendingAssistantMessage.role,
        status: 'pending',
        createdAt: result.pendingAssistantMessage.createdAt.toISOString()
      },
      processing: true
    })
  } catch (error) {
    if (error instanceof Error && error.message === 'Conversation not found') {
      return NextResponse.json({ error: 'Conversation not found' }, { status: 404 })
    }
    throw error
  }
}
