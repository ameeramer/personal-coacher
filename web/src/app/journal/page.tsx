'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
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
          <div className="w-8 h-8 border-2 border-[var(--accent-primary)] border-t-transparent rounded-full animate-spin" />
          <p className="text-[var(--muted)] font-medium">Loading your journal...</p>
        </div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  return (
    <div className="min-h-screen bg-[var(--background)]">
      <div className="max-w-4xl mx-auto px-5 py-10">
        {/* Header - iOS large title style */}
        <header className="mb-10">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="p-3 bg-[var(--accent-primary)] rounded-2xl shadow-lg shadow-[var(--accent-primary)]/20">
                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                </svg>
              </div>
              <div>
                <h1 className="text-3xl font-bold text-[var(--foreground)]">
                  Journal
                </h1>
                <p className="text-[var(--muted)] text-sm mt-1">
                  Capture your thoughts and reflect on your journey
                </p>
              </div>
            </div>
          </div>
        </header>

        {/* Entries Section */}
        <section>
          {entries.length > 0 && (
            <div className="flex items-center justify-between mb-5">
              <span className="text-sm text-[var(--muted-foreground)]">
                {entries.length} {entries.length === 1 ? 'entry' : 'entries'}
              </span>
              <button
                onClick={() => router.push('/journal/new')}
                className="text-[var(--accent-primary)] hover:text-[var(--accent-secondary)] transition-colors duration-200 p-2 rounded-xl hover:bg-[var(--accent-primary)]/10"
                title="New journal entry"
                aria-label="Create new journal entry"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
              </button>
            </div>
          )}

          {entries.length === 0 ? (
            <div className="text-center py-16 px-8 bg-[var(--glass-bg)] backdrop-blur-[20px] rounded-3xl border border-[var(--glass-border)]">
              <div className="w-20 h-20 mx-auto mb-6 bg-[var(--accent-primary)]/10 rounded-full flex items-center justify-center">
                <svg className="w-10 h-10 text-[var(--accent-primary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                </svg>
              </div>
              <h3 className="text-xl font-semibold text-[var(--foreground)] mb-3">
                Your journal is empty
              </h3>
              <p className="text-[var(--muted)] max-w-sm mx-auto mb-8 leading-relaxed">
                Start capturing your thoughts and experiences. Your first entry is just a few words away!
              </p>
              <button
                onClick={() => router.push('/journal/new')}
                className="inline-flex items-center gap-2 px-6 py-3 bg-[var(--accent-primary)] text-white rounded-2xl shadow-lg shadow-[var(--accent-primary)]/20 hover:shadow-xl hover:shadow-[var(--accent-primary)]/30 transition-all duration-200"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                <span className="font-medium">Write your first entry</span>
              </button>
            </div>
          ) : (
            <div className="space-y-4">
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
