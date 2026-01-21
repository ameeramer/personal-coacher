import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { COACH_SYSTEM_PROMPT } from '@/lib/prompts/coach'

// Use Edge runtime for streaming support and no timeout limits
export const runtime = 'edge'

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
 * Initiates a server-side Claude streaming chat.
 * Creates a ChatJob record and starts streaming in the background.
 * Returns immediately with a job ID that can be polled for status.
 *
 * The streaming continues on the server even if the client disconnects.
 * When complete, a push notification is sent if the client is disconnected.
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
      // Return existing job info
      return NextResponse.json({
        jobId: existingJob.id,
        statusUrl: `/api/coach/status/${existingJob.id}`,
        existing: true,
        buffer: existingJob.buffer
      })
    }

    // Create ChatJob record
    const job = await prisma.chatJob.create({
      data: {
        userId: session.user.id,
        conversationId,
        messageId,
        fcmToken,
        status: 'STREAMING',
        clientConnected: true
      }
    })

    // Build system prompt with context
    const systemPrompt = systemContext
      ? `${COACH_SYSTEM_PROMPT}\n\n${systemContext}`
      : COACH_SYSTEM_PROMPT

    // Start streaming in the background using a streaming response
    // This approach streams to the client while also buffering on the server
    const encoder = new TextEncoder()

    const stream = new ReadableStream({
      async start(controller) {
        try {
          // Call Claude API with streaming
          const response = await anthropic.messages.stream({
            model: CLAUDE_MODEL,
            max_tokens: 4096,
            system: systemPrompt,
            messages: messages.map(m => ({
              role: m.role,
              content: m.content
            }))
          })

          let fullBuffer = ''

          // Process the stream
          for await (const event of response) {
            if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
              const text = event.delta.text
              fullBuffer += text

              // Send SSE event to client
              const sseData = JSON.stringify({
                type: 'delta',
                text,
                jobId: job.id
              })
              controller.enqueue(encoder.encode(`data: ${sseData}\n\n`))

              // Periodically update the buffer in DB (every 500 chars to reduce DB writes)
              if (fullBuffer.length % 500 < text.length) {
                await prisma.chatJob.update({
                  where: { id: job.id },
                  data: { buffer: fullBuffer }
                })
              }
            }
          }

          // Streaming complete - update job status
          await prisma.chatJob.update({
            where: { id: job.id },
            data: {
              status: 'COMPLETED',
              buffer: fullBuffer,
              clientConnected: false
            }
          })

          // Update the Message record with the full response
          await prisma.message.update({
            where: { id: messageId },
            data: {
              content: fullBuffer,
              status: 'completed',
              updatedAt: new Date()
            }
          })

          // Update conversation timestamp
          await prisma.conversation.update({
            where: { id: conversationId },
            data: { updatedAt: new Date() }
          })

          // Send completion event
          const completeData = JSON.stringify({
            type: 'complete',
            content: fullBuffer,
            jobId: job.id
          })
          controller.enqueue(encoder.encode(`data: ${completeData}\n\n`))

          // Check if we need to send push notification
          // This happens if the client disconnected mid-stream
          const updatedJob = await prisma.chatJob.findUnique({
            where: { id: job.id }
          })

          if (updatedJob && !updatedJob.clientConnected && updatedJob.fcmToken) {
            // TODO: Send FCM push notification
            // await sendFCMNotification(updatedJob.fcmToken, 'Coach replied', fullBuffer.slice(0, 100))
            console.log('Would send FCM notification to:', updatedJob.fcmToken)
          }

          controller.close()
        } catch (error) {
          console.error('Streaming error:', error)

          // Update job status to failed
          await prisma.chatJob.update({
            where: { id: job.id },
            data: {
              status: 'FAILED',
              error: error instanceof Error ? error.message : 'Unknown error'
            }
          })

          // Send error event
          const errorData = JSON.stringify({
            type: 'error',
            error: error instanceof Error ? error.message : 'Streaming failed',
            jobId: job.id
          })
          controller.enqueue(encoder.encode(`data: ${errorData}\n\n`))
          controller.close()
        }
      }
    })

    // Return SSE streaming response
    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache, no-transform',
        'Connection': 'keep-alive',
        'X-Job-Id': job.id
      }
    })
  } catch (error) {
    console.error('Error in chat endpoint:', error)
    return NextResponse.json({
      error: 'Failed to start chat',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
