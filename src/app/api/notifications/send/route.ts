import { NextRequest, NextResponse } from 'next/server'
import webpush from 'web-push'
import { prisma } from '@/lib/prisma'

// Configure web-push with VAPID keys
const vapidPublicKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY
const vapidPrivateKey = process.env.VAPID_PRIVATE_KEY
const vapidSubject = process.env.VAPID_SUBJECT || 'mailto:admin@example.com'

if (vapidPublicKey && vapidPrivateKey) {
  webpush.setVapidDetails(vapidSubject, vapidPublicKey, vapidPrivateKey)
}

// POST /api/notifications/send - Send push notifications to all subscribers
// This endpoint should be called by an external cron service (e.g., cron-job.org)
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

  if (authHeader !== `Bearer ${cronSecret}`) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  if (!vapidPublicKey || !vapidPrivateKey) {
    console.error('VAPID keys not configured')
    return NextResponse.json(
      { error: 'Push notifications not configured' },
      { status: 500 }
    )
  }

  try {
    // Get custom notification payload if provided
    let payload = {
      title: 'Journal Reminder',
      body: "Time to reflect on your day! Take a moment to journal your thoughts.",
      icon: '/icons/icon-192.svg',
      badge: '/icons/icon-192.svg',
      tag: 'journal-reminder',
      data: { url: '/journal' }
    }

    try {
      const body = await request.json()
      if (body.title || body.body) {
        payload = { ...payload, ...body }
      }
    } catch {
      // No custom payload provided, use defaults
    }

    // Get all push subscriptions
    const subscriptions = await prisma.pushSubscription.findMany()

    if (subscriptions.length === 0) {
      return NextResponse.json({ message: 'No subscribers', sent: 0 })
    }

    // Send notifications to all subscribers
    const results = await Promise.allSettled(
      subscriptions.map(async (sub) => {
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
          // If subscription is invalid (410 Gone or 404), remove it
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

    const successful = results.filter(
      (r) => r.status === 'fulfilled' && r.value.success
    ).length
    const failed = results.filter((r) => r.status === 'rejected').length
    const removed = results.filter(
      (r) => r.status === 'fulfilled' && !r.value.success && r.value.removed
    ).length

    return NextResponse.json({
      message: 'Notifications sent',
      sent: successful,
      failed,
      removed
    })
  } catch (error) {
    console.error('Error sending push notifications:', error)
    return NextResponse.json(
      { error: 'Failed to send notifications' },
      { status: 500 }
    )
  }
}

// GET /api/notifications/send - Return VAPID public key for subscription
export async function GET() {
  if (!vapidPublicKey) {
    return NextResponse.json(
      { error: 'Push notifications not configured' },
      { status: 500 }
    )
  }

  return NextResponse.json({ vapidPublicKey })
}
