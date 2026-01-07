'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { RecorderInterface, Transcription } from '@/components/recorder'

interface RecordingSession {
  id: string
  title: string | null
  chunkDuration: number
  status: string
  startedAt: string
  endedAt: string | null
  transcriptions: Transcription[]
}

export default function RecorderPage() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const [sessions, setSessions] = useState<RecordingSession[]>([])
  const [selectedSession, setSelectedSession] = useState<RecordingSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  useEffect(() => {
    if (session) {
      fetchSessions()
    }
  }, [session])

  const fetchSessions = async () => {
    try {
      const res = await fetch('/api/recorder/sessions')
      if (res.ok) {
        const data = await res.json()
        setSessions(data)
      }
    } catch (error) {
      console.error('Failed to fetch sessions:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchSession = async (id: string) => {
    try {
      const res = await fetch(`/api/recorder/sessions/${id}`)
      if (res.ok) {
        const data = await res.json()
        setSelectedSession(data)
      }
    } catch (error) {
      console.error('Failed to fetch session:', error)
    }
  }

  const handleSelectSession = (id: string) => {
    fetchSession(id)
    setSidebarOpen(false)
  }

  const handleNewSession = () => {
    setSelectedSession(null)
    setSidebarOpen(false)
  }

  const handleSessionCreated = (sessionId: string) => {
    fetchSession(sessionId)
    fetchSessions()
  }

  const handleDeleteSession = async (id: string) => {
    if (!confirm('Are you sure you want to delete this recording session?')) {
      return
    }

    try {
      const res = await fetch(`/api/recorder/sessions/${id}`, {
        method: 'DELETE'
      })
      if (res.ok) {
        setSessions(prev => prev.filter(s => s.id !== id))
        if (selectedSession?.id === id) {
          setSelectedSession(null)
        }
      }
    } catch (error) {
      console.error('Failed to delete session:', error)
    }
  }

  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'recording':
        return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
      case 'paused':
        return 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
      case 'completed':
        return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400'
      default:
        return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
    }
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

  return (
    <div className="fixed inset-0 top-0 flex overflow-hidden bg-gray-50 dark:bg-[#0f0f0f]">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 top-0 bg-black/50 z-20 sm:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <aside
        className={`
          fixed sm:relative z-30 sm:z-auto
          w-72 sm:w-80 sm:h-full
          bg-white dark:bg-[#1a1a1a] border-r border-gray-200 dark:border-gray-800
          transform transition-transform duration-300 ease-out
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full sm:translate-x-0'}
          flex-shrink-0 shadow-xl sm:shadow-none dark:shadow-black/30
          top-0 bottom-0
        `}
      >
        <div className="h-full flex flex-col">
          {/* Sidebar header */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-800">
            <button
              onClick={handleNewSession}
              className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-gradient-to-r from-emerald-500 to-teal-500 dark:from-violet-500 dark:to-purple-500 text-white rounded-xl font-medium shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-[1.02]"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
              </svg>
              New Recording
            </button>
          </div>

          {/* Sessions list */}
          <div className="flex-1 overflow-y-auto">
            <div className="p-2 space-y-1">
              {sessions.length === 0 ? (
                <div className="text-center py-8 text-gray-500 dark:text-gray-400 text-sm">
                  No recording sessions yet
                </div>
              ) : (
                sessions.map((s) => (
                  <div
                    key={s.id}
                    className={`group relative p-3 rounded-xl cursor-pointer transition-all duration-200 ${
                      selectedSession?.id === s.id
                        ? 'bg-emerald-50 dark:bg-violet-900/30 border border-emerald-200 dark:border-violet-700'
                        : 'hover:bg-gray-50 dark:hover:bg-gray-800/50 border border-transparent'
                    }`}
                    onClick={() => handleSelectSession(s.id)}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <div className="font-medium text-gray-900 dark:text-white truncate text-sm">
                          {s.title || `Recording ${formatDate(s.startedAt)}`}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                          {s.transcriptions?.length || 0} transcription{(s.transcriptions?.length || 0) !== 1 ? 's' : ''}
                        </div>
                      </div>
                      <span className={`text-xs px-2 py-0.5 rounded-full ${getStatusColor(s.status)}`}>
                        {s.status}
                      </span>
                    </div>
                    {/* Delete button */}
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDeleteSession(s.id)
                      }}
                      className="absolute top-2 right-2 p-1.5 rounded-lg opacity-0 group-hover:opacity-100 hover:bg-red-100 dark:hover:bg-red-900/30 text-gray-400 hover:text-red-600 dark:hover:text-red-400 transition-all"
                      aria-label="Delete session"
                    >
                      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 flex flex-col min-w-0 bg-white dark:bg-[#141414] overflow-hidden">
        {/* Header */}
        <header className="flex items-center gap-3 px-4 h-14 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-[#1a1a1a] flex-shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="sm:hidden p-2.5 -ml-2 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 active:bg-gray-200 dark:active:bg-gray-700 transition-colors touch-manipulation"
            aria-label="Open sessions"
          >
            <svg className="w-6 h-6 text-gray-600 dark:text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <h1 className="font-semibold text-gray-900 dark:text-white truncate text-base">
            {selectedSession?.title || 'New Recording'}
          </h1>
        </header>

        {/* Recorder interface */}
        <div className="flex-1 overflow-hidden">
          <RecorderInterface
            sessionId={selectedSession?.id}
            initialTranscriptions={selectedSession?.transcriptions || []}
            onSessionCreated={handleSessionCreated}
          />
        </div>
      </main>
    </div>
  )
}
