import { NextRequest, NextResponse } from 'next/server'
import { verifySignatureAppRouter } from '@upstash/qstash/nextjs'
import { prisma } from '@/lib/prisma'
import { anthropic } from '@/lib/anthropic'
import {
  DAILY_TOOL_SYSTEM_PROMPT,
  buildDailyToolUserPrompt,
  parseGeneratedToolResponse
} from '@/lib/prompts/daily-tool'

/**
 * POST /api/daily-tools/generate
 *
 * QStash callback endpoint for generating daily tools.
 * This endpoint is called by QStash after a job is queued.
 *
 * The request body contains:
 * - jobId: The DailyToolJob ID
 * - userId: The user ID
 * - recentEntries: Optional array of journal entries (if provided by client)
 * - previousToolIds: Optional array of previous tool IDs to avoid duplicates
 */
async function handler(request: NextRequest) {
  try {
    const body = await request.json()
    const { jobId, userId, recentEntries: providedEntries, previousToolIds } = body

    if (!jobId || !userId) {
      console.error('Missing jobId or userId in QStash callback')
      return NextResponse.json({ error: 'Missing jobId or userId' }, { status: 400 })
    }

    console.log(`Processing daily tool generation job ${jobId} for user ${userId}`)

    // Verify job exists and is in correct state
    const job = await prisma.dailyToolJob.findUnique({
      where: { id: jobId }
    })

    if (!job) {
      console.error(`Job ${jobId} not found`)
      return NextResponse.json({ error: 'Job not found' }, { status: 404 })
    }

    if (job.status === 'COMPLETED') {
      console.log(`Job ${jobId} already completed`)
      return NextResponse.json({ status: 'already_completed', dailyToolId: job.dailyToolId })
    }

    if (job.status === 'FAILED') {
      console.log(`Job ${jobId} already failed`)
      return NextResponse.json({ status: 'already_failed', error: job.error })
    }

    // Update job status to PROCESSING
    await prisma.dailyToolJob.update({
      where: { id: jobId },
      data: { status: 'PROCESSING' }
    })

    try {
      // Fetch recent journal entries if not provided
      let recentEntries = providedEntries
      if (!recentEntries) {
        const entries = await prisma.journalEntry.findMany({
          where: { userId },
          orderBy: { date: 'desc' },
          take: 7 // Last 7 entries
        })

        recentEntries = entries.map(e => ({
          content: e.content,
          mood: e.mood,
          tags: e.tags,
          date: e.date
        }))
      }

      // Fetch previous tools to avoid duplicates
      let previousTools: { title: string; description: string; date: Date }[] = []
      if (previousToolIds && previousToolIds.length > 0) {
        const tools = await prisma.dailyTool.findMany({
          where: { id: { in: previousToolIds } },
          select: { title: true, description: true, date: true }
        })
        previousTools = tools
      } else {
        // Fetch last 14 tools for this user
        const tools = await prisma.dailyTool.findMany({
          where: { userId },
          orderBy: { date: 'desc' },
          take: 14,
          select: { title: true, description: true, date: true }
        })
        previousTools = tools
      }

      // Build prompts
      const userPrompt = buildDailyToolUserPrompt(recentEntries, previousTools)

      console.log(`Calling Claude API for job ${jobId}`)

      // Call Claude API
      const response = await anthropic.messages.create({
        model: 'claude-sonnet-4-20250514',
        max_tokens: 8192,
        system: DAILY_TOOL_SYSTEM_PROMPT,
        messages: [{ role: 'user', content: userPrompt }]
      })

      // Extract text content
      const textContent = response.content.find(c => c.type === 'text')
      if (!textContent || textContent.type !== 'text') {
        throw new Error('No text content in Claude response')
      }

      // Parse the generated tool
      const generatedTool = parseGeneratedToolResponse(textContent.text)

      console.log(`Generated tool "${generatedTool.title}" for job ${jobId}`)

      // Create the daily tool in the database
      const dailyTool = await prisma.dailyTool.create({
        data: {
          userId,
          date: new Date(),
          title: generatedTool.title,
          description: generatedTool.description,
          htmlCode: generatedTool.htmlCode,
          journalContext: generatedTool.journalContext,
          status: 'PENDING'
        }
      })

      // Update job as completed
      await prisma.dailyToolJob.update({
        where: { id: jobId },
        data: {
          status: 'COMPLETED',
          dailyToolId: dailyTool.id
        }
      })

      console.log(`Job ${jobId} completed successfully, created tool ${dailyTool.id}`)

      return NextResponse.json({
        status: 'completed',
        dailyToolId: dailyTool.id
      })
    } catch (generationError) {
      console.error(`Generation failed for job ${jobId}:`, generationError)

      // Update job as failed
      const errorMessage = generationError instanceof Error
        ? generationError.message
        : 'Unknown generation error'

      await prisma.dailyToolJob.update({
        where: { id: jobId },
        data: {
          status: 'FAILED',
          error: errorMessage
        }
      })

      // Return 500 so QStash will retry
      return NextResponse.json({
        error: 'Generation failed',
        details: errorMessage
      }, { status: 500 })
    }
  } catch (error) {
    console.error('Error in daily tool generate callback:', error)
    return NextResponse.json({
      error: 'Failed to process generation callback',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// Wrap handler with QStash signature verification
// This ensures only QStash can call this endpoint
export const POST = verifySignatureAppRouter(handler)
