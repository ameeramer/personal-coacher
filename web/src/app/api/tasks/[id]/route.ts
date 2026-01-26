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

  const task = await prisma.task.findFirst({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (!task) {
    return NextResponse.json({ error: 'Task not found' }, { status: 404 })
  }

  return NextResponse.json(task)
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
  const { title, description, dueDate, isCompleted, priority, linkedGoalId } = body

  const task = await prisma.task.updateMany({
    where: {
      id,
      userId: session.user.id
    },
    data: {
      ...(title && { title }),
      ...(description !== undefined && { description }),
      ...(dueDate !== undefined && { dueDate: dueDate ? new Date(dueDate) : null }),
      ...(isCompleted !== undefined && { isCompleted }),
      ...(priority && { priority }),
      ...(linkedGoalId !== undefined && { linkedGoalId: linkedGoalId || null })
    }
  })

  if (task.count === 0) {
    return NextResponse.json({ error: 'Task not found' }, { status: 404 })
  }

  const updated = await prisma.task.findUnique({ where: { id } })
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

  const task = await prisma.task.deleteMany({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (task.count === 0) {
    return NextResponse.json({ error: 'Task not found' }, { status: 404 })
  }

  return NextResponse.json({ success: true })
}
