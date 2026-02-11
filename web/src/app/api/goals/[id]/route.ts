import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

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
  } catch (error) {
    console.error('GET /api/goals/[id] error:', error)
    const message = error instanceof Error ? error.message : 'Unknown error'
    return NextResponse.json({ error: message }, { status: 500 })
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
    const body = await request.json()
    const { title, description, targetDate, status, priority } = body

    console.log('PUT /api/goals/[id] - Received body:', JSON.stringify(body, null, 2))

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
    console.log('PUT /api/goals/[id] - Updated goal:', id)
    return NextResponse.json(updated)
  } catch (error) {
    console.error('PUT /api/goals/[id] error:', error)
    const message = error instanceof Error ? error.message : 'Unknown error'
    return NextResponse.json({ error: message }, { status: 500 })
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

    const goal = await prisma.goal.deleteMany({
      where: {
        id,
        userId: session.user.id
      }
    })

    if (goal.count === 0) {
      return NextResponse.json({ error: 'Goal not found' }, { status: 404 })
    }

    console.log('DELETE /api/goals/[id] - Deleted goal:', id)
    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('DELETE /api/goals/[id] error:', error)
    const message = error instanceof Error ? error.message : 'Unknown error'
    return NextResponse.json({ error: message }, { status: 500 })
  }
}
