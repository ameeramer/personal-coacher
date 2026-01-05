'use client'

import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

interface ChatInterfaceProps {
  conversationId?: string
  initialMessages?: Message[]
  onSendMessage: (message: string, conversationId?: string) => Promise<{
    conversationId: string
    message: Message
  }>
}

export function ChatInterface({
  conversationId: initialConversationId,
  initialMessages = [],
  onSendMessage
}: ChatInterfaceProps) {
  const [messages, setMessages] = useState<Message[]>(initialMessages)
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [conversationId, setConversationId] = useState(initialConversationId)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Sync messages when conversation changes (only when explicitly selecting a different conversation)
  useEffect(() => {
    // Only reset messages when selecting an existing conversation (with messages)
    // or when explicitly starting a new conversation (null id, empty messages)
    // Don't reset when a new conversation was just created (transitioning from undefined to new id)
    if (initialConversationId !== conversationId) {
      // This is a different conversation selection, sync everything
      setMessages(initialMessages)
      setConversationId(initialConversationId)
    }
  }, [initialConversationId, initialMessages, conversationId])

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
      createdAt: new Date().toISOString()
    }

    setMessages(prev => [...prev, userMessage])
    setInput('')
    setSending(true)

    try {
      const response = await onSendMessage(input, conversationId)
      setConversationId(response.conversationId)
      setMessages(prev => [
        ...prev.filter(m => m.id !== userMessage.id),
        { ...userMessage, id: `user-${Date.now()}` },
        response.message
      ])
    } catch {
      setMessages(prev => prev.filter(m => m.id !== userMessage.id))
      setInput(input)
    } finally {
      setSending(false)
    }
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
              ) : (
                <div className="prose prose-sm dark:prose-invert max-w-none prose-p:my-1 prose-headings:my-2 prose-p:leading-relaxed">
                  <ReactMarkdown>{message.content}</ReactMarkdown>
                </div>
              )}
            </div>
          </div>
        ))}
        {sending && (
          <div className="flex justify-start">
            <div className="bg-gray-100 dark:bg-gray-800 rounded-2xl rounded-bl-md px-4 py-3">
              <div className="flex space-x-1.5">
                <div className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                <div className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                <div className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
              </div>
            </div>
          </div>
        )}
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
