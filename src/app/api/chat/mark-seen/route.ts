import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// POST /api/chat/mark-seen - Mark a message as seen by the user
// This prevents the cron job from sending a push notification
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

  const { messageId } = body

  if (!messageId) {
    return NextResponse.json({ error: 'messageId is required' }, { status: 400 })
  }

  // Verify the message belongs to the user
  const message = await prisma.message.findFirst({
    where: {
      id: messageId,
      conversation: {
        userId: session.user.id
      }
    }
  })

  if (!message) {
    return NextResponse.json({ error: 'Message not found' }, { status: 404 })
  }

  // Mark as seen (notificationSent = true means no notification needed)
  await prisma.message.update({
    where: { id: messageId },
    data: { notificationSent: true }
  })

  return NextResponse.json({ success: true })
}
