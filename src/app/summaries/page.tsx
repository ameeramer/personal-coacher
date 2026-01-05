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
        <div className="text-gray-500 dark:text-gray-400">Loading...</div>
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
    <div className="max-w-4xl mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-8">Summaries</h1>

      <div className="grid gap-4 md:grid-cols-3 mb-8">
        {summaryTypes.map(({ type, label, description }) => (
          <button
            key={type}
            onClick={() => generateSummary(type)}
            disabled={generating !== null}
            className="p-6 bg-white dark:bg-[#1a1a1a] rounded-xl shadow-sm dark:shadow-black/20 border border-gray-200 dark:border-gray-800 hover:shadow-md dark:hover:shadow-black/30 transition-shadow text-left disabled:opacity-50"
          >
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">{label}</h3>
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{description}</p>
            {generating === type && (
              <p className="text-sm text-emerald-600 dark:text-emerald-400 mt-2">Generating...</p>
            )}
          </button>
        ))}
      </div>

      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white">Past Summaries</h2>
        {summaries.length === 0 ? (
          <div className="bg-white dark:bg-[#1a1a1a] rounded-xl shadow-sm dark:shadow-black/20 border border-gray-200 dark:border-gray-800 p-8 text-center">
            <p className="text-gray-500 dark:text-gray-400">No summaries yet. Generate one above!</p>
          </div>
        ) : (
          summaries.map((summary) => {
            const isExpanded = expandedIds.has(summary.id)
            return (
              <div
                key={summary.id}
                className="bg-white dark:bg-[#1a1a1a] rounded-xl shadow-sm dark:shadow-black/20 border border-gray-200 dark:border-gray-800 overflow-hidden"
              >
                <button
                  onClick={() => toggleExpanded(summary.id)}
                  className="w-full p-4 flex justify-between items-center hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors text-left"
                >
                  <div className="flex items-center gap-3">
                    <span className="inline-block px-2 py-1 text-xs rounded-full bg-emerald-100 dark:bg-emerald-900/50 text-emerald-700 dark:text-emerald-400 capitalize">
                      {summary.type}
                    </span>
                    <p className="text-sm text-gray-600 dark:text-gray-400">
                      {new Date(summary.startDate).toLocaleDateString()} - {new Date(summary.endDate).toLocaleDateString()}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <p className="text-xs text-gray-400 dark:text-gray-500">
                      Generated {new Date(summary.createdAt).toLocaleDateString()}
                    </p>
                    <svg
                      className={`w-5 h-5 text-gray-400 dark:text-gray-500 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </div>
                </button>
                {isExpanded && (
                  <div className="px-4 pb-4 border-t border-gray-100 dark:border-gray-800">
                    <div className="prose prose-sm dark:prose-invert max-w-none text-gray-700 dark:text-gray-300 pt-4">
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
