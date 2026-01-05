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
        <div className="text-gray-500 dark:text-gray-400">Loading...</div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-8">Journal</h1>

      <div className="bg-white dark:bg-[#1a1a1a] rounded-xl shadow-sm dark:shadow-black/20 border border-gray-200 dark:border-gray-800 p-6 mb-8">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">New Entry</h2>
        <JournalEditor onSave={handleSave} />
      </div>

      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white">Past Entries</h2>
        {entries.length === 0 ? (
          <p className="text-gray-500 dark:text-gray-400 text-center py-8">No entries yet. Write your first one above!</p>
        ) : (
          entries.map((entry) => (
            <JournalEntryCard
              key={entry.id}
              id={entry.id}
              content={entry.content}
              mood={entry.mood}
              tags={entry.tags}
              date={entry.date}
              onDelete={handleDelete}
            />
          ))
        )}
      </div>
    </div>
  )
}
