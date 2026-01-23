import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { COACH_SYSTEM_PROMPT } from '@/lib/prompts/coach'

// Use Node.js runtime for Prisma compatibility
export const runtime = 'nodejs'

// Increase max duration for long-running chat (Vercel Pro plan: up to 300s)
// Free plan: 60s, Pro plan: 300s
export const maxDuration = 60

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

interface ChatRequest {
  conversationId: string
  messageId: string  // The assistant message ID to fill
  messages: ChatMessage[]
  fcmToken?: string  // Optional FCM token for push notification
  systemContext?: string  // Optional additional context (journal entries, etc.)
}

/**
 * POST /api/coach/chat
 *
 * Initiates a server-side Claude chat WITHOUT streaming to the client.
 * This is the "WhatsApp-style" approach:
 * 1. Client sends request
 * 2. Server returns job ID immediately
 * 3. Server processes Claude request in background and buffers response
 * 4. Client polls /api/coach/status/{jobId} for completion
 * 5. If client disconnects, server sends push notification when done
 *
 * This avoids Android 15+ network restriction issues with SSE streaming.
 */
export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const body: ChatRequest = await request.json()
    const { conversationId, messageId, messages, fcmToken, systemContext } = body

    if (!conversationId || !messageId || !messages?.length) {
      return NextResponse.json(
        { error: 'Missing required fields: conversationId, messageId, messages' },
        { status: 400 }
      )
    }

    // Check for existing job for this message
    const existingJob = await prisma.chatJob.findFirst({
      where: {
        messageId,
        status: { in: ['PENDING', 'STREAMING'] }
      }
    })

    if (existingJob) {
      // Return existing job info - client can poll for status
      return NextResponse.json({
        jobId: existingJob.id,
        statusUrl: `/api/coach/status/${existingJob.id}`,
        existing: true,
        status: existingJob.status
      })
    }

    // Create ChatJob record with PENDING status
    const job = await prisma.chatJob.create({
      data: {
        userId: session.user.id,
        conversationId,
        messageId,
        fcmToken,
        status: 'PENDING',
        clientConnected: true
      }
    })

    // Build system prompt with context
    const systemPrompt = systemContext
      ? `${COACH_SYSTEM_PROMPT}\n\n${systemContext}`
      : COACH_SYSTEM_PROMPT

    // Return immediately with job ID - processing happens below
    // The client will poll /api/coach/status/{jobId} for completion
    const response = NextResponse.json({
      jobId: job.id,
      statusUrl: `/api/coach/status/${job.id}`,
      status: 'PENDING'
    })

    // Start processing in the background using waitUntil
    // This allows the response to return immediately while processing continues
    const processPromise = processClaudeRequest(
      job.id,
      systemPrompt,
      messages
    )

    // Use Vercel's waitUntil to continue processing after response
    // This is a no-op in development but works in production
    if (typeof (globalThis as unknown as { waitUntil?: (p: Promise<void>) => void }).waitUntil === 'function') {
      (globalThis as unknown as { waitUntil: (p: Promise<void>) => void }).waitUntil(processPromise)
    } else {
      // In development/without waitUntil, we still need to process
      // but the response might complete before processing is done
      // That's OK - the client will poll for status
      processPromise.catch((error: Error) => {
        console.error('Background processing error:', error)
      })
    }

    return response
  } catch (error) {
    console.error('Error in chat endpoint:', error)
    return NextResponse.json({
      error: 'Failed to start chat',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

/**
 * Process the Claude API request and update the job status.
 * This runs in the background after the initial response is sent.
 */
async function processClaudeRequest(
  jobId: string,
  systemPrompt: string,
  messages: ChatMessage[]
): Promise<void> {
  try {
    // Update status to STREAMING
    await prisma.chatJob.update({
      where: { id: jobId },
      data: { status: 'STREAMING' }
    })

    // Call Claude API (non-streaming for simplicity and reliability)
    const response = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 4096,
      system: systemPrompt,
      messages: messages.map(m => ({
        role: m.role,
        content: m.content
      }))
    })

    // Extract the response text
    const fullContent = response.content
      .filter((block) => block.type === 'text')
      .map(block => 'text' in block ? block.text : '')
      .join('')

    // Update job with completed response
    await prisma.chatJob.update({
      where: { id: jobId },
      data: {
        status: 'COMPLETED',
        buffer: fullContent,
        clientConnected: false
      }
    })

    // Check if we need to send push notification
    const updatedJob = await prisma.chatJob.findUnique({
      where: { id: jobId }
    })

    if (updatedJob?.fcmToken) {
      // TODO: Send FCM push notification
      // await sendFCMNotification(updatedJob.fcmToken, 'Coach replied', fullContent.slice(0, 100))
      console.log('Would send FCM notification to:', updatedJob.fcmToken)
    }
  } catch (error) {
    console.error('Error processing Claude request:', error)

    // Update job status to failed
    await prisma.chatJob.update({
      where: { id: jobId },
      data: {
        status: 'FAILED',
        error: error instanceof Error ? error.message : 'Unknown error'
      }
    })
  }
}
