import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// GET /api/daily-tools/preferences - Get user's daily tool generation preferences
export async function GET() {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const user = await prisma.user.findUnique({
      where: { id: session.user.id },
      select: {
        dailyToolEnabled: true,
        dailyToolHour: true,
        dailyToolMinute: true,
        timezone: true
      }
    })

    if (!user) {
      return NextResponse.json({ error: 'User not found' }, { status: 404 })
    }

    return NextResponse.json({
      enabled: user.dailyToolEnabled,
      hour: user.dailyToolHour,
      minute: user.dailyToolMinute,
      timezone: user.timezone
    })
  } catch (error) {
    console.error('Error fetching daily tool preferences:', error)
    return NextResponse.json(
      { error: 'Failed to fetch preferences' },
      { status: 500 }
    )
  }
}

// PUT /api/daily-tools/preferences - Update user's daily tool generation preferences
export async function PUT(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const body = await request.json()
    const { enabled, hour, minute, timezone } = body

    // Validate inputs
    if (typeof enabled !== 'boolean') {
      return NextResponse.json(
        { error: 'enabled must be a boolean' },
        { status: 400 }
      )
    }

    if (enabled) {
      if (typeof hour !== 'number' || hour < 0 || hour > 23) {
        return NextResponse.json(
          { error: 'hour must be a number between 0 and 23' },
          { status: 400 }
        )
      }

      if (typeof minute !== 'number' || minute < 0 || minute > 59) {
        return NextResponse.json(
          { error: 'minute must be a number between 0 and 59' },
          { status: 400 }
        )
      }

      // Validate timezone if provided
      if (timezone) {
        try {
          Intl.DateTimeFormat(undefined, { timeZone: timezone })
        } catch {
          return NextResponse.json(
            { error: 'Invalid timezone' },
            { status: 400 }
          )
        }
      }
    }

    const user = await prisma.user.update({
      where: { id: session.user.id },
      data: {
        dailyToolEnabled: enabled,
        dailyToolHour: enabled ? hour : null,
        dailyToolMinute: enabled ? minute : null,
        ...(timezone ? { timezone } : {})
      },
      select: {
        dailyToolEnabled: true,
        dailyToolHour: true,
        dailyToolMinute: true,
        timezone: true
      }
    })

    return NextResponse.json({
      enabled: user.dailyToolEnabled,
      hour: user.dailyToolHour,
      minute: user.dailyToolMinute,
      timezone: user.timezone
    })
  } catch (error) {
    console.error('Error updating daily tool preferences:', error)
    return NextResponse.json(
      { error: 'Failed to update preferences' },
      { status: 500 }
    )
  }
}
