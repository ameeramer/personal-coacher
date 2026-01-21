import { NextRequest, NextResponse } from 'next/server'
import { timingSafeEqual } from 'crypto'
import webpush from 'web-push'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import {
  DAILY_TOOL_SYSTEM_PROMPT,
  buildDailyToolUserPrompt,
  parseDailyToolResponse,
  JournalEntryContext,
  PreviousToolContext
} from '@/lib/prompts/daily-tool'

// Configure web-push with VAPID keys
const vapidPublicKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY
const vapidPrivateKey = process.env.VAPID_PRIVATE_KEY
const vapidSubject = process.env.VAPID_SUBJECT || 'mailto:admin@example.com'

if (vapidPublicKey && vapidPrivateKey) {
  webpush.setVapidDetails(vapidSubject, vapidPublicKey, vapidPrivateKey)
}

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

// Get current hour in a specific timezone
function getCurrentHourInTimezone(timezone: string): { hour: number; minute: number } {
  try {
    const now = new Date()
    const formatter = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      hour: 'numeric',
      minute: 'numeric',
      hour12: false
    })
    const parts = formatter.formatToParts(now)
    const hour = parseInt(parts.find((p) => p.type === 'hour')?.value || '0', 10)
    const minute = parseInt(parts.find((p) => p.type === 'minute')?.value || '0', 10)
    return { hour, minute }
  } catch {
    // Fallback to UTC
    const now = new Date()
    return { hour: now.getUTCHours(), minute: now.getUTCMinutes() }
  }
}

// Check if we already generated a tool for this user today
async function hasToolForToday(userId: string): Promise<boolean> {
  const startOfDay = new Date()
  startOfDay.setHours(0, 0, 0, 0)

  const existingTool = await prisma.dailyTool.findFirst({
    where: {
      userId,
      date: { gte: startOfDay }
    }
  })

  return !!existingTool
}

// Generate a daily tool for a specific user
async function generateToolForUser(userId: string): Promise<{ success: boolean; toolId?: string; error?: string }> {
  try {
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
      return { success: false, error: 'No recent journal entries' }
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

    return { success: true, toolId: dailyTool.id }
  } catch (error) {
    console.error(`[Cron] Failed to generate tool for user ${userId}:`, error)
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' }
  }
}

// Send push notification to user about their new daily tool
async function sendNotification(userId: string, toolTitle: string): Promise<boolean> {
  if (!vapidPublicKey || !vapidPrivateKey) {
    console.log('[Cron] VAPID keys not configured, skipping notification')
    return false
  }

  try {
    const subscriptions = await prisma.pushSubscription.findMany({
      where: { userId }
    })

    if (subscriptions.length === 0) {
      return false
    }

    const payload = {
      title: 'Your Daily Tool is Ready!',
      body: `"${toolTitle}" - A personalized tool just for you.`,
      icon: '/icons/icon-192.svg',
      badge: '/icons/icon-192.svg',
      tag: 'daily-tool-ready',
      data: { url: '/daily-tools' }
    }

    const results = await Promise.allSettled(
      subscriptions.map(async (sub) => {
        const pushSubscription = {
          endpoint: sub.endpoint,
          keys: {
            p256dh: sub.p256dh,
            auth: sub.auth
          }
        }

        try {
          await webpush.sendNotification(pushSubscription, JSON.stringify(payload))
          return true
        } catch (error: unknown) {
          const webpushError = error as { statusCode?: number }
          if (webpushError.statusCode === 410 || webpushError.statusCode === 404) {
            // Remove invalid subscription
            await prisma.pushSubscription.delete({
              where: { id: sub.id }
            })
          }
          return false
        }
      })
    )

    return results.some((r) => r.status === 'fulfilled' && r.value === true)
  } catch (error) {
    console.error(`[Cron] Failed to send notification to user ${userId}:`, error)
    return false
  }
}

