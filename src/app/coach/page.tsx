'use client'

import { useSession } from 'next-auth/react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useState, Suspense } from 'react'
import { ChatInterface } from '@/components/coach/ChatInterface'
import { ConversationList } from '@/components/coach/ConversationList'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

interface Conversation {
  id: string
  title: string | null
  updatedAt: string
  messages?: Message[]
}

function CoachPageContent() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null)
  const [loading, setLoading] = useState(true)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [initialCoachMessage, setInitialCoachMessage] = useState<string | null>(null)

  // Check for notification message in URL params
  useEffect(() => {
    const notificationTitle = searchParams.get('notificationTitle')
    const notificationBody = searchParams.get('notificationBody')

    if (notificationTitle && notificationBody) {
      // Create the coach message from notification
      setInitialCoachMessage(`**${notificationTitle}**\n\n${notificationBody}`)

      // Clear the URL params to avoid showing the message again on refresh
      const url = new URL(window.location.href)
      url.searchParams.delete('notificationTitle')
      url.searchParams.delete('notificationBody')
      window.history.replaceState({}, '', url.pathname)
    }
  }, [searchParams])

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  useEffect(() => {
    if (session) {
      fetchConversations()
    }
  }, [session])

  const fetchConversations = async () => {
    try {
      const res = await fetch('/api/conversations')
      const data = await res.json()
      setConversations(data)
    } catch (error) {
      console.error('Failed to fetch conversations:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchConversation = async (id: string) => {
    try {
      const res = await fetch(`/api/conversations/${id}`)
      const data = await res.json()
      setSelectedConversation(data)
    } catch (error) {
      console.error('Failed to fetch conversation:', error)
    }
  }

  const handleSelectConversation = (id: string) => {
    fetchConversation(id)
    setSidebarOpen(false)
  }

  const handleNewConversation = () => {
    setSelectedConversation(null)
    setSidebarOpen(false)
  }

  const handleSendMessage = async (message: string, conversationId?: string, initialAssistantMessage?: string) => {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, conversationId, initialAssistantMessage })
    })

    if (!res.ok) {
      throw new Error('Failed to send message')
    }

    const data = await res.json()

    // Update sidebar without affecting the current chat
    fetchConversations()

    // If this was a new conversation, update the selected conversation with the new ID
    // but don't fetch it (which would reset the messages state)
    if (!conversationId && data.conversationId) {
      setSelectedConversation(prev => ({
        id: data.conversationId,
        title: prev?.title || null,
        updatedAt: new Date().toISOString(),
        messages: prev?.messages
      }))
    }

    return data
  }

  if (status === 'loading' || loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="text-gray-500 dark:text-gray-400">Loading...</div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  // Heights: Mobile nav = 136px (64px header + 72px bottom nav), Desktop nav = 64px
  // Using fixed layout to prevent iOS safari viewport issues
  return (
    <div className="fixed inset-0 top-[136px] sm:top-16 flex overflow-hidden bg-gray-50 dark:bg-[#0f0f0f]">
      {/* Mobile overlay - below sidebar but covers content */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 top-[136px] bg-black/50 z-20 sm:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar - slides from left on mobile, always visible on desktop */}
      <aside
        className={`
          fixed sm:relative z-30 sm:z-auto
          w-72 sm:w-80 sm:h-full
          bg-white dark:bg-[#1a1a1a] border-r border-gray-200 dark:border-gray-800
          transform transition-transform duration-300 ease-out
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full sm:translate-x-0'}
          flex-shrink-0 shadow-xl sm:shadow-none dark:shadow-black/30
        `}
        style={{
          // Use inline style for proper mobile positioning
          top: 'var(--nav-height-mobile, 136px)',
          bottom: 0,
        }}
      >
        <div className="h-full overflow-hidden">
          <ConversationList
            conversations={conversations}
            selectedId={selectedConversation?.id}
            onSelect={handleSelectConversation}
            onNewConversation={handleNewConversation}
          />
        </div>
      </aside>

      {/* Main chat area */}
      <main className="flex-1 flex flex-col min-w-0 bg-white dark:bg-[#141414] overflow-hidden">
        {/* Header bar with hamburger menu on mobile */}
        <header className="flex items-center gap-3 px-4 h-14 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-[#1a1a1a] flex-shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="sm:hidden p-2.5 -ml-2 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 active:bg-gray-200 dark:active:bg-gray-700 transition-colors touch-manipulation"
            aria-label="Open conversations"
          >
            <svg className="w-6 h-6 text-gray-600 dark:text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <h1 className="font-semibold text-gray-900 dark:text-white truncate text-base">
            {selectedConversation?.title || 'New Conversation'}
          </h1>
        </header>

        {/* Chat interface fills remaining space */}
        <div className="flex-1 overflow-hidden">
          <ChatInterface
            conversationId={selectedConversation?.id}
            initialMessages={selectedConversation?.messages || []}
            onSendMessage={handleSendMessage}
            initialCoachMessage={initialCoachMessage}
          />
        </div>
      </main>
    </div>
  )
}

export default function CoachPage() {
  return (
    <Suspense fallback={
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="text-gray-500 dark:text-gray-400">Loading...</div>
      </div>
    }>
      <CoachPageContent />
    </Suspense>
  )
}
