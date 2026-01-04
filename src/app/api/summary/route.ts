import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { SUMMARY_SYSTEM_PROMPT, buildSummaryPrompt } from '@/lib/prompts/coach'

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

  // Get journal entries in range
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

  // Generate summary with Claude
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

  // Save summary
  const summary = await prisma.summary.create({
    data: {
      userId: session.user.id,
      type,
      content: summaryContent,
      startDate,
      endDate: now
    }
  })

  return NextResponse.json(summary, { status: 201 })
}

export async function GET(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const searchParams = request.nextUrl.searchParams
  const type = searchParams.get('type')

  const summaries = await prisma.summary.findMany({
    where: {
      userId: session.user.id,
      ...(type && { type })
    },
    orderBy: { createdAt: 'desc' },
    take: 20
  })

  return NextResponse.json(summaries)
}
