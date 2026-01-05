'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { JournalEditor } from '@/components/journal/JournalEditor'
import { JournalEntryCard } from '@/components/journal/JournalEntryCard'

interface JournalEntry {
  id: string
  content: string
  mood: string | null
  tags: string[]
  date: string
}

export default function JournalPage() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const [entries, setEntries] = useState<JournalEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  useEffect(() => {
    if (session) {
      fetchEntries()
    }
  }, [session])

  const fetchEntries = async () => {
    try {
      const res = await fetch('/api/journal')
      const data = await res.json()
      setEntries(data)
    } catch (error) {
      console.error('Failed to fetch entries:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async (entry: { content: string; mood: string; tags: string[] }) => {
    const res = await fetch('/api/journal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(entry)
    })

    if (res.ok) {
      fetchEntries()
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this entry?')) return

    const res = await fetch(`/api/journal/${id}`, {
      method: 'DELETE'
    })

    if (res.ok) {
      setEntries(entries.filter(e => e.id !== id))
    }
  }

  if (status === 'loading' || loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-2 border-amber-500 dark:border-violet-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-amber-600 dark:text-gray-400 font-medium">Loading your journal...</p>
        </div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f]">
      <div className="max-w-4xl mx-auto px-4 py-8">
        {/* Header */}
        <header className="mb-10">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-gradient-to-br from-amber-400 to-orange-500 dark:from-violet-500 dark:to-purple-600 rounded-xl shadow-lg shadow-amber-500/20 dark:shadow-violet-500/20">
              <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
              </svg>
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white font-serif">
                My Journal
              </h1>
              <p className="text-amber-600/80 dark:text-gray-500 text-sm">
                Capture your thoughts, track your mood, and reflect on your journey
              </p>
            </div>
          </div>
        </header>

        {/* New Entry Section */}
        <section className="mb-12">
          <JournalEditor onSave={handleSave} />
        </section>

        {/* Past Entries Section */}
        <section>
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white font-serif flex items-center gap-2">
              <svg className="w-5 h-5 text-amber-500 dark:text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              Past Entries
            </h2>
            {entries.length > 0 && (
              <span className="text-sm text-amber-600/60 dark:text-gray-500">
                {entries.length} {entries.length === 1 ? 'entry' : 'entries'}
              </span>
            )}
          </div>

          {entries.length === 0 ? (
            <div className="text-center py-16 px-6 bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-900/50 dark:to-[#1a1a1a] rounded-2xl border border-amber-100 dark:border-gray-800 border-dashed">
              <div className="w-16 h-16 mx-auto mb-4 bg-amber-100 dark:bg-gray-800 rounded-full flex items-center justify-center">
                <svg className="w-8 h-8 text-amber-400 dark:text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                </svg>
              </div>
              <h3 className="text-lg font-medium text-gray-900 dark:text-gray-200 mb-2">
                Your journal is empty
              </h3>
              <p className="text-amber-600/70 dark:text-gray-500 max-w-sm mx-auto">
                Start capturing your thoughts and experiences. Your first entry is just a few words away!
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              {entries.map((entry) => (
                <JournalEntryCard
                  key={entry.id}
                  id={entry.id}
                  content={entry.content}
                  mood={entry.mood}
                  tags={entry.tags}
                  date={entry.date}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
