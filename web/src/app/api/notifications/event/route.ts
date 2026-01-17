import { NextRequest, NextResponse } from 'next/server'
import { timingSafeEqual } from 'crypto'
import webpush from 'web-push'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import {
  EVENT_MESSAGE_GENERATION_SYSTEM_PROMPT,
  buildEventMessagePrompt
} from '@/lib/prompts/coach'

// Configure web-push with VAPID keys
const vapidPublicKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY
const vapidPrivateKey = process.env.VAPID_PRIVATE_KEY
const vapidSubject = process.env.VAPID_SUBJECT || 'mailto:admin@example.com'

if (vapidPublicKey && vapidPrivateKey) {
  webpush.setVapidDetails(vapidSubject, vapidPublicKey, vapidPrivateKey)
}

interface NotificationMessage {
  title: string
  body: string
}

// Generate notification message on-demand using AI
async function generateNotificationMessage(
  eventTitle: string,
  eventDescription: string | null,
  eventStartTime: Date,
  eventEndTime: Date | null,
  eventLocation: string | null,
  type: 'before' | 'after',
  userName: string | null
): Promise<NotificationMessage> {
  const prompt = buildEventMessagePrompt(
    {
      title: eventTitle,
      description: eventDescription,
      startTime: eventStartTime,
      endTime: eventEndTime,
      isAllDay: false,
      location: eventLocation
    },
    type,
    userName
  )

  const response = await anthropic.messages.create({
    model: CLAUDE_MODEL,
    max_tokens: 256,
    system: EVENT_MESSAGE_GENERATION_SYSTEM_PROMPT,
    messages: [{ role: 'user', content: prompt }]
  })

  const textContent = response.content.find(block => block.type === 'text')
  if (!textContent || textContent.type !== 'text') {
    throw new Error('No text response from AI')
  }

  try {
    let jsonText = textContent.text
    const jsonMatch = jsonText.match(/```(?:json)?\s*([\s\S]*?)```/)
    if (jsonMatch) {
      jsonText = jsonMatch[1].trim()
    }
    return JSON.parse(jsonText)
  } catch {
    // Fallback to generic messages
    if (type === 'before') {
      return {
        title: 'Event Coming Up',
        body: `Your "${eventTitle}" is starting soon. Good luck!`
      }
    } else {
      return {
        title: 'Event Finished',
        body: `How did "${eventTitle}" go? Feel free to reflect on it.`
      }
    }
  }
}

