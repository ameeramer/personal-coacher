import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// POST /api/notifications/subscribe - Save push subscription
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  try {
    const body = await request.json()
    const { endpoint, keys } = body

    if (!endpoint || !keys?.p256dh || !keys?.auth) {
      return NextResponse.json(
        { error: 'Invalid subscription data' },
        { status: 400 }
      )
    }

    // Check if endpoint is already registered to another user (prevent hijacking)
    const existing = await prisma.pushSubscription.findUnique({
      where: { endpoint }
    })
    if (existing && existing.userId !== session.user.id) {
      return NextResponse.json(
        { error: 'Subscription endpoint already registered to another user' },
        { status: 409 }
      )
    }

    // Rate limiting: limit subscriptions per user (max 5)
    if (!existing) {
      const existingCount = await prisma.pushSubscription.count({
        where: { userId: session.user.id }
      })
      if (existingCount >= 5) {
        return NextResponse.json(
          { error: 'Maximum subscription limit reached (5 devices)' },
          { status: 429 }
        )
      }
    }

    // Upsert subscription (update if endpoint exists for this user, create if not)
    const subscription = await prisma.pushSubscription.upsert({
      where: { endpoint },
      update: {
        p256dh: keys.p256dh,
        auth: keys.auth,
        updatedAt: new Date()
      },
      create: {
        userId: session.user.id,
        endpoint,
        p256dh: keys.p256dh,
        auth: keys.auth
      }
    })

    return NextResponse.json({ success: true, id: subscription.id })
  } catch (error) {
    console.error('Error saving push subscription:', error)
    return NextResponse.json(
      { error: 'Failed to save subscription' },
      { status: 500 }
    )
  }
}

// DELETE /api/notifications/subscribe - Remove push subscription
export async function DELETE(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  try {
    const body = await request.json()
    const { endpoint } = body

    if (!endpoint) {
      return NextResponse.json(
        { error: 'Endpoint is required' },
        { status: 400 }
      )
    }

    await prisma.pushSubscription.deleteMany({
      where: {
        endpoint,
        userId: session.user.id
      }
    })

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('Error removing push subscription:', error)
    return NextResponse.json(
      { error: 'Failed to remove subscription' },
      { status: 500 }
    )
  }
}

// GET /api/notifications/subscribe - Check subscription status
export async function GET() {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  try {
    const subscriptions = await prisma.pushSubscription.findMany({
      where: { userId: session.user.id },
      select: { id: true, endpoint: true, createdAt: true }
    })

    return NextResponse.json({
      hasSubscription: subscriptions.length > 0,
      subscriptions
    })
  } catch (error) {
    console.error('Error checking subscription:', error)
    return NextResponse.json(
      { error: 'Failed to check subscription' },
      { status: 500 }
    )
  }
}
