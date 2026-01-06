'use client'

import { useState, useRef, useEffect, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  status?: 'pending' | 'completed'
}

interface ChatResponse {
  conversationId: string
  userMessage: {
    id: string
    role: string
    content: string
    createdAt: string
  }
  pendingMessage: {
    id: string
    role: string
    status: 'pending'
    createdAt: string
  }
  processing: boolean
}

interface ChatInterfaceProps {
  conversationId?: string
  initialMessages?: Message[]
  onSendMessage: (message: string, conversationId?: string, initialAssistantMessage?: string) => Promise<ChatResponse>
  initialCoachMessage?: string | null
}

// Polling interval for checking message status (3 seconds)
const POLL_INTERVAL_MS = 3000

export function ChatInterface({
  conversationId: initialConversationId,
  initialMessages = [],
  onSendMessage,
  initialCoachMessage
}: ChatInterfaceProps) {
  // Create the initial messages array, including coach message from notification if present
  const getInitialMessages = (): Message[] => {
    if (initialMessages.length > 0) {
      return initialMessages
    }
    if (initialCoachMessage) {
      return [{
        id: 'notification-initial',
        role: 'assistant' as const,
        content: initialCoachMessage,
        createdAt: new Date().toISOString(),
        status: 'completed'
      }]
    }
    return []
  }

  const [messages, setMessages] = useState<Message[]>(getInitialMessages())
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [conversationId, setConversationId] = useState(initialConversationId)
  const [pendingMessageId, setPendingMessageId] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null)

  // Mark message as seen (prevents notification from being sent)
  const markMessageAsSeen = useCallback(async (messageId: string) => {
    try {
      await fetch('/api/chat/mark-seen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messageId })
      })
    } catch (error) {
      console.error('Failed to mark message as seen:', error)
    }
  }, [])

  // Poll for message status
  const pollMessageStatus = useCallback(async (messageId: string) => {
    try {
      const res = await fetch(`/api/chat/status?messageId=${messageId}`)
      if (!res.ok) return null
      const data = await res.json()
      return data.message
    } catch (error) {
      console.error('Failed to poll message status:', error)
      return null
    }
  }, [])

  // Sync messages when conversation changes (only when explicitly selecting a different conversation)
  useEffect(() => {
    // Only reset messages when selecting an existing conversation (with messages)
    // or when explicitly starting a new conversation (null id, empty messages)
    // Don't reset when a new conversation was just created (transitioning from undefined to new id)
    if (initialConversationId !== conversationId) {
      // This is a different conversation selection, sync everything
      // eslint-disable-next-line react-hooks/set-state-in-effect -- Intentional prop-to-state sync
      setMessages(initialMessages)
      setConversationId(initialConversationId)
      // Clear any pending polling
      setPendingMessageId(null)
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current)
        pollIntervalRef.current = null
      }
    }
  }, [initialConversationId, initialMessages, conversationId])

  // Handle initialCoachMessage arriving after mount (from URL params)
  useEffect(() => {
    if (initialCoachMessage && messages.length === 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- Intentional prop-to-state sync
      setMessages([{
        id: 'notification-initial',
        role: 'assistant',
        content: initialCoachMessage,
        createdAt: new Date().toISOString(),
        status: 'completed'
      }])
    }
  }, [initialCoachMessage, messages.length])

  // Poll for pending message completion
  useEffect(() => {
    if (!pendingMessageId) return

    // Track if component is still mounted to prevent state updates after unmount
    let isMounted = true

    const poll = async () => {
      const message = await pollMessageStatus(pendingMessageId)
      // Check if still mounted before updating state
      if (!isMounted) return

      if (message && message.status === 'completed' && message.content) {
        // Update the message in state with the completed content
        setMessages(prev => prev.map(m =>
          m.id === pendingMessageId
            ? { ...m, content: message.content, status: 'completed' }
            : m
        ))
        // Mark as seen to prevent notification
        markMessageAsSeen(pendingMessageId)
        // Stop polling
        setPendingMessageId(null)
        setSending(false)
        if (pollIntervalRef.current) {
          clearInterval(pollIntervalRef.current)
          pollIntervalRef.current = null
        }
      }
    }

    // Start polling
    poll() // Poll immediately
    pollIntervalRef.current = setInterval(poll, POLL_INTERVAL_MS)

    return () => {
      isMounted = false
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current)
        pollIntervalRef.current = null
      }
    }
  }, [pendingMessageId, pollMessageStatus, markMessageAsSeen])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || sending) return

    const userMessage: Message = {
      id: `temp-${Date.now()}`,
      role: 'user',
      content: input,
      createdAt: new Date().toISOString(),
      status: 'completed'
    }

    setMessages(prev => [...prev, userMessage])
    setInput('')
    setSending(true)

    try {
      // Pass the initial coach message if this is a new conversation (no conversationId yet)
      // so the AI is aware of its own notification message
      const assistantContext = !conversationId && initialCoachMessage ? initialCoachMessage : undefined
      const response = await onSendMessage(input, conversationId, assistantContext)
      setConversationId(response.conversationId)

      // Update with real user message ID and add pending assistant message
      const pendingMsg: Message = {
        id: response.pendingMessage.id,
        role: 'assistant',
        content: '', // Will be filled when AI responds
        createdAt: response.pendingMessage.createdAt,
        status: 'pending'
      }

      setMessages(prev => [
        ...prev.filter(m => m.id !== userMessage.id),
        {
          id: response.userMessage.id,
          role: 'user' as const,
          content: response.userMessage.content,
          createdAt: response.userMessage.createdAt,
          status: 'completed'
        },
        pendingMsg
      ])

      // Start polling for the pending message
      setPendingMessageId(response.pendingMessage.id)
    } catch {
      setMessages(prev => prev.filter(m => m.id !== userMessage.id))
      setInput(input)
      setSending(false)
    }
    // Note: setSending(false) is handled by the polling effect when message completes
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Messages area with smooth scrolling */}
      <div className="flex-1 overflow-y-auto overscroll-contain p-4 space-y-4">
        {messages.length === 0 && (
          <div className="text-center text-gray-500 dark:text-gray-400 mt-8 px-4">
            <p className="text-lg mb-2 font-medium">Welcome to your personal coach</p>
            <p className="text-sm text-gray-400 dark:text-gray-500">Ask me anything about personal growth, or just tell me about your day!</p>
          </div>
        )}
        {messages.map((message) => (
          <div
            key={message.id}
            className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-[85%] sm:max-w-[75%] rounded-2xl px-4 py-3 ${
                message.role === 'user'
                  ? 'bg-emerald-600 dark:bg-violet-600 text-white rounded-br-md'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-200 rounded-bl-md'
              }`}
            >
              {message.role === 'user' ? (
                <p className="whitespace-pre-wrap text-[15px] leading-relaxed">{message.content}</p>
              ) : message.status === 'pending' ? (
                // Show loading indicator for pending assistant messages
                <div className="flex space-x-1.5">
                  <div className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <div className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                </div>
              ) : (
                <div className="prose prose-sm dark:prose-invert max-w-none prose-p:my-1 prose-headings:my-2 prose-p:leading-relaxed">
                  <ReactMarkdown>{message.content}</ReactMarkdown>
                </div>
              )}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* Input form with safe area padding for phones with home indicators */}
      <form
        onSubmit={handleSubmit}
        className="border-t border-gray-200 dark:border-gray-800 p-3 sm:p-4 bg-white dark:bg-[#1a1a1a] flex-shrink-0"
        style={{ paddingBottom: 'max(12px, env(safe-area-inset-bottom, 12px))' }}
      >
        <div className="flex gap-2 items-end">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Type your message..."
            className="flex-1 px-4 py-3 border border-gray-300 dark:border-gray-700 rounded-xl focus:ring-2 focus:ring-emerald-500 dark:focus:ring-violet-500 focus:border-emerald-500 dark:focus:border-violet-500 focus:outline-none transition-shadow touch-manipulation text-base bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
            disabled={sending}
            enterKeyHint="send"
            autoComplete="off"
            autoCorrect="on"
          />
          <button
            type="submit"
            disabled={!input.trim() || sending}
            className="px-5 py-3 bg-emerald-600 dark:bg-violet-600 text-white rounded-xl font-medium hover:bg-emerald-700 dark:hover:bg-violet-700 active:bg-emerald-800 dark:active:bg-violet-800 disabled:opacity-50 disabled:cursor-not-allowed transition-colors touch-manipulation min-w-[72px]"
          >
            Send
          </button>
        </div>
      </form>
    </div>
  )
}