// POST /api/notifications/event - Send pending event-based notifications
// This endpoint should be called by an external cron service (e.g., every 5 minutes)
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

  const now = new Date()
  const results = {
    beforeNotificationsSent: 0,
    afterNotificationsSent: 0,
    errors: 0
  }

  try {
    // Find event notifications that need to be sent BEFORE the event
    const beforeNotifications = await prisma.eventNotification.findMany({
      where: {
        notifyBefore: true,
        beforeNotificationSent: false,
        minutesBefore: { not: null }
      },
      include: {
        agendaItem: true,
        user: {
          select: { name: true }
        }
      }
    })

    // Process before notifications
    for (const notification of beforeNotifications) {
      const eventStartTime = notification.agendaItem.startTime
      const minutesBefore = notification.minutesBefore!
      const notifyTime = new Date(eventStartTime.getTime() - minutesBefore * 60 * 1000)

      // Check if it's time to send the notification
      if (now >= notifyTime && now < eventStartTime) {
        try {
          // Get or generate the message
          let message = notification.beforeMessage
          let title = 'Event Reminder'

          if (!message) {
            // Generate message on-demand
            const generated = await generateNotificationMessage(
              notification.agendaItem.title,
              notification.agendaItem.description,
              notification.agendaItem.startTime,
              notification.agendaItem.endTime,
              notification.agendaItem.location,
              'before',
              notification.user?.name || null
            )
            title = generated.title
            message = generated.body

            // Save the generated message for reference
            await prisma.eventNotification.update({
              where: { id: notification.id },
              data: { beforeMessage: message }
            })
          }

          // Get user's push subscriptions
          const subscriptions = await prisma.pushSubscription.findMany({
            where: { userId: notification.userId }
          })

          // Send notifications to all user's devices
          const payload = {
            title,
            body: message,
            icon: '/icons/icon-192.svg',
            badge: '/icons/icon-192.svg',
            tag: `event-before-${notification.agendaItemId}`,
            data: { url: '/journal' }
          }

          for (const sub of subscriptions) {
            try {
              await webpush.sendNotification(
                {
                  endpoint: sub.endpoint,
                  keys: { p256dh: sub.p256dh, auth: sub.auth }
                },
                JSON.stringify(payload)
              )
            } catch (error: unknown) {
              const webpushError = error as { statusCode?: number }
              if (webpushError.statusCode === 410 || webpushError.statusCode === 404) {
                await prisma.pushSubscription.delete({ where: { id: sub.id } })
              }
            }
          }

          // Mark as sent
          await prisma.eventNotification.update({
            where: { id: notification.id },
            data: {
              beforeNotificationSent: true,
              beforeSentAt: now
            }
          })

          // Record in sent notifications
          await prisma.sentNotification.create({
            data: {
              userId: notification.userId,
              title,
              body: message,
              topicReference: `Event: ${notification.agendaItem.title}`,
              timeOfDay: getTimeOfDay(now)
            }
          })

          results.beforeNotificationsSent++
        } catch (error) {
          console.error('Error sending before notification:', error)
          results.errors++
        }
      }
    }

    // Find event notifications that need to be sent AFTER the event
    const afterNotifications = await prisma.eventNotification.findMany({
      where: {
        notifyAfter: true,
        afterNotificationSent: false,
        minutesAfter: { not: null }
      },
      include: {
        agendaItem: true,
        user: {
          select: { name: true }
        }
      }
    })

    // Process after notifications
    for (const notification of afterNotifications) {
      // Use end time if available, otherwise use start time
      const eventEndTime = notification.agendaItem.endTime || notification.agendaItem.startTime
      const minutesAfter = notification.minutesAfter!
      const notifyTime = new Date(eventEndTime.getTime() + minutesAfter * 60 * 1000)

      // Check if it's time to send the notification (within a 30-minute window after notify time)
      const windowEnd = new Date(notifyTime.getTime() + 30 * 60 * 1000)
      if (now >= notifyTime && now < windowEnd) {
        try {
          // Get or generate the message
          let message = notification.afterMessage
          let title = 'Event Follow-up'

          if (!message) {
            // Generate message on-demand
            const generated = await generateNotificationMessage(
              notification.agendaItem.title,
              notification.agendaItem.description,
              notification.agendaItem.startTime,
              notification.agendaItem.endTime,
              notification.agendaItem.location,
              'after',
              notification.user?.name || null
            )
            title = generated.title
            message = generated.body

            // Save the generated message for reference
            await prisma.eventNotification.update({
              where: { id: notification.id },
              data: { afterMessage: message }
            })
          }

          // Get user's push subscriptions
          const subscriptions = await prisma.pushSubscription.findMany({
            where: { userId: notification.userId }
          })

          // Send notifications to all user's devices
          const payload = {
            title,
            body: message,
            icon: '/icons/icon-192.svg',
            badge: '/icons/icon-192.svg',
            tag: `event-after-${notification.agendaItemId}`,
            data: { url: '/coach' }
          }

          for (const sub of subscriptions) {
            try {
              await webpush.sendNotification(
                {
                  endpoint: sub.endpoint,
                  keys: { p256dh: sub.p256dh, auth: sub.auth }
                },
                JSON.stringify(payload)
              )
            } catch (error: unknown) {
              const webpushError = error as { statusCode?: number }
              if (webpushError.statusCode === 410 || webpushError.statusCode === 404) {
                await prisma.pushSubscription.delete({ where: { id: sub.id } })
              }
            }
          }

          // Mark as sent
          await prisma.eventNotification.update({
            where: { id: notification.id },
            data: {
              afterNotificationSent: true,
              afterSentAt: now
            }
          })

          // Record in sent notifications
          await prisma.sentNotification.create({
            data: {
              userId: notification.userId,
              title,
              body: message,
              topicReference: `Event follow-up: ${notification.agendaItem.title}`,
              timeOfDay: getTimeOfDay(now)
            }
          })

          results.afterNotificationsSent++
        } catch (error) {
          console.error('Error sending after notification:', error)
          results.errors++
        }
      }
    }

    return NextResponse.json({
      message: 'Event notifications processed',
      ...results
    })
  } catch (error) {
    console.error('Error processing event notifications:', error)
    return NextResponse.json(
      { error: 'Failed to process event notifications' },
      { status: 500 }
    )
  }
}

// Helper function to determine time of day
function getTimeOfDay(date: Date): string {
  const hour = date.getHours()
  if (hour >= 5 && hour < 12) return 'morning'
  if (hour >= 12 && hour < 17) return 'afternoon'
  if (hour >= 17 && hour < 21) return 'evening'
  return 'night'
}

// GET /api/notifications/event - Get pending event notifications for a user
export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams
  const userId = searchParams.get('userId')

  if (!userId) {
    return NextResponse.json({ error: 'userId is required' }, { status: 400 })
  }

  try {
    const notifications = await prisma.eventNotification.findMany({
      where: {
        userId,
        OR: [
          { notifyBefore: true, beforeNotificationSent: false },
          { notifyAfter: true, afterNotificationSent: false }
        ]
      },
      include: {
        agendaItem: {
          select: {
            id: true,
            title: true,
            startTime: true,
            endTime: true
          }
        }
      },
      orderBy: {
        agendaItem: {
          startTime: 'asc'
        }
      }
    })

    return NextResponse.json(notifications)
  } catch (error) {
    console.error('Error fetching event notifications:', error)
    return NextResponse.json(
      { error: 'Failed to fetch event notifications' },
      { status: 500 }
    )
  }
}
