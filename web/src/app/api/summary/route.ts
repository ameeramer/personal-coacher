import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// GET - Retrieve summaries for the current user (for sync/download)
export async function GET(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const searchParams = request.nextUrl.searchParams
  const type = searchParams.get('type')

  const summaries = await prisma.summary.findMany({
    where: {
      userId: session.user.id,
      ...(type ? { type } : {})
    },
    orderBy: { createdAt: 'desc' },
    take: 50
  })

  return NextResponse.json(summaries)
}

// POST - Create/sync a summary from the mobile app (for upload/backup)
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { id, type, content, startDate, endDate, createdAt } = body

  if (!type || !content || !startDate || !endDate) {
    return NextResponse.json(
      { error: 'type, content, startDate, and endDate are required' },
      { status: 400 }
    )
  }

  // Upsert - create if doesn't exist, update if it does
  const summary = await prisma.summary.upsert({
    where: { id: id || '' },
    update: {
      type,
      content,
      startDate: new Date(startDate),
      endDate: new Date(endDate)
    },
    create: {
      id,
      userId: session.user.id,
      type,
      content,
      startDate: new Date(startDate),
      endDate: new Date(endDate),
      createdAt: createdAt ? new Date(createdAt) : new Date()
    }
  })

  return NextResponse.json(summary, { status: 201 })
}
