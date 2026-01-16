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

    const item = await prisma.agendaItem.findUnique({
      where: { id }
    })

    if (!item) {
      return NextResponse.json({ error: 'Item not found' }, { status: 404 })
    }

    if (item.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    return NextResponse.json(item)
  } catch (error) {
    console.error('Error fetching agenda item:', error)
    return NextResponse.json({
      error: 'Failed to fetch agenda item',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

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

    const existingItem = await prisma.agendaItem.findUnique({
      where: { id }
    })

    if (!existingItem) {
      return NextResponse.json({ error: 'Item not found' }, { status: 404 })
    }

    if (existingItem.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    const body = await request.json()
    const { title, description, startTime, endTime, isAllDay, location } = body

    const validation = validateAgendaInput(body)
    if (!validation.valid) {
      return NextResponse.json({ error: validation.error }, { status: 400 })
    }

    const updatedItem = await prisma.agendaItem.update({
      where: { id },
      data: {
        title,
        description,
        startTime: new Date(startTime),
        endTime: endTime ? new Date(endTime) : null,
        isAllDay: isAllDay || false,
        location
      }
    })

    return NextResponse.json(updatedItem)
  } catch (error) {
    console.error('Error updating agenda item:', error)
    return NextResponse.json({
      error: 'Failed to update agenda item',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

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

    const existingItem = await prisma.agendaItem.findUnique({
      where: { id }
    })

    if (!existingItem) {
      return NextResponse.json({ error: 'Item not found' }, { status: 404 })
    }

    if (existingItem.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    await prisma.agendaItem.delete({
      where: { id }
    })

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('Error deleting agenda item:', error)
    return NextResponse.json({
      error: 'Failed to delete agenda item',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