// GET /api/cron/daily-tools - Cron job to generate daily tools for all enabled users
// Called by Vercel Cron or external cron service
export async function GET(request: NextRequest) {
  // Verify cron secret for security
  const authHeader = request.headers.get('authorization')
  const cronSecret = process.env.CRON_SECRET

  if (!cronSecret) {
    console.error('[Cron] CRON_SECRET not configured')
    return NextResponse.json(
      { error: 'Server not configured for cron jobs' },
      { status: 500 }
    )
  }

  // Use timing-safe comparison to prevent timing attacks
  const expected = Buffer.from(`Bearer ${cronSecret}`)
  const provided = Buffer.from(authHeader || '')
  if (expected.length !== provided.length || !timingSafeEqual(expected, provided)) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  if (!process.env.ANTHROPIC_API_KEY) {
    return NextResponse.json(
      { error: 'Anthropic API key not configured' },
      { status: 500 }
    )
  }

  try {
    // Find users who have daily tool generation enabled
    // We check if their scheduled time matches the current hour
    const usersWithDailyTool = await prisma.user.findMany({
      where: {
        dailyToolEnabled: true,
        dailyToolHour: { not: null },
        dailyToolMinute: { not: null }
      },
      select: {
        id: true,
        timezone: true,
        dailyToolHour: true,
        dailyToolMinute: true
      }
    })

    console.log(`[Cron] Found ${usersWithDailyTool.length} users with daily tool enabled`)

    const results: {
      userId: string
      status: 'generated' | 'skipped' | 'error'
      reason?: string
      toolId?: string
    }[] = []

    for (const user of usersWithDailyTool) {
      const timezone = user.timezone || 'UTC'
      const { hour: currentHour, minute: currentMinute } = getCurrentHourInTimezone(timezone)

      // Check if it's time to generate (within a 15-minute window)
      const scheduledHour = user.dailyToolHour!
      const scheduledMinute = user.dailyToolMinute!

      const isTimeToGenerate =
        currentHour === scheduledHour &&
        currentMinute >= scheduledMinute &&
        currentMinute < scheduledMinute + 15

      if (!isTimeToGenerate) {
        results.push({
          userId: user.id,
          status: 'skipped',
          reason: `Not scheduled time (current: ${currentHour}:${currentMinute}, scheduled: ${scheduledHour}:${scheduledMinute})`
        })
        continue
      }

      // Check if we already generated a tool today
      if (await hasToolForToday(user.id)) {
        results.push({
          userId: user.id,
          status: 'skipped',
          reason: 'Already generated tool today'
        })
        continue
      }

      // Generate the tool
      console.log(`[Cron] Generating daily tool for user ${user.id}`)
      const result = await generateToolForUser(user.id)

      if (result.success && result.toolId) {
        // Send push notification
        const tool = await prisma.dailyTool.findUnique({
          where: { id: result.toolId },
          select: { title: true }
        })

        if (tool) {
          const notificationSent = await sendNotification(user.id, tool.title)

          // Update notification sent status
          await prisma.dailyTool.update({
            where: { id: result.toolId },
            data: { notificationSent }
          })
        }

        results.push({
          userId: user.id,
          status: 'generated',
          toolId: result.toolId
        })
      } else {
        results.push({
          userId: user.id,
          status: 'error',
          reason: result.error
        })
      }

      // Small delay between users to avoid rate limiting
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }

    const generated = results.filter((r) => r.status === 'generated').length
    const skipped = results.filter((r) => r.status === 'skipped').length
    const errors = results.filter((r) => r.status === 'error').length

    console.log(`[Cron] Daily tools: ${generated} generated, ${skipped} skipped, ${errors} errors`)

    return NextResponse.json({
      message: 'Daily tool generation completed',
      generated,
      skipped,
      errors,
      results
    })
  } catch (error) {
    console.error('[Cron] Daily tool generation failed:', error)
    return NextResponse.json(
      {
        error: 'Failed to run daily tool generation',
        details: error instanceof Error ? error.message : 'Unknown error'
      },
      { status: 500 }
    )
  }
}
