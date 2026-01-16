import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

export async function GET(request: NextRequest) {
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
}

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { title, description, startTime, endTime, isAllDay, location, sourceJournalEntryId } = body

  if (!title || !startTime) {
    return NextResponse.json({ error: 'Title and startTime are required' }, { status: 400 })
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
}
