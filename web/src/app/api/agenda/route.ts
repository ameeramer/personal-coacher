import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// Validation constants
const MAX_TITLE_LENGTH = 200
const MAX_DESCRIPTION_LENGTH = 2000
const MAX_LOCATION_LENGTH = 500

function isValidISODate(dateString: string): boolean {
  const date = new Date(dateString)
  return !isNaN(date.getTime())
}

function validateAgendaInput(body: {
  title?: string
  description?: string
  startTime?: string
  endTime?: string
  isAllDay?: unknown
  location?: string
}): { valid: boolean; error?: string } {
  if (!body.title || typeof body.title !== 'string') {
    return { valid: false, error: 'Title is required' }
  }
  if (body.title.length > MAX_TITLE_LENGTH) {
    return { valid: false, error: `Title must be ${MAX_TITLE_LENGTH} characters or less` }
  }
  if (!body.startTime || typeof body.startTime !== 'string') {
    return { valid: false, error: 'Start time is required' }
  }
  if (!isValidISODate(body.startTime)) {
    return { valid: false, error: 'Invalid start time format' }
  }
  if (body.endTime && typeof body.endTime === 'string') {
    if (!isValidISODate(body.endTime)) {
      return { valid: false, error: 'Invalid end time format' }
    }
    if (new Date(body.endTime) < new Date(body.startTime)) {
      return { valid: false, error: 'End time must be after start time' }
    }
  }
  if (body.description && typeof body.description === 'string' && body.description.length > MAX_DESCRIPTION_LENGTH) {
    return { valid: false, error: `Description must be ${MAX_DESCRIPTION_LENGTH} characters or less` }
  }
  if (body.location && typeof body.location === 'string' && body.location.length > MAX_LOCATION_LENGTH) {
    return { valid: false, error: `Location must be ${MAX_LOCATION_LENGTH} characters or less` }
  }
  if (body.isAllDay !== undefined && typeof body.isAllDay !== 'boolean') {
    return { valid: false, error: 'isAllDay must be a boolean' }
  }
  return { valid: true }
}

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const searchParams = request.nextUrl.searchParams
    const startTime = searchParams.get('startTime')
    const endTime = searchParams.get('endTime')

    const items = await prisma.agendaItem.findMany({
      where: {
        userId: session.user.id,
        ...(startTime && endTime ? {
          startTime: {
            gte: new Date(startTime),
            lte: new Date(endTime)
          }
        } : {})
      },
      orderBy: { startTime: 'asc' }
    })

    return NextResponse.json(items)
  } catch (error) {
    console.error('Error fetching agenda items:', error)
    return NextResponse.json({
      error: 'Failed to fetch agenda items',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const body = await request.json()
    const { title, description, startTime, endTime, isAllDay, location, sourceJournalEntryId } = body

    const validation = validateAgendaInput(body)
    if (!validation.valid) {
      return NextResponse.json({ error: validation.error }, { status: 400 })
    }

    const item = await prisma.agendaItem.create({
      data: {
        userId: session.user.id,
        title,
        description,
        startTime: new Date(startTime),
        endTime: endTime ? new Date(endTime) : null,
        isAllDay: isAllDay || false,
        location,
        sourceJournalEntryId
      }
    })

    return NextResponse.json(item, { status: 201 })
  } catch (error) {
    console.error('Error creating agenda item:', error)
    return NextResponse.json({
      error: 'Failed to create agenda item',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
