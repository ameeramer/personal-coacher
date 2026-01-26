import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

export async function GET() {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const goals = await prisma.goal.findMany({
    where: {
      userId: session.user.id
    },
    orderBy: { createdAt: 'desc' },
    take: 100
  })

  return NextResponse.json(goals)
}

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { title, description, targetDate, priority } = body

  if (!title) {
    return NextResponse.json({ error: 'Title is required' }, { status: 400 })
  }

  const goal = await prisma.goal.create({
    data: {
      userId: session.user.id,
      title,
      description: description || '',
      targetDate: targetDate ? new Date(targetDate) : null,
      priority: priority || 'MEDIUM',
      status: 'ACTIVE'
    }
  })

  return NextResponse.json(goal, { status: 201 })
}
