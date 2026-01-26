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

  const goal = await prisma.goal.findFirst({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (!goal) {
    return NextResponse.json({ error: 'Goal not found' }, { status: 404 })
  }

  return NextResponse.json(goal)
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
  const body = await request.json()
  const { title, description, targetDate, status, priority } = body

  const goal = await prisma.goal.updateMany({
    where: {
      id,
      userId: session.user.id
    },
    data: {
      ...(title && { title }),
      ...(description !== undefined && { description }),
      ...(targetDate !== undefined && { targetDate: targetDate ? new Date(targetDate) : null }),
      ...(status && { status }),
      ...(priority && { priority })
    }
  })

  if (goal.count === 0) {
    return NextResponse.json({ error: 'Goal not found' }, { status: 404 })
  }

  const updated = await prisma.goal.findUnique({ where: { id } })
  return NextResponse.json(updated)
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

  const goal = await prisma.goal.deleteMany({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (goal.count === 0) {
    return NextResponse.json({ error: 'Goal not found' }, { status: 404 })
  }

  return NextResponse.json({ success: true })
}
