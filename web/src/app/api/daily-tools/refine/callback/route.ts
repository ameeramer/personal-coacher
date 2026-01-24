import { NextRequest, NextResponse } from 'next/server'
import { prisma } from '@/lib/prisma'
import { anthropic } from '@/lib/anthropic'
import {
  DAILY_TOOL_REFINE_SYSTEM_PROMPT,
  buildRefineUserPrompt,
  parseGeneratedToolResponse
} from '@/lib/prompts/daily-tool'

// QStash signature verification is loaded dynamically to avoid build-time errors
// when QSTASH_CURRENT_SIGNING_KEY is not set
let verifySignatureAppRouter: typeof import('@upstash/qstash/nextjs').verifySignatureAppRouter | null = null

// Check if QStash signing keys are configured
const hasQStashSigningKeys = !!(
  process.env.QSTASH_CURRENT_SIGNING_KEY ||
  process.env.QSTASH_NEXT_SIGNING_KEY
)

/**
 * POST /api/daily-tools/refine/callback
 *
 * QStash callback endpoint for refining daily tools.
 * This endpoint is called by QStash after a refinement job is queued.
 *
 * The request body contains:
 * - jobId: The DailyToolRefineJob ID
 * - userId: The user ID
 * - dailyToolId: The DailyTool ID being refined
 * - feedback: User's feedback describing desired changes
 * - currentTitle: Current tool title
 * - currentDescription: Current tool description
 * - currentHtmlCode: Current tool HTML code
 * - currentJournalContext: Current journal context (optional)
 */
async function handler(request: NextRequest) {
  try {
    const body = await request.json()
    const {
      jobId,
      userId,
      dailyToolId,
      feedback,
      currentTitle,
      currentDescription,
      currentHtmlCode,
      currentJournalContext
    } = body

    if (!jobId || !userId || !dailyToolId || !feedback || !currentHtmlCode) {
      console.error('Missing required fields in QStash callback')
      return NextResponse.json({ error: 'Missing required fields' }, { status: 400 })
    }

    console.log(`Processing daily tool refinement job ${jobId} for tool ${dailyToolId}`)

    // Verify job exists and is in correct state
    const job = await prisma.dailyToolRefineJob.findUnique({
      where: { id: jobId }
    })

    if (!job) {
      console.error(`Job ${jobId} not found`)
      return NextResponse.json({ error: 'Job not found' }, { status: 404 })
    }

    if (job.status === 'COMPLETED') {
      console.log(`Job ${jobId} already completed`)
      return NextResponse.json({ status: 'already_completed', dailyToolId })
    }

    if (job.status === 'FAILED') {
      console.log(`Job ${jobId} already failed`)
      return NextResponse.json({ status: 'already_failed', error: job.error })
    }

    // Update job status to PROCESSING
    await prisma.dailyToolRefineJob.update({
      where: { id: jobId },
      data: { status: 'PROCESSING' }
    })

    try {
      // Build prompts for refinement
      const currentTool = {
        title: currentTitle || 'Daily Tool',
        description: currentDescription || 'A personalized tool',
        journalContext: currentJournalContext || null,
        htmlCode: currentHtmlCode
      }
      const userPrompt = buildRefineUserPrompt(currentTool, feedback)

      console.log(`Calling Claude API for refinement job ${jobId}`)

      // Call Claude API
      const response = await anthropic.messages.create({
        model: 'claude-sonnet-4-20250514',
        max_tokens: 8192,
        system: DAILY_TOOL_REFINE_SYSTEM_PROMPT,
        messages: [{ role: 'user', content: userPrompt }]
      })

      // Extract text content
      const textContent = response.content.find(c => c.type === 'text')
      if (!textContent || textContent.type !== 'text') {
        throw new Error('No text content in Claude response')
      }

      // Parse the refined tool
      const refinedTool = parseGeneratedToolResponse(textContent.text)

      console.log(`Refined tool "${refinedTool.title}" for job ${jobId}`)

      // Update the daily tool in the database
      await prisma.dailyTool.update({
        where: { id: dailyToolId },
        data: {
          title: refinedTool.title,
          description: refinedTool.description,
          htmlCode: refinedTool.htmlCode,
          journalContext: refinedTool.journalContext,
          updatedAt: new Date()
        }
      })

      // Update job as completed
      await prisma.dailyToolRefineJob.update({
        where: { id: jobId },
        data: {
          status: 'COMPLETED'
        }
      })

      console.log(`Job ${jobId} completed successfully, refined tool ${dailyToolId}`)

      return NextResponse.json({
        status: 'completed',
        dailyToolId
      })
    } catch (refinementError) {
      console.error(`Refinement failed for job ${jobId}:`, refinementError)

      // Update job as failed
      const errorMessage = refinementError instanceof Error
        ? refinementError.message
        : 'Unknown refinement error'

      await prisma.dailyToolRefineJob.update({
        where: { id: jobId },
        data: {
          status: 'FAILED',
          error: errorMessage
        }
      })

      // Return 500 so QStash will retry
      return NextResponse.json({
        error: 'Refinement failed',
        details: errorMessage
      }, { status: 500 })
    }
  } catch (error) {
    console.error('Error in daily tool refine callback:', error)
    return NextResponse.json({
      error: 'Failed to process refinement callback',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// Export handler with conditional QStash signature verification
// In production with QStash configured, signature verification is required
// In development or when QStash is not configured, the handler runs without verification
export async function POST(request: NextRequest) {
  // If QStash signing keys are configured, verify the signature
  if (hasQStashSigningKeys) {
    // Dynamically import to avoid build-time errors
    if (!verifySignatureAppRouter) {
      const qstashModule = await import('@upstash/qstash/nextjs')
      verifySignatureAppRouter = qstashModule.verifySignatureAppRouter
    }
    // Create wrapped handler and call it
    const wrappedHandler = verifySignatureAppRouter(handler)
    return wrappedHandler(request)
  }

  // No QStash signing keys - run handler directly (development mode)
  console.warn('QStash signature verification disabled - QSTASH_CURRENT_SIGNING_KEY not set')
  return handler(request)
}
