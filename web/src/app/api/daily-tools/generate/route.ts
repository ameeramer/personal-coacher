import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import {
  DAILY_TOOL_SYSTEM_PROMPT,
  buildDailyToolUserPrompt,
  parseDailyToolResponse,
  JournalEntryContext,
  PreviousToolContext
} from '@/lib/prompts/daily-tool'

// Strip HTML tags from content
function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '').trim()
}

// Format date for display
function formatDate(date: Date): string {
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// POST /api/daily-tools/generate - Generate a daily tool for the authenticated user
export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const userId = session.user.id

    // Check if API key is configured
    if (!process.env.ANTHROPIC_API_KEY) {
      return NextResponse.json(
        { error: 'Anthropic API key not configured on server' },
        { status: 500 }
      )
    }

    // Get recent journal entries (last 7 days)
    const sevenDaysAgo = new Date()
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7)

    const recentEntries = await prisma.journalEntry.findMany({
      where: {
        userId,
        date: { gte: sevenDaysAgo }
      },
      orderBy: { date: 'desc' },
      take: 7
    })

    if (recentEntries.length === 0) {
      return NextResponse.json(
        { error: 'No recent journal entries found. Write some journal entries first!' },
        { status: 400 }
      )
    }

    // Get previous tools to avoid duplicates (last 14 tools)
    const previousTools = await prisma.dailyTool.findMany({
      where: { userId },
      orderBy: { date: 'desc' },
      take: 14,
      select: {
        title: true,
        description: true,
        date: true
      }
    })

    // Format entries for the prompt
    const entriesContext: JournalEntryContext[] = recentEntries.map((entry) => ({
      date: formatDate(entry.date),
      mood: entry.mood || undefined,
      tags: entry.tags,
      content: stripHtml(entry.content).substring(0, 500)
    }))

    // Format previous tools for the prompt
    const previousToolsContext: PreviousToolContext[] = previousTools.map((tool) => ({
      title: tool.title,
      date: formatDate(tool.date),
      description: tool.description
    }))

    // Build the user prompt
    const userPrompt = buildDailyToolUserPrompt(entriesContext, previousToolsContext)

    console.log(`[Daily Tool] Generating for user ${userId} with ${recentEntries.length} entries`)

    // Call Claude API
    const message = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 8192,
      system: DAILY_TOOL_SYSTEM_PROMPT,
      messages: [{ role: 'user', content: userPrompt }]
    })

    // Extract text content
    const textContent = message.content.find((c) => c.type === 'text')
    if (!textContent || textContent.type !== 'text') {
      throw new Error('No text content in Claude response')
    }

    // Parse the generated tool
    const generatedTool = parseDailyToolResponse(textContent.text)

    // Save to database
    const dailyTool = await prisma.dailyTool.create({
      data: {
        userId,
        date: new Date(),
        title: generatedTool.title,
        description: generatedTool.description,
        htmlCode: generatedTool.htmlCode,
        journalContext: generatedTool.journalContext,
        status: 'PENDING',
        generatedBy: 'server',
        notificationSent: false
      }
    })

    console.log(`[Daily Tool] Generated "${dailyTool.title}" for user ${userId}`)

    return NextResponse.json(dailyTool, { status: 201 })
  } catch (error) {
    console.error('[Daily Tool] Generation error:', error)

    // Handle specific error types
    if (error instanceof SyntaxError) {
      return NextResponse.json(
        { error: 'Failed to parse AI response. Please try again.' },
        { status: 500 }
      )
    }

    return NextResponse.json(
      {
        error: 'Failed to generate daily tool',
        details: error instanceof Error ? error.message : 'Unknown error'
      },
      { status: 500 }
    )
  }
}
