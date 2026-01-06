import { NextRequest, NextResponse } from 'next/server'
import { timingSafeEqual } from 'crypto'
import webpush from 'web-push'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import {
  NOTIFICATION_SYSTEM_PROMPT,
  buildNotificationPrompt,
  getTimeOfDay,
  type NotificationContext
} from '@/lib/prompts/coach'

// Configure web-push with VAPID keys
const vapidPublicKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY
const vapidPrivateKey = process.env.VAPID_PRIVATE_KEY
const vapidSubject = process.env.VAPID_SUBJECT || 'mailto:admin@example.com'

if (vapidPublicKey && vapidPrivateKey) {
  webpush.setVapidDetails(vapidSubject, vapidPublicKey, vapidPrivateKey)
}

interface GeneratedNotification {
  title: string
  body: string
  topicReference: string
}

async function generateNotificationForUser(
  userId: string,
  userName: string | null
): Promise<GeneratedNotification | null> {
  const timeOfDay = getTimeOfDay()

  // Get recent journal entries (last 7 days)
  const sevenDaysAgo = new Date()
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7)

  const recentEntries = await prisma.journalEntry.findMany({
    where: {
      userId,
      date: { gte: sevenDaysAgo }
    },
    orderBy: { date: 'desc' },
    take: 5,
    select: {
      content: true,
      mood: true,
      tags: true,
      date: true
    }
  })

  // Get recent notifications sent to this user (last 3 days)
  const threeDaysAgo = new Date()
  threeDaysAgo.setDate(threeDaysAgo.getDate() - 3)

  const recentNotifications = await prisma.sentNotification.findMany({
    where: {
      userId,
      sentAt: { gte: threeDaysAgo }
    },
    orderBy: { sentAt: 'desc' },
    take: 10,
    select: {
      body: true,
      topicReference: true,
      timeOfDay: true,
      sentAt: true
    }
  })

  const context: NotificationContext = {
    recentEntries,
    recentNotifications,
    timeOfDay,
    userName
  }

  const userPrompt = buildNotificationPrompt(context)

  try {
    const response = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 256,
      system: NOTIFICATION_SYSTEM_PROMPT,
      messages: [{ role: 'user', content: userPrompt }]
    })

    const textContent = response.content.find((c) => c.type === 'text')
    if (!textContent || textContent.type !== 'text') {
      console.error('No text content in AI response')
      return null
    }

    // Parse JSON response
    const jsonMatch = textContent.text.match(/\{[\s\S]*\}/)
    if (!jsonMatch) {
      console.error('Could not extract JSON from AI response:', textContent.text)
      return null
    }

    const notification = JSON.parse(jsonMatch[0]) as GeneratedNotification

    // Validate response structure
    if (!notification.title || !notification.body) {
      console.error('Invalid notification structure:', notification)
      return null
    }

    // Enforce length limits
    notification.title = notification.title.substring(0, 50)
    notification.body = notification.body.substring(0, 100)
    notification.topicReference = notification.topicReference?.substring(0, 200) || 'general check-in'

    return notification
  } catch (error) {
    console.error('Error generating notification:', error)
    return null
  }
}

// POST /api/notifications/send-dynamic - Send AI-generated personalized notifications
// This endpoint should be called by an external cron service at different times throughout the day
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

  if (!vapidPublicKey || !vapidPrivateKey) {
    console.error('VAPID keys not configured')
    return NextResponse.json(
      { error: 'Push notifications not configured' },
      { status: 500 }
    )
  }

  const timeOfDay = getTimeOfDay()

  try {
    // Get all users with push subscriptions
    const usersWithSubscriptions = await prisma.user.findMany({
      where: {
        pushSubscriptions: { some: {} }
      },
      include: {
        pushSubscriptions: true
      }
    })

    if (usersWithSubscriptions.length === 0) {
      return NextResponse.json({ message: 'No users with subscriptions', sent: 0 })
    }

    const results = await Promise.allSettled(
      usersWithSubscriptions.map(async (user) => {
        // Generate personalized notification for this user
        const notification = await generateNotificationForUser(user.id, user.name)

        if (!notification) {
          return { success: false, userId: user.id, reason: 'generation_failed' }
        }

        const payload = {
          title: notification.title,
          body: notification.body,
          icon: '/icons/icon-192.svg',
          badge: '/icons/icon-192.svg',
          tag: `coach-checkin-${Date.now()}`,
          data: { url: '/coach' }
        }

        // Send to all of user's subscriptions
        const sendResults = await Promise.allSettled(
          user.pushSubscriptions.map(async (sub) => {
            const pushSubscription = {
              endpoint: sub.endpoint,
              keys: {
                p256dh: sub.p256dh,
                auth: sub.auth
              }
            }

            try {
              await webpush.sendNotification(pushSubscription, JSON.stringify(payload))
              return { success: true, endpoint: sub.endpoint }
            } catch (error: unknown) {
              const webpushError = error as { statusCode?: number }
              if (webpushError.statusCode === 410 || webpushError.statusCode === 404) {
                await prisma.pushSubscription.delete({
                  where: { id: sub.id }
                })
                return { success: false, endpoint: sub.endpoint, removed: true }
              }
              throw error
            }
          })
        )

        const anySent = sendResults.some(
          (r) => r.status === 'fulfilled' && r.value.success
        )

        // Record the sent notification if at least one push was successful
        if (anySent) {
          await prisma.sentNotification.create({
            data: {
              userId: user.id,
              title: notification.title,
              body: notification.body,
              topicReference: notification.topicReference,
              timeOfDay
            }
          })
        }

        return {
          success: anySent,
          userId: user.id,
          notification: anySent ? notification : null,
          subscriptionResults: sendResults.length
        }
      })
    )

    const successful = results.filter(
      (r) => r.status === 'fulfilled' && r.value.success
    ).length
    const failed = results.filter(
      (r) => r.status === 'rejected' || (r.status === 'fulfilled' && !r.value.success)
    ).length

    return NextResponse.json({
      message: 'Dynamic notifications sent',
      timeOfDay,
      usersProcessed: usersWithSubscriptions.length,
      successful,
      failed
    })
  } catch (error) {
    console.error('Error sending dynamic notifications:', error)
    return NextResponse.json(
      { error: 'Failed to send notifications' },
      { status: 500 }
    )
  }
}

// GET /api/notifications/send-dynamic - Return info about the dynamic notification system
export async function GET() {
  const timeOfDay = getTimeOfDay()

  return NextResponse.json({
    description: 'Dynamic AI-generated notification endpoint',
    currentTimeOfDay: timeOfDay,
    usage: 'POST with Authorization: Bearer CRON_SECRET header'
  })
}
