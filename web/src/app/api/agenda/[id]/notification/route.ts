import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import {
  EVENT_NOTIFICATION_SYSTEM_PROMPT,
  buildEventNotificationPrompt,
  EventNotificationAnalysis
} from '@/lib/prompts/coach'

// GET - Get notification settings for an agenda item
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const { id } = await params

    const agendaItem = await prisma.agendaItem.findUnique({
      where: { id },
      include: { eventNotifications: true }
    })

    if (!agendaItem) {
      return NextResponse.json({ error: 'Agenda item not found' }, { status: 404 })
    }

    if (agendaItem.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    const notification = agendaItem.eventNotifications[0] || null

    return NextResponse.json({
      agendaItemId: id,
      notification
    })
  } catch (error) {
    console.error('Error fetching event notification:', error)
    return NextResponse.json({
      error: 'Failed to fetch event notification',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// POST - Analyze agenda item with AI and create notification settings
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const { id } = await params

    const agendaItem = await prisma.agendaItem.findUnique({
      where: { id }
    })

    if (!agendaItem) {
      return NextResponse.json({ error: 'Agenda item not found' }, { status: 404 })
    }

    if (agendaItem.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    // Get user name for personalization
    const user = await prisma.user.findUnique({
      where: { id: session.user.id },
      select: { name: true }
    })

    // Build prompt for AI analysis
    const prompt = buildEventNotificationPrompt({
      title: agendaItem.title,
      description: agendaItem.description,
      startTime: agendaItem.startTime,
      endTime: agendaItem.endTime,
      isAllDay: agendaItem.isAllDay,
      location: agendaItem.location
    }, user?.name)

    // Call Claude to analyze the event
    const response = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 1024,
      system: EVENT_NOTIFICATION_SYSTEM_PROMPT,
      messages: [{ role: 'user', content: prompt }]
    })

    // Parse the AI response
    const textContent = response.content.find(block => block.type === 'text')
    if (!textContent || textContent.type !== 'text') {
      throw new Error('No text response from AI')
    }

    let analysis: EventNotificationAnalysis
    try {
      // Extract JSON from the response (handle potential markdown code blocks)
      let jsonText = textContent.text
      const jsonMatch = jsonText.match(/```(?:json)?\s*([\s\S]*?)```/)
      if (jsonMatch) {
        jsonText = jsonMatch[1].trim()
      }
      analysis = JSON.parse(jsonText)
    } catch {
      console.error('Failed to parse AI response:', textContent.text)
      throw new Error('Invalid AI response format')
    }

    // Create or update the event notification settings
    const notification = await prisma.eventNotification.upsert({
      where: { agendaItemId: id },
      create: {
        agendaItemId: id,
        userId: session.user.id,
        notifyBefore: analysis.shouldNotifyBefore,
        minutesBefore: analysis.minutesBefore || null,
        beforeMessage: analysis.beforeMessage || null,
        notifyAfter: analysis.shouldNotifyAfter,
        minutesAfter: analysis.minutesAfter || null,
        afterMessage: analysis.afterMessage || null,
        aiDetermined: true,
        aiReasoning: analysis.reasoning
      },
      update: {
        notifyBefore: analysis.shouldNotifyBefore,
        minutesBefore: analysis.minutesBefore || null,
        beforeMessage: analysis.beforeMessage || null,
        notifyAfter: analysis.shouldNotifyAfter,
        minutesAfter: analysis.minutesAfter || null,
        afterMessage: analysis.afterMessage || null,
        aiDetermined: true,
        aiReasoning: analysis.reasoning
      }
    })

    return NextResponse.json({
      agendaItemId: id,
      notification,
      analysis
    })
  } catch (error) {
    console.error('Error analyzing event for notifications:', error)
    return NextResponse.json({
      error: 'Failed to analyze event for notifications',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// PUT - Manually update notification settings (user override)
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const { id } = await params
    const body = await request.json()

    const agendaItem = await prisma.agendaItem.findUnique({
      where: { id }
    })

    if (!agendaItem) {
      return NextResponse.json({ error: 'Agenda item not found' }, { status: 404 })
    }

    if (agendaItem.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    // Validate input
    const {
      notifyBefore,
      minutesBefore,
      beforeMessage,
      notifyAfter,
      minutesAfter,
      afterMessage
    } = body

    if (notifyBefore !== undefined && typeof notifyBefore !== 'boolean') {
      return NextResponse.json({ error: 'notifyBefore must be a boolean' }, { status: 400 })
    }
    if (minutesBefore !== undefined && minutesBefore !== null && (typeof minutesBefore !== 'number' || minutesBefore < 1)) {
      return NextResponse.json({ error: 'minutesBefore must be a positive number' }, { status: 400 })
    }
    if (notifyAfter !== undefined && typeof notifyAfter !== 'boolean') {
      return NextResponse.json({ error: 'notifyAfter must be a boolean' }, { status: 400 })
    }
    if (minutesAfter !== undefined && minutesAfter !== null && (typeof minutesAfter !== 'number' || minutesAfter < 1)) {
      return NextResponse.json({ error: 'minutesAfter must be a positive number' }, { status: 400 })
    }

    // Create or update the notification settings
    const notification = await prisma.eventNotification.upsert({
      where: { agendaItemId: id },
      create: {
        agendaItemId: id,
        userId: session.user.id,
        notifyBefore: notifyBefore ?? false,
        minutesBefore: minutesBefore ?? null,
        beforeMessage: beforeMessage ?? null,
        notifyAfter: notifyAfter ?? false,
        minutesAfter: minutesAfter ?? null,
        afterMessage: afterMessage ?? null,
        aiDetermined: false
      },
      update: {
        ...(notifyBefore !== undefined && { notifyBefore }),
        ...(minutesBefore !== undefined && { minutesBefore }),
        ...(beforeMessage !== undefined && { beforeMessage }),
        ...(notifyAfter !== undefined && { notifyAfter }),
        ...(minutesAfter !== undefined && { minutesAfter }),
        ...(afterMessage !== undefined && { afterMessage }),
        aiDetermined: false
      }
    })

    return NextResponse.json({
      agendaItemId: id,
      notification
    })
  } catch (error) {
    console.error('Error updating event notification:', error)
    return NextResponse.json({
      error: 'Failed to update event notification',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// DELETE - Remove notification settings
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const { id } = await params

    const agendaItem = await prisma.agendaItem.findUnique({
      where: { id }
    })

    if (!agendaItem) {
      return NextResponse.json({ error: 'Agenda item not found' }, { status: 404 })
    }

    if (agendaItem.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    await prisma.eventNotification.deleteMany({
      where: { agendaItemId: id }
    })

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('Error deleting event notification:', error)
    return NextResponse.json({
      error: 'Failed to delete event notification',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
