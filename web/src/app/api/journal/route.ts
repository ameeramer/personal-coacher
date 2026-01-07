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
  const startDate = searchParams.get('startDate')
  const endDate = searchParams.get('endDate')

  const entries = await prisma.journalEntry.findMany({
    where: {
      userId: session.user.id,
      ...(startDate && endDate ? {
        date: {
          gte: new Date(startDate),
          lte: new Date(endDate)
        }
      } : {})
    },
    orderBy: { date: 'desc' },
    take: 50
  })

  return NextResponse.json(entries)
}

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { content, mood, tags, date } = body

  if (!content) {
    return NextResponse.json({ error: 'Content is required' }, { status: 400 })
  }

  const entry = await prisma.journalEntry.create({
    data: {
      userId: session.user.id,
      content,
      mood,
      tags: tags || [],
      date: date ? new Date(date) : new Date()
    }
  })

  return NextResponse.json(entry, { status: 201 })
}
