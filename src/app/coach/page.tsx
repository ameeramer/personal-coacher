'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
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

export default function CoachPage() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null)
  const [loading, setLoading] = useState(true)
  const [sidebarOpen, setSidebarOpen] = useState(false)

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

  const handleSendMessage = async (message: string, conversationId?: string) => {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, conversationId })
    })

    if (!res.ok) {
      throw new Error('Failed to send message')
    }

    const data = await res.json()
    fetchConversations()
    return data
  }

  if (status === 'loading' || loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="text-gray-500">Loading...</div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  // Heights: Mobile nav = 112px (64 + 48), Desktop nav = 64px
  return (
    <div className="h-[calc(100dvh-112px)] sm:h-[calc(100dvh-64px)] flex overflow-hidden">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 sm:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar - hidden on mobile by default, visible on desktop */}
      <aside className={`
        fixed sm:static inset-y-0 left-0 z-50 w-[280px] sm:w-80 bg-white border-r border-gray-200
        transform transition-transform duration-300 ease-in-out
        ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}
        sm:translate-x-0 sm:flex-shrink-0
        top-[112px] sm:top-0 h-[calc(100dvh-112px)] sm:h-auto
      `}>
        <ConversationList
          conversations={conversations}
          selectedId={selectedConversation?.id}
          onSelect={handleSelectConversation}
          onNewConversation={handleNewConversation}
        />
      </aside>

      {/* Main chat area */}
      <div className="flex-1 flex flex-col min-w-0 bg-white">
        {/* Mobile header - always visible on mobile */}
        <header className="sm:hidden flex items-center gap-3 px-4 py-3 border-b border-gray-200 bg-white flex-shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-2 -ml-2 rounded-lg hover:bg-gray-100 transition-colors"
            aria-label="Open conversations"
          >
            <svg className="w-6 h-6 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <h1 className="font-semibold text-gray-900 truncate">
            {selectedConversation?.title || 'New Conversation'}
          </h1>
        </header>

        {/* Chat interface - scrollable */}
        <div className="flex-1 min-h-0">
          <ChatInterface
            conversationId={selectedConversation?.id}
            initialMessages={selectedConversation?.messages || []}
            onSendMessage={handleSendMessage}
          />
        </div>
      </div>
    </div>
  )
}
