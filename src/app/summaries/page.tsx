'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

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
        <div className="text-gray-500">Loading...</div>
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
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Summaries</h1>

      <div className="grid gap-4 md:grid-cols-3 mb-8">
        {summaryTypes.map(({ type, label, description }) => (
          <button
            key={type}
            onClick={() => generateSummary(type)}
            disabled={generating !== null}
            className="p-6 bg-white rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow text-left disabled:opacity-50"
          >
            <h3 className="text-lg font-semibold text-gray-900">{label}</h3>
            <p className="text-sm text-gray-600 mt-1">{description}</p>
            {generating === type && (
              <p className="text-sm text-emerald-600 mt-2">Generating...</p>
            )}
          </button>
        ))}
      </div>

      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-900">Past Summaries</h2>
        {summaries.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-8 text-center">
            <p className="text-gray-500">No summaries yet. Generate one above!</p>
          </div>
        ) : (
          summaries.map((summary) => (
            <div
              key={summary.id}
              className="bg-white rounded-xl shadow-sm border border-gray-200 p-6"
            >
              <div className="flex justify-between items-start mb-3">
                <div>
                  <span className="inline-block px-2 py-1 text-xs rounded-full bg-emerald-100 text-emerald-700 capitalize">
                    {summary.type}
                  </span>
                  <p className="text-sm text-gray-500 mt-1">
                    {new Date(summary.startDate).toLocaleDateString()} - {new Date(summary.endDate).toLocaleDateString()}
                  </p>
                </div>
                <p className="text-xs text-gray-400">
                  Generated {new Date(summary.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div className="prose prose-sm max-w-none">
                <p className="text-gray-700 whitespace-pre-wrap">{summary.content}</p>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
