import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
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
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
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

  if (!title || !startTime) {
    return NextResponse.json({ error: 'Title and startTime are required' }, { status: 400 })
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
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
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
}
