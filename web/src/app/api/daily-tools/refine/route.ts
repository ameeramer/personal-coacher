import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { qstashClient, getQStashCallbackUrl } from '@/lib/qstash'

/**
 * POST /api/daily-tools/refine
 *
 * Initiates an async daily tool refinement via QStash.
 * Returns immediately with a job ID that can be polled for status.
 *
 * Request body:
 * - appId: The ID of the DailyTool to refine
 * - feedback: User's description of desired changes
 * - currentTitle: Current tool title
 * - currentDescription: Current tool description
 * - currentHtmlCode: Current tool HTML code
 * - currentJournalContext: Current journal context (optional)
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

    // Parse request body
    const body = await request.json()
    const { appId, feedback, currentTitle, currentDescription, currentHtmlCode, currentJournalContext } = body

    if (!appId || !feedback) {
      return NextResponse.json(
        { error: 'Missing required fields: appId and feedback are required' },
        { status: 400 }
      )
    }

    if (!currentHtmlCode) {
      return NextResponse.json(
        { error: 'Missing currentHtmlCode - required for refinement' },
        { status: 400 }
      )
    }

    // Check for existing pending refinement job for this tool
    const existingJob = await prisma.dailyToolRefineJob.findFirst({
      where: {
        dailyToolId: appId,
        status: { in: ['PENDING', 'PROCESSING'] }
      },
      orderBy: { createdAt: 'desc' }
    })

    if (existingJob) {
      // Return existing job info instead of creating a new one
      return NextResponse.json({
        jobId: existingJob.id,
        statusUrl: `/api/daily-tools/refine/status/${existingJob.id}`,
        existing: true
      })
    }

    // Create job record
    const job = await prisma.dailyToolRefineJob.create({
      data: {
        userId: session.user.id,
        dailyToolId: appId,
        feedback: feedback,
        status: 'PENDING'
      }
    })

    // Prepare callback payload
    const callbackPayload = {
      jobId: job.id,
      userId: session.user.id,
      dailyToolId: appId,
      feedback,
      currentTitle,
      currentDescription,
      currentHtmlCode,
      currentJournalContext
    }

    // Get callback URL
    const baseUrl = getQStashCallbackUrl()
    const callbackUrl = `${baseUrl}/api/daily-tools/refine/callback`

    // Publish to QStash
    const qstashResponse = await qstashClient.publishJSON({
      url: callbackUrl,
      body: callbackPayload,
      retries: 3, // QStash will retry up to 3 times on failure
    })

    // Update job with QStash message ID
    await prisma.dailyToolRefineJob.update({
      where: { id: job.id },
      data: {
        qstashMessageId: qstashResponse.messageId,
        status: 'PROCESSING'
      }
    })

    return NextResponse.json({
      jobId: job.id,
      statusUrl: `/api/daily-tools/refine/status/${job.id}`,
      qstashMessageId: qstashResponse.messageId
    })
  } catch (error) {
    console.error('Error creating daily tool refine job:', error)
    return NextResponse.json({
      error: 'Failed to create daily tool refine job',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
