import { NextRequest } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { anthropic, CLAUDE_MODEL } from '@/lib/anthropic'
import { buildCoachContext } from '@/lib/prompts/coach'

export const runtime = 'nodejs'

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    })
  }

  let body
  try {
    body = await request.json()
  } catch {
    return new Response(JSON.stringify({ error: 'Invalid JSON' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    })
  }

  const { message, conversationId, initialAssistantMessage } = body

  if (!message) {
    return new Response(JSON.stringify({ error: 'Message is required' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    })
  }

  try {
    // Get or create conversation and save user message
    const result = await prisma.$transaction(async (tx) => {
      let conversation
      if (conversationId) {
        conversation = await tx.conversation.findFirst({
          where: { id: conversationId, userId: session.user.id },
          include: { messages: { orderBy: { createdAt: 'asc' } } }
        })
        if (!conversation) {
          throw new Error('Conversation not found')
        }
      } else {
        conversation = await tx.conversation.create({
          data: {
            userId: session.user.id,
            title: message.slice(0, 50) + (message.length > 50 ? '...' : '')
          },
          include: { messages: true }
        })
      }

      // If this is a new conversation with an initial assistant message (from notification),
      // save it to the database
      if (initialAssistantMessage && conversation.messages.length === 0) {
        await tx.message.create({
          data: {
            conversationId: conversation.id,
            role: 'assistant',
            content: initialAssistantMessage,
            status: 'completed',
            notificationSent: true
          }
        })
      }

      // Save user message
      const userMessage = await tx.message.create({
        data: {
          conversationId: conversation.id,
          role: 'user',
          content: message,
          status: 'completed',
          notificationSent: true
        }
      })

      // Create placeholder assistant message that will be filled during streaming
      const assistantMessage = await tx.message.create({
        data: {
          conversationId: conversation.id,
          role: 'assistant',
          content: '',
          status: 'processing',
          notificationSent: true // No notification needed for streaming
        }
      })

      // Update conversation timestamp
      await tx.conversation.update({
        where: { id: conversation.id },
        data: { updatedAt: new Date() }
      })

      // Reload conversation with all messages to get correct history
      const updatedConversation = await tx.conversation.findFirst({
        where: { id: conversation.id },
        include: { messages: { orderBy: { createdAt: 'asc' } } }
      })

      return {
        conversation: updatedConversation!,
        userMessage,
        assistantMessage
      }
    })

    const { conversation, userMessage, assistantMessage } = result

    // Get recent journal entries for context
    const recentEntries = await prisma.journalEntry.findMany({
      where: { userId: session.user.id },
      orderBy: { date: 'desc' },
      take: 5
    })

    const entryContents = recentEntries.map(e =>
      `[${e.date.toLocaleDateString()}]${e.mood ? ` (Mood: ${e.mood})` : ''}\n${e.content}`
    )

    // Build conversation history (exclude the assistant message we just created)
    const filteredMessages = conversation.messages
      .filter(m => m.id !== assistantMessage.id && m.content)

    // Find the index of the first user message
    const firstUserIndex = filteredMessages.findIndex(m => m.role === 'user')

    // Collect any initial assistant messages to add to context
    const initialAssistantMessages = firstUserIndex > 0
      ? filteredMessages.slice(0, firstUserIndex).filter(m => m.role === 'assistant')
      : []

    // Only include messages starting from the first user message
    const conversationHistory = filteredMessages
      .slice(firstUserIndex >= 0 ? firstUserIndex : 0)
      .map(m => ({
        role: m.role as 'user' | 'assistant',
        content: m.content
      }))

    // Build system prompt
    let systemPrompt = buildCoachContext(entryContents)

    if (initialAssistantMessages.length > 0) {
      const initialContext = initialAssistantMessages
        .map(m => m.content)
        .join('\n\n')
      systemPrompt += `\n\n## Your Previous Message (you initiated this conversation)\nYou sent a check-in message to the user that started this conversation:\n"${initialContext}"\n\nThe user is now responding to your message. Continue the conversation naturally, acknowledging what you said and responding to their reply.`
    }

    // Create a streaming response using Server-Sent Events
    const encoder = new TextEncoder()

    const stream = new ReadableStream({
      async start(controller) {
        try {
          // Send initial metadata
          const initData = {
            type: 'init',
            conversationId: conversation.id,
            userMessage: {
              id: userMessage.id,
              role: userMessage.role,
              content: userMessage.content,
              createdAt: userMessage.createdAt.toISOString()
            },
            assistantMessageId: assistantMessage.id
          }
          controller.enqueue(encoder.encode(`data: ${JSON.stringify(initData)}\n\n`))

          // Stream from Claude API
          let fullContent = ''

          const streamResponse = await anthropic.messages.stream({
            model: CLAUDE_MODEL,
            max_tokens: 1024,
            system: systemPrompt,
            messages: conversationHistory
          })

          for await (const event of streamResponse) {
            if (event.type === 'content_block_delta') {
              const delta = event.delta
              if ('text' in delta) {
                fullContent += delta.text
                const chunk = {
                  type: 'delta',
                  text: delta.text
                }
                controller.enqueue(encoder.encode(`data: ${JSON.stringify(chunk)}\n\n`))
              }
            }
          }

          // Update the assistant message with the complete content
          await prisma.message.update({
            where: { id: assistantMessage.id },
            data: {
              content: fullContent || "I'm sorry, I wasn't able to generate a response. Please try again.",
              status: 'completed'
            }
          })

          // Send completion event
          const doneData = {
            type: 'done',
            assistantMessage: {
              id: assistantMessage.id,
              role: 'assistant',
              content: fullContent,
              createdAt: assistantMessage.createdAt.toISOString(),
              status: 'completed'
            }
          }
          controller.enqueue(encoder.encode(`data: ${JSON.stringify(doneData)}\n\n`))

          controller.close()
        } catch (error) {
          console.error('Streaming error:', error)

          // Update message as failed
          await prisma.message.update({
            where: { id: assistantMessage.id },
            data: {
              content: "I'm sorry, something went wrong. Please try again.",
              status: 'completed'
            }
          })

          const errorData = {
            type: 'error',
            error: 'Failed to generate response'
          }
          controller.enqueue(encoder.encode(`data: ${JSON.stringify(errorData)}\n\n`))
          controller.close()
        }
      }
    })

    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      }
    })
  } catch (error) {
    if (error instanceof Error && error.message === 'Conversation not found') {
      return new Response(JSON.stringify({ error: 'Conversation not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' }
      })
    }
    console.error('Chat stream error:', error)
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    })
  }
}
