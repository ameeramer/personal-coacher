'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'

interface Summary {
  id: string
  type: string
  content: string
  startDate: string
  endDate: string
  createdAt: string
}

export default function SummariesPage() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const [summaries, setSummaries] = useState<Summary[]>([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState<string | null>(null)
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())

  const toggleExpanded = (id: string) => {
    setExpandedIds(prev => {
      const newSet = new Set(prev)
      if (newSet.has(id)) {
        newSet.delete(id)
      } else {
        newSet.add(id)
      }
      return newSet
    })
  }

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  useEffect(() => {
    if (session) {
      fetchSummaries()
    }
  }, [session])

  const fetchSummaries = async () => {
    try {
      const res = await fetch('/api/summary')
      const data = await res.json()
      setSummaries(data)
    } catch (error) {
      console.error('Failed to fetch summaries:', error)
    } finally {
      setLoading(false)
    }
  }

  const generateSummary = async (type: 'daily' | 'weekly' | 'monthly') => {
    setGenerating(type)
    try {
      const res = await fetch('/api/summary', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type })
      })

      if (res.ok) {
        fetchSummaries()
      } else {
        const error = await res.json()
        alert(error.error || 'Failed to generate summary')
      }
    } catch (error) {
      console.error('Failed to generate summary:', error)
    } finally {
      setGenerating(null)
    }
  }

  if (status === 'loading' || loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-2 border-[var(--accent-primary)] border-t-transparent rounded-full animate-spin" />
          <p className="text-[var(--muted)] font-medium">Loading...</p>
        </div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  const summaryTypes = [
    { type: 'daily' as const, label: 'Daily', description: 'Summary of today\'s entries' },
    { type: 'weekly' as const, label: 'Weekly', description: 'Summary of this week\'s entries' },
    { type: 'monthly' as const, label: 'Monthly', description: 'Summary of this month\'s entries' }
  ]

  return (
    <div className="max-w-4xl mx-auto px-5 py-10">
      <h1 className="text-3xl font-bold text-[var(--foreground)] mb-10">Summaries</h1>

      <div className="grid gap-5 md:grid-cols-3 mb-10">
        {summaryTypes.map(({ type, label, description }) => (
          <button
            key={type}
            onClick={() => generateSummary(type)}
            disabled={generating !== null}
            className="p-7 bg-[var(--glass-bg)] backdrop-blur-[20px] rounded-2xl border border-[var(--glass-border)] shadow-[var(--glass-shadow)] hover:border-[var(--accent-primary)]/20 transition-all text-left disabled:opacity-50"
          >
            <h3 className="text-lg font-semibold text-[var(--foreground)]">{label}</h3>
            <p className="text-sm text-[var(--muted)] mt-2 leading-relaxed">{description}</p>
            {generating === type && (
              <p className="text-sm text-[var(--accent-primary)] mt-3 font-medium">Generating...</p>
            )}
          </button>
        ))}
      </div>

      <div className="space-y-5">
        <h2 className="text-xl font-semibold text-[var(--foreground)]">Past Summaries</h2>
        {summaries.length === 0 ? (
          <div className="bg-[var(--glass-bg)] backdrop-blur-[20px] rounded-2xl border border-[var(--glass-border)] p-10 text-center">
            <p className="text-[var(--muted)]">No summaries yet. Generate one above!</p>
          </div>
        ) : (
          summaries.map((summary) => {
            const isExpanded = expandedIds.has(summary.id)
            return (
              <div
                key={summary.id}
                className="bg-[var(--glass-bg)] backdrop-blur-[20px] rounded-2xl border border-[var(--glass-border)] shadow-[var(--glass-shadow)] overflow-hidden"
              >
                <button
                  onClick={() => toggleExpanded(summary.id)}
                  className="w-full p-5 flex justify-between items-center hover:bg-[var(--foreground)]/5 transition-colors text-left"
                >
                  <div className="flex items-center gap-3">
                    <span className="inline-block px-3 py-1.5 text-xs rounded-full bg-[var(--accent-primary)]/10 text-[var(--accent-primary)] capitalize font-medium">
                      {summary.type}
                    </span>
                    <p className="text-sm text-[var(--muted)]">
                      {new Date(summary.startDate).toLocaleDateString()} - {new Date(summary.endDate).toLocaleDateString()}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <p className="text-xs text-[var(--muted-foreground)]">
                      Generated {new Date(summary.createdAt).toLocaleDateString()}
                    </p>
                    <svg
                      className={`w-5 h-5 text-[var(--muted)] transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </div>
                </button>
                {isExpanded && (
                  <div className="px-6 pb-6 border-t border-[var(--glass-border)]">
                    <div className="prose prose-sm dark:prose-invert max-w-none text-[var(--foreground)] pt-5">
                      <ReactMarkdown>{summary.content}</ReactMarkdown>
                    </div>
                  </div>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
