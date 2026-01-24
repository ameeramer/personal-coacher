import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

/**
 * GET /api/daily-tools/refine/status/{id}
 *
 * Check the status of a daily tool refinement job.
 *
 * Response:
 * - status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
 * - error?: string (if status is FAILED)
 * - dailyTool?: DailyTool object (if status is COMPLETED)
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

    const { id: jobId } = await params

    // Find the job
    const job = await prisma.dailyToolRefineJob.findUnique({
      where: { id: jobId }
    })

    if (!job) {
      return NextResponse.json({ error: 'Job not found' }, { status: 404 })
    }

    // Verify ownership
    if (job.userId !== session.user.id) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    // Build response based on status
    const response: {
      status: string
      error?: string
      dailyTool?: object
      createdAt: Date
      updatedAt: Date
    } = {
      status: job.status,
      createdAt: job.createdAt,
      updatedAt: job.updatedAt
    }

    if (job.status === 'FAILED' && job.error) {
      response.error = job.error
    }

    if (job.status === 'COMPLETED') {
      // Fetch the refined daily tool
      const dailyTool = await prisma.dailyTool.findUnique({
        where: { id: job.dailyToolId }
      })

      if (dailyTool) {
        response.dailyTool = dailyTool
      }
    }

    return NextResponse.json(response)
  } catch (error) {
    console.error('Error checking refine job status:', error)
    return NextResponse.json({
      error: 'Failed to check job status',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
