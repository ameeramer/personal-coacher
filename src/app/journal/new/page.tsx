'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'
import { FullPageJournalEditor } from '@/components/journal/FullPageJournalEditor'

export default function NewJournalEntryPage() {
  const { data: session, status } = useSession()
  const router = useRouter()

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  const handleSave = async (data: { content: string; mood: string | null; tags: string[] }) => {
    const res = await fetch('/api/journal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}))
      throw new Error(errorData.message || 'Failed to create entry')
    }

    return true
  }

  if (status === 'loading') {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-2 border-amber-500 dark:border-violet-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-amber-600 dark:text-gray-400 font-medium">Loading...</p>
        </div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  return (
    <FullPageJournalEditor
      onSave={handleSave}
      placeholder="Start writing..."
    />
  )
}
