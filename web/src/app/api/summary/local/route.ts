import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { SUMMARY_SYSTEM_PROMPT, buildSummaryPrompt } from '@/lib/prompts/coach'

// POST /api/summary/local - Generate summary locally (AI only, no DB persistence)
// This endpoint is for mobile apps that want to store summaries locally
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { type } = body // 'daily', 'weekly', 'monthly'

  if (!type || !['daily', 'weekly', 'monthly'].includes(type)) {
    return NextResponse.json({ error: 'Invalid summary type' }, { status: 400 })
  }

  // Calculate date range
  const now = new Date()
  let startDate: Date

  switch (type) {
    case 'daily':
      startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      break
    case 'weekly':
      const dayOfWeek = now.getDay()
      startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dayOfWeek)
      break
    case 'monthly':
      startDate = new Date(now.getFullYear(), now.getMonth(), 1)
      break
    default:
      startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  }

  // Get journal entries in range (read from server DB for context)
  const entries = await prisma.journalEntry.findMany({
    where: {
      userId: session.user.id,
      date: {
        gte: startDate,
        lte: now
      }
    },
    orderBy: { date: 'asc' }
  })

  if (entries.length === 0) {
    return NextResponse.json({
      error: 'No journal entries found for this period'
    }, { status: 404 })
  }

  const entryContents = entries.map(e =>
    `[${e.date.toLocaleDateString()}]${e.mood ? ` (Mood: ${e.mood})` : ''}\n${e.content}`
  )

  // Generate summary with Claude (no DB persistence)
  const response = await anthropic.messages.create({
    model: CLAUDE_MODEL,
    max_tokens: 2048,
    system: SUMMARY_SYSTEM_PROMPT,
    messages: [{
      role: 'user',
      content: buildSummaryPrompt(entryContents, type)
    }]
  })

  const summaryContent = response.content[0].type === 'text'
    ? response.content[0].text
    : ''

  // Return the summary without saving to DB
  // Client is responsible for local storage
  return NextResponse.json({
    type,
    content: summaryContent,
    startDate: startDate.toISOString(),
    endDate: now.toISOString(),
    createdAt: now.toISOString()
  })
}
