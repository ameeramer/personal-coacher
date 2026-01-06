import { NextRequest, NextResponse } from 'next/server'
import { timingSafeEqual } from 'crypto'
import webpush from 'web-push'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { buildCoachContext } from '@/lib/prompts/coach'

// Configure web-push with VAPID keys
const vapidPublicKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY
const vapidPrivateKey = process.env.VAPID_PRIVATE_KEY
const vapidSubject = process.env.VAPID_SUBJECT || 'mailto:admin@example.com'

if (vapidPublicKey && vapidPrivateKey) {
  webpush.setVapidDetails(vapidSubject, vapidPublicKey, vapidPrivateKey)
}

// Time threshold before sending notification (30 seconds)
// This gives the frontend time to mark messages as seen if user is still on page
const NOTIFICATION_DELAY_MS = 30000

async function sendPushNotification(
  userId: string,
  title: string,
  body: string,
  conversationId: string
): Promise<boolean> {
  if (!vapidPublicKey || !vapidPrivateKey) {
    console.log('VAPID keys not configured, skipping push notification')
    return false
  }

  const subscriptions = await prisma.pushSubscription.findMany({
    where: { userId }
  })

  if (subscriptions.length === 0) {
    console.log(`No push subscriptions for user ${userId}`)
    return false
  }

  const payload = {
    title,
    body: body.length > 100 ? body.substring(0, 97) + '...' : body,
    icon: '/icons/icon-192.svg',
    badge: '/icons/icon-192.svg',
    tag: `coach-response-${conversationId}`,
    data: { url: '/coach' }
  }

  const results = await Promise.allSettled(
    subscriptions.map(async (sub) => {
      try {
        await webpush.sendNotification(
          {
            endpoint: sub.endpoint,
            keys: { p256dh: sub.p256dh, auth: sub.auth }
          },
          JSON.stringify(payload)
        )
        return { success: true }
      } catch (error: unknown) {
        const webpushError = error as { statusCode?: number }
        if (webpushError.statusCode === 410 || webpushError.statusCode === 404) {
          // Subscription expired, remove it
          await prisma.pushSubscription.delete({ where: { id: sub.id } })
        }
        return { success: false }
      }
    })
  )

  return results.some(r => r.status === 'fulfilled' && r.value.success)
}

// POST /api/chat/process-pending - Process pending AI messages
// This endpoint should be called by a cron job every 30-60 seconds
// Protected by CRON_SECRET environment variable
export async function POST(request: NextRequest) {
  // Verify cron secret for security
  const authHeader = request.headers.get('authorization')
  const cronSecret = process.env.CRON_SECRET

  if (!cronSecret) {
    console.error('CRON_SECRET not configured')
    return NextResponse.json(
      { error: 'Server not configured for cron jobs' },
      { status: 500 }
    )
  }

  // Use timing-safe comparison to prevent timing attacks
  const expected = Buffer.from(`Bearer ${cronSecret}`)
  const provided = Buffer.from(authHeader || '')
  if (expected.length !== provided.length || !timingSafeEqual(expected, provided)) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  try {
    // Find all pending assistant messages
    const pendingMessages = await prisma.message.findMany({
      where: {
        role: 'assistant',
        status: 'pending'
      },
      include: {
        conversation: {
          include: {
            user: true,
            messages: {
              orderBy: { createdAt: 'asc' }
            }
          }
        }
      },
      orderBy: { createdAt: 'asc' } // Process oldest first
    })

    if (pendingMessages.length === 0) {
      return NextResponse.json({ message: 'No pending messages', processed: 0 })
    }

    const results = await Promise.allSettled(
      pendingMessages.map(async (pendingMsg) => {
        const conversation = pendingMsg.conversation
        const userId = conversation.userId

        // Get recent journal entries for context
        const recentEntries = await prisma.journalEntry.findMany({
          where: { userId },
          orderBy: { date: 'desc' },
          take: 5
        })

        const entryContents = recentEntries.map(e =>
          `[${e.date.toLocaleDateString()}]${e.mood ? ` (Mood: ${e.mood})` : ''}\n${e.content}`
        )

        // Build conversation history (exclude the pending message itself)
        const conversationHistory = conversation.messages
          .filter(m => m.id !== pendingMsg.id && m.content) // Exclude pending and empty messages
          .map(m => ({
            role: m.role as 'user' | 'assistant',
            content: m.content
          }))

        // Call Claude API
        const systemPrompt = buildCoachContext(entryContents)

        const response = await anthropic.messages.create({
          model: CLAUDE_MODEL,
          max_tokens: 1024,
          system: systemPrompt,
          messages: conversationHistory
        })

        const assistantContent = response.content[0].type === 'text'
          ? response.content[0].text
          : ''

        // Check if we need to send notification
        // Only send if: notificationSent is false AND message is older than threshold
        const messageAge = Date.now() - pendingMsg.createdAt.getTime()
        const shouldNotify = !pendingMsg.notificationSent && messageAge >= NOTIFICATION_DELAY_MS

        let notificationSent = pendingMsg.notificationSent
        if (shouldNotify) {
          const sent = await sendPushNotification(
            userId,
            'Coach replied',
            assistantContent,
            conversation.id
          )
          if (sent) {
            notificationSent = true
          }
        }

        // Update the message with the AI response
        await prisma.message.update({
          where: { id: pendingMsg.id },
          data: {
            content: assistantContent,
            status: 'completed',
            notificationSent
          }
        })

        // Update conversation timestamp
        await prisma.conversation.update({
          where: { id: conversation.id },
          data: { updatedAt: new Date() }
        })

        return {
          messageId: pendingMsg.id,
          conversationId: conversation.id,
          notificationSent: shouldNotify && notificationSent
        }
      })
    )

    const successful = results.filter(r => r.status === 'fulfilled').length
    const failed = results.filter(r => r.status === 'rejected').length
    const notified = results.filter(
      r => r.status === 'fulfilled' && r.value.notificationSent
    ).length

    return NextResponse.json({
      message: 'Pending messages processed',
      processed: pendingMessages.length,
      successful,
      failed,
      notificationsSent: notified
    })
  } catch (error) {
    console.error('Error processing pending messages:', error)
    return NextResponse.json(
      { error: 'Failed to process pending messages' },
      { status: 500 }
    )
  }
}

// GET /api/chat/process-pending - Return info about the endpoint
export async function GET() {
  const pendingCount = await prisma.message.count({
    where: {
      role: 'assistant',
      status: 'pending'
    }
  })

  return NextResponse.json({
    description: 'Process pending AI chat messages',
    pendingMessages: pendingCount,
    usage: 'POST with Authorization: Bearer CRON_SECRET header'
  })
}
