import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

interface RouteParams {
  params: Promise<{ id: string }>
}

// GET /api/recorder/sessions/[id] - Get a specific session with transcriptions
export async function GET(request: NextRequest, { params }: RouteParams) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { id } = await params

  try {
    const recordingSession = await prisma.recordingSession.findFirst({
      where: {
        id,
        userId: session.user.id
      },
      include: {
        transcriptions: {
          orderBy: { chunkIndex: 'asc' }
        }
      }
    })

    if (!recordingSession) {
      return NextResponse.json({ error: 'Session not found' }, { status: 404 })
    }

    return NextResponse.json(recordingSession)
  } catch (error) {
    console.error('Failed to fetch recording session:', error)
    return NextResponse.json({ error: 'Failed to fetch session' }, { status: 500 })
  }
}

// PATCH /api/recorder/sessions/[id] - Update session status or title
export async function PATCH(request: NextRequest, { params }: RouteParams) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { id } = await params

  let body
  try {
    body = await request.json()
  } catch {
    return NextResponse.json({ error: 'Invalid JSON' }, { status: 400 })
  }

  const { status, title } = body

  try {
    // Verify ownership
    const existingSession = await prisma.recordingSession.findFirst({
      where: {
        id,
        userId: session.user.id
      }
    })

    if (!existingSession) {
      return NextResponse.json({ error: 'Session not found' }, { status: 404 })
    }

    const updateData: { status?: string; title?: string; endedAt?: Date } = {}

    if (status) {
      updateData.status = status
      if (status === 'completed') {
        updateData.endedAt = new Date()
      }
    }

    if (title !== undefined) {
      updateData.title = title
    }

    const updatedSession = await prisma.recordingSession.update({
      where: { id },
      data: updateData,
      include: {
        transcriptions: {
          orderBy: { chunkIndex: 'asc' }
        }
      }
    })

    return NextResponse.json(updatedSession)
  } catch (error) {
    console.error('Failed to update recording session:', error)
    return NextResponse.json({ error: 'Failed to update session' }, { status: 500 })
  }
}

// DELETE /api/recorder/sessions/[id] - Delete a session and its transcriptions
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { id } = await params

  try {
    // Verify ownership
    const existingSession = await prisma.recordingSession.findFirst({
      where: {
        id,
        userId: session.user.id
      }
    })

    if (!existingSession) {
      return NextResponse.json({ error: 'Session not found' }, { status: 404 })
    }

    // Delete session (cascades to transcriptions due to onDelete: Cascade)
    await prisma.recordingSession.delete({
      where: { id }
    })

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('Failed to delete recording session:', error)
    return NextResponse.json({ error: 'Failed to delete session' }, { status: 500 })
  }
}
