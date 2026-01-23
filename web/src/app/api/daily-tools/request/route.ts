import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { qstashClient, getQStashCallbackUrl } from '@/lib/qstash'

/**
 * POST /api/daily-tools/request
 *
 * Initiates an async daily tool generation via QStash.
 * Returns immediately with a job ID that can be polled for status.
 *
 * Request body (optional):
 * - recentEntries: Array of journal entries (if not provided, fetched from DB)
 * - previousToolIds: Array of previous tool IDs to avoid duplicates
 *
 * Response:
 * - jobId: The job ID to poll for status
 * - statusUrl: URL to check job status
 */
export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    // Check if QStash is configured
    if (!qstashClient) {
      return NextResponse.json(
        { error: 'QStash not configured. Set QSTASH_TOKEN environment variable.' },
        { status: 503 }
      )
    }

    // Check for existing pending job for this user
    const existingJob = await prisma.dailyToolJob.findFirst({
      where: {
        userId: session.user.id,
        status: { in: ['PENDING', 'PROCESSING'] }
      },
      orderBy: { createdAt: 'desc' }
    })

    if (existingJob) {
      // Return existing job info instead of creating a new one
      return NextResponse.json({
        jobId: existingJob.id,
        statusUrl: `/api/daily-tools/status/${existingJob.id}`,
        existing: true
      })
    }

    // Parse request body for optional data
    let recentEntries = null
    let previousToolIds: string[] = []

    try {
      const body = await request.json()
      recentEntries = body.recentEntries
      previousToolIds = body.previousToolIds || []
    } catch {
      // No body provided, will fetch data in callback
    }

    // Create job record
    const job = await prisma.dailyToolJob.create({
      data: {
        userId: session.user.id,
        status: 'PENDING'
      }
    })

    // Prepare callback payload
    const callbackPayload = {
      jobId: job.id,
      userId: session.user.id,
      recentEntries,
      previousToolIds
    }

    // Get callback URL
    const baseUrl = getQStashCallbackUrl()
    const callbackUrl = `${baseUrl}/api/daily-tools/generate`

    // Publish to QStash
    const qstashResponse = await qstashClient.publishJSON({
      url: callbackUrl,
      body: callbackPayload,
      retries: 3, // QStash will retry up to 3 times on failure (free!)
      // 15 minutes max for free tier
    })

    // Update job with QStash message ID
    await prisma.dailyToolJob.update({
      where: { id: job.id },
      data: {
        qstashMessageId: qstashResponse.messageId,
        status: 'PROCESSING'
      }
    })

    return NextResponse.json({
      jobId: job.id,
      statusUrl: `/api/daily-tools/status/${job.id}`,
      qstashMessageId: qstashResponse.messageId
    })
  } catch (error) {
    console.error('Error creating daily tool job:', error)
    return NextResponse.json({
      error: 'Failed to create daily tool job',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
