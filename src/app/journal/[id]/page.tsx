'use client'

import { useSession } from 'next-auth/react'
import { useRouter, useParams } from 'next/navigation'
import { useEffect, useState, useCallback } from 'react'
import { FullPageJournalEditor } from '@/components/journal/FullPageJournalEditor'

interface JournalEntry {
  id: string
  content: string
  mood: string | null
  tags: string[]
  date: string
}

export default function JournalEntryPage() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const params = useParams()
  const entryId = params.id as string

  const [entry, setEntry] = useState<JournalEntry | null>(null)
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  const fetchEntry = useCallback(async () => {
    try {
      const res = await fetch(`/api/journal/${entryId}`)
      if (res.ok) {
        const data = await res.json()
        setEntry(data)
      } else if (res.status === 404) {
        setFetchError('Entry not found')
        setTimeout(() => router.push('/journal'), 2000)
      } else {
        setFetchError('Failed to load entry')
        setTimeout(() => router.push('/journal'), 2000)
      }
    } catch (error) {
      console.error('Failed to fetch entry:', error)
      setFetchError('Failed to load entry')
      setTimeout(() => router.push('/journal'), 2000)
    } finally {
      setLoading(false)
    }
  }, [entryId, router])

  useEffect(() => {
    if (session && entryId) {
      fetchEntry()
    }
  }, [session, entryId, fetchEntry])

  const handleSave = async (data: { content: string; mood: string | null; tags: string[] }) => {
    const res = await fetch(`/api/journal/${entryId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}))
      throw new Error(errorData.message || 'Failed to update entry')
    }

    return true
  }

  if (status === 'loading' || loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-2 border-amber-500 dark:border-violet-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-amber-600 dark:text-gray-400 font-medium">Loading entry...</p>
        </div>
      </div>
    )
  }

  if (fetchError) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f]">
        <div className="flex flex-col items-center gap-4">
          <svg className="w-12 h-12 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p className="text-red-600 dark:text-red-400 font-medium">{fetchError}</p>
          <p className="text-amber-600 dark:text-gray-400 text-sm">Redirecting to journal...</p>
        </div>
      </div>
    )
  }

  if (!session || !entry) {
    return null
  }

  return (
    <FullPageJournalEditor
      initialContent={entry.content}
      initialMood={entry.mood || ''}
      initialTags={entry.tags}
      onSave={handleSave}
      placeholder="Continue writing..."
    />
  )
}
