import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

/**
 * GET /api/coach/status/[id]
 *
 * Gets the current status of a chat job.
 * Used by clients to poll for completion when they reconnect.
 *
 * Response:
 * - status: 'PENDING' | 'STREAMING' | 'COMPLETED' | 'FAILED'
 * - buffer: Current accumulated response text
 * - error: Error message if failed
 */
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

    const job = await prisma.chatJob.findUnique({
      where: { id }
    })

    if (!job) {
      return NextResponse.json({ error: 'Job not found' }, { status: 404 })
    }

    // Verify user owns this job
    if (job.userId !== session.user.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })
    }

    return NextResponse.json({
      id: job.id,
      status: job.status,
      buffer: job.buffer,
      error: job.error,
      conversationId: job.conversationId,
      messageId: job.messageId,
      createdAt: job.createdAt,
      updatedAt: job.updatedAt
    })
  } catch (error) {
    console.error('Error getting chat job status:', error)
    return NextResponse.json({
      error: 'Failed to get job status',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

/**
 * PATCH /api/coach/status/[id]
 *
 * Updates the chat job (e.g., mark client as disconnected)
 *
 * Request body:
 * - clientConnected: boolean - Set to false when client disconnects
 */
export async function PATCH(
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

    const job = await prisma.chatJob.findUnique({
      where: { id }
    })

    if (!job) {
      return NextResponse.json({ error: 'Job not found' }, { status: 404 })
    }

    // Verify user owns this job
    if (job.userId !== session.user.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })
    }

    // Update allowed fields
    const updateData: { clientConnected?: boolean; fcmToken?: string } = {}

    if (typeof body.clientConnected === 'boolean') {
      updateData.clientConnected = body.clientConnected
    }

    if (typeof body.fcmToken === 'string') {
      updateData.fcmToken = body.fcmToken
    }

    const updatedJob = await prisma.chatJob.update({
      where: { id },
      data: updateData
    })

    return NextResponse.json({
      id: updatedJob.id,
      status: updatedJob.status,
      buffer: updatedJob.buffer,
      clientConnected: updatedJob.clientConnected
    })
  } catch (error) {
    console.error('Error updating chat job:', error)
    return NextResponse.json({
      error: 'Failed to update job',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
