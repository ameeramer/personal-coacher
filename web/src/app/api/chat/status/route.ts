import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// GET /api/chat/status?messageId=xxx - Check status of a specific message
// or GET /api/chat/status?conversationId=xxx - Get latest messages in conversation
export async function GET(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { searchParams } = new URL(request.url)
  const messageId = searchParams.get('messageId')
  const conversationId = searchParams.get('conversationId')

  if (messageId) {
    // Check status of a specific message
    const message = await prisma.message.findFirst({
      where: {
        id: messageId,
        conversation: {
          userId: session.user.id
        }
      },
      select: {
        id: true,
        role: true,
        content: true,
        status: true,
        createdAt: true
      }
    })

    if (!message) {
      return NextResponse.json({ error: 'Message not found' }, { status: 404 })
    }

    return NextResponse.json({
      message: {
        id: message.id,
        role: message.role,
        content: message.content,
        status: message.status,
        createdAt: message.createdAt.toISOString()
      }
    })
  }

  if (conversationId) {
    // Verify conversation belongs to user
    const conversation = await prisma.conversation.findFirst({
      where: {
        id: conversationId,
        userId: session.user.id
      }
    })

    if (!conversation) {
      return NextResponse.json({ error: 'Conversation not found' }, { status: 404 })
    }

    // Get recent messages (last 5) with their status
    const messages = await prisma.message.findMany({
      where: { conversationId },
      orderBy: { createdAt: 'desc' },
      take: 5,
      select: {
        id: true,
        role: true,
        content: true,
        status: true,
        createdAt: true
      }
    })

    return NextResponse.json({
      messages: messages.reverse().map(m => ({
        id: m.id,
        role: m.role,
        content: m.content,
        status: m.status,
        createdAt: m.createdAt.toISOString()
      }))
    })
  }

  return NextResponse.json(
    { error: 'messageId or conversationId query parameter is required' },
    { status: 400 }
  )
}
