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
  console.log(`[process-pending] sendPushNotification called for user ${userId}, conversation ${conversationId}`)

  if (!vapidPublicKey || !vapidPrivateKey) {
    console.log('[process-pending] VAPID keys not configured, skipping push notification')
    return false
  }

  const subscriptions = await prisma.pushSubscription.findMany({
    where: { userId }
  })

  console.log(`[process-pending] Found ${subscriptions.length} subscriptions for user ${userId}`)

  if (subscriptions.length === 0) {
    console.log(`[process-pending] No push subscriptions for user ${userId}`)
    return false
  }

  const payload = {
    title,
    body: body.length > 100 ? body.substring(0, 97) + '...' : body,
    icon: '/icons/icon-192.svg',
    badge: '/icons/icon-192.svg',
    tag: `coach-response-${conversationId}`,
    data: {
      url: '/coach',
      conversationId  // Include conversationId so clicking navigates to the right conversation
    }
  }

  const results = await Promise.allSettled(
    subscriptions.map(async (sub) => {
      console.log(`[process-pending] Sending push to endpoint: ${sub.endpoint.substring(0, 50)}...`)
      try {
        await webpush.sendNotification(
          {
            endpoint: sub.endpoint,
            keys: { p256dh: sub.p256dh, auth: sub.auth }
          },
          JSON.stringify(payload)
        )
        console.log(`[process-pending] Push notification sent successfully`)
        return { success: true }
      } catch (error: unknown) {
        const webpushError = error as { statusCode?: number; message?: string }
        console.log(`[process-pending] Push notification failed: ${webpushError.statusCode} - ${webpushError.message}`)
        if (webpushError.statusCode === 410 || webpushError.statusCode === 404) {
          // Subscription expired, remove it
          console.log(`[process-pending] Removing expired subscription ${sub.id}`)
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
    console.log('[process-pending] Starting cron job execution')

    // Phase 1: Process any pending messages (generate AI responses)
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

    console.log(`[process-pending] Phase 1: Found ${pendingMessages.length} pending messages to process`)

    let successful = 0
    let skipped = 0
    let failed = 0

    // Only process if there are pending messages
    if (pendingMessages.length > 0) {
      const results = await Promise.allSettled(
      pendingMessages.map(async (pendingMsg) => {
        // Use optimistic concurrency: atomically claim this message by updating
        // status from 'pending' to 'processing'. If another worker already claimed it,
        // this will update 0 rows and we skip it.
        const claimed = await prisma.message.updateMany({
          where: {
            id: pendingMsg.id,
            status: 'pending' // Only update if still pending
          },
          data: {
            status: 'processing'
          }
        })

        // If we didn't claim the message (another worker got it), skip
        if (claimed.count === 0) {
          return {
            messageId: pendingMsg.id,
            conversationId: pendingMsg.conversation.id,
            skipped: true,
            notificationSent: false
          }
        }

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
        // Claude API requires conversations to start with a user message
        // This handles cases where a conversation was initiated by the coach via dynamic notification
        const filteredMessages = conversation.messages
          .filter(m => m.id !== pendingMsg.id && m.content) // Exclude pending and empty messages

        // Find the index of the first user message
        const firstUserIndex = filteredMessages.findIndex(m => m.role === 'user')

        // Collect any initial assistant messages (from dynamic notifications) to add to context
        const initialAssistantMessages = firstUserIndex > 0
          ? filteredMessages.slice(0, firstUserIndex).filter(m => m.role === 'assistant')
          : []

        // Only include messages starting from the first user message
        // (Claude API requires conversations to start with a user message)
        const conversationHistory = filteredMessages
          .slice(firstUserIndex >= 0 ? firstUserIndex : 0)
          .map(m => ({
            role: m.role as 'user' | 'assistant',
            content: m.content
          }))

        // Call Claude API
        let systemPrompt = buildCoachContext(entryContents)

        // If there were initial assistant messages from dynamic notifications,
        // add them to the system prompt so the AI is aware of what it said
        if (initialAssistantMessages.length > 0) {
          const initialContext = initialAssistantMessages
            .map(m => m.content)
            .join('\n\n')
          systemPrompt += `\n\n## Your Previous Message (you initiated this conversation)\nYou sent a check-in message to the user that started this conversation:\n"${initialContext}"\n\nThe user is now responding to your message. Continue the conversation naturally, acknowledging what you said and responding to their reply.`
        }

        const response = await anthropic.messages.create({
          model: CLAUDE_MODEL,
          max_tokens: 1024,
          system: systemPrompt,
          messages: conversationHistory
        })

        // Validate and extract assistant content from Claude's response
        let assistantContent = ''
        if (response.content && response.content.length > 0) {
          const textBlock = response.content.find(block => block.type === 'text')
          if (textBlock && textBlock.type === 'text') {
            assistantContent = textBlock.text
          }
        }

        // Handle empty or invalid response
        if (!assistantContent) {
          console.warn(`Empty or non-text response from Claude for message ${pendingMsg.id}`)
          assistantContent = "I'm sorry, I wasn't able to generate a response. Please try again."
        }

        // Update the message with the AI response
        // Note: Notification will be sent in a separate phase (below) for messages
        // that are completed and old enough, to give the frontend time to mark as seen
        await prisma.message.update({
          where: { id: pendingMsg.id },
          data: {
            content: assistantContent,
            status: 'completed'
            // notificationSent stays false - will be updated in notification phase
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
          processed: true
        }
      })
      )

      successful = results.filter(
        r => r.status === 'fulfilled' && !r.value.skipped
      ).length
      skipped = results.filter(
        r => r.status === 'fulfilled' && r.value.skipped
      ).length
      failed = results.filter(r => r.status === 'rejected').length
    }

    // Phase 2: Send notifications for completed messages that are old enough
    // IMPORTANT: This phase runs EVERY time, regardless of whether there were pending messages
    // This is done separately from processing to give the frontend time to mark
    // messages as seen (preventing notifications for users who stayed on the page)
    const notificationThreshold = new Date(Date.now() - NOTIFICATION_DELAY_MS)
    console.log(`[process-pending] Phase 2: Looking for notifications (threshold: ${notificationThreshold.toISOString()})`)

    const messagesToNotify = await prisma.message.findMany({
      where: {
        role: 'assistant',
        status: 'completed',
        notificationSent: false,
        createdAt: { lte: notificationThreshold }
      },
      include: {
        conversation: {
          include: { user: true }
        }
      }
    })

    console.log(`[process-pending] Found ${messagesToNotify.length} messages to notify`)
    for (const msg of messagesToNotify) {
      console.log(`[process-pending] Message ${msg.id}: createdAt=${msg.createdAt.toISOString()}, conversationId=${msg.conversationId}, userId=${msg.conversation.userId}`)
    }

    // Debug: Also log ALL completed assistant messages to see what we're missing
    const allCompletedAssistant = await prisma.message.findMany({
      where: {
        role: 'assistant',
        status: 'completed'
      },
      include: {
        conversation: true
      },
      orderBy: { createdAt: 'desc' },
      take: 10
    })
    console.log(`[process-pending] DEBUG: Last 10 completed assistant messages:`)
    for (const msg of allCompletedAssistant) {
      console.log(`[process-pending]   - ${msg.id}: notificationSent=${msg.notificationSent}, createdAt=${msg.createdAt.toISOString()}, content=${msg.content.substring(0, 50)}...`)
    }

    let notificationsSent = 0
    for (const msg of messagesToNotify) {
      const sent = await sendPushNotification(
        msg.conversation.userId,
        'Coach replied',
        msg.content,
        msg.conversation.id
      )

      // Mark as notificationSent regardless of whether push succeeded
      // (to prevent retrying forever if user has no valid subscription)
      await prisma.message.update({
        where: { id: msg.id },
        data: { notificationSent: true }
      })

      if (sent) {
        notificationsSent++
      }
    }

    return NextResponse.json({
      message: 'Pending messages processed',
      processed: pendingMessages.length,
      successful,
      skipped,
      failed,
      notificationsSent,
      messagesCheckedForNotification: messagesToNotify.length
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
