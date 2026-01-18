import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// GET - Retrieve a single daily tool by ID
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

    const dailyTool = await prisma.dailyTool.findUnique({
      where: { id }
    })

    if (!dailyTool) {
      return NextResponse.json({ error: 'Daily tool not found' }, { status: 404 })
    }

    if (dailyTool.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    return NextResponse.json(dailyTool)
  } catch (error) {
    console.error('Error fetching daily tool:', error)
    return NextResponse.json({
      error: 'Failed to fetch daily tool',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// PUT - Update a daily tool (status, usedAt)
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

    const existingTool = await prisma.dailyTool.findUnique({
      where: { id }
    })

    if (!existingTool) {
      return NextResponse.json({ error: 'Daily tool not found' }, { status: 404 })
    }

    if (existingTool.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    const body = await request.json()
    const { status, usedAt } = body

    // Validate status if provided
    const validStatuses = ['PENDING', 'LIKED', 'DISLIKED']
    if (status && !validStatuses.includes(status)) {
      return NextResponse.json(
        { error: `status must be one of: ${validStatuses.join(', ')}` },
        { status: 400 }
      )
    }

    const updateData: { status?: string; usedAt?: Date | null } = {}

    if (status !== undefined) {
      updateData.status = status
    }

    if (usedAt !== undefined) {
      updateData.usedAt = usedAt ? new Date(usedAt) : null
    }

    const updatedTool = await prisma.dailyTool.update({
      where: { id },
      data: updateData
    })

    return NextResponse.json(updatedTool)
  } catch (error) {
    console.error('Error updating daily tool:', error)
    return NextResponse.json({
      error: 'Failed to update daily tool',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// DELETE - Delete a daily tool
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

    const existingTool = await prisma.dailyTool.findUnique({
      where: { id }
    })

    if (!existingTool) {
      return NextResponse.json({ error: 'Daily tool not found' }, { status: 404 })
    }

    if (existingTool.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    await prisma.dailyTool.delete({
      where: { id }
    })

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('Error deleting daily tool:', error)
    return NextResponse.json({
      error: 'Failed to delete daily tool',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
