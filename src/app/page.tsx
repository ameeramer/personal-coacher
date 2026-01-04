'use client'

import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import Link from 'next/link'

interface JournalEntry {
  id: string
  content: string
  mood: string | null
  date: string
}

const PenIcon = () => (
  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" />
  </svg>
)

const ChatIcon = () => (
  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 8.511c.884.284 1.5 1.128 1.5 2.097v4.286c0 1.136-.847 2.1-1.98 2.193-.34.027-.68.052-1.02.072v3.091l-3-3c-1.354 0-2.694-.055-4.02-.163a2.115 2.115 0 01-.825-.242m9.345-8.334a2.126 2.126 0 00-.476-.095 48.64 48.64 0 00-8.048 0c-1.131.094-1.976 1.057-1.976 2.192v4.286c0 .837.46 1.58 1.155 1.951m9.345-8.334V6.637c0-1.621-1.152-3.026-2.76-3.235A48.455 48.455 0 0011.25 3c-2.115 0-4.198.137-6.24.402-1.608.209-2.76 1.614-2.76 3.235v6.226c0 1.621 1.152 3.026 2.76 3.235.577.075 1.157.14 1.74.194V21l4.155-4.155" />
  </svg>
)

const SparklesIcon = () => (
  <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
  </svg>
)

const BookIcon = () => (
  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25" />
  </svg>
)

export default function Dashboard() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const [recentEntries, setRecentEntries] = useState<JournalEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  useEffect(() => {
    if (session) {
      fetchRecentEntries()
    }
  }, [session])

  const fetchRecentEntries = async () => {
    try {
      const res = await fetch('/api/journal')
      const data = await res.json()
      setRecentEntries(data.slice(0, 3))
    } catch (error) {
      console.error('Failed to fetch entries:', error)
    } finally {
      setLoading(false)
    }
  }

  if (status === 'loading' || loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="animate-pulse flex flex-col items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-gradient-to-r from-indigo-500 to-purple-500 animate-spin" style={{ animationDuration: '3s' }} />
          <p className="text-gray-400">Loading...</p>
        </div>
      </div>
    )
  }

  if (!session) {
    return null
  }

  const today = new Date()
  const greeting = today.getHours() < 12 ? 'Good morning' : today.getHours() < 18 ? 'Good afternoon' : 'Good evening'
  const firstName = session.user?.name?.split(' ')[0] || 'there'

  return (
    <div className="min-h-[calc(100vh-64px)] bg-gradient-to-br from-slate-50 via-white to-emerald-50/50 relative overflow-hidden">
      {/* Background decorative elements */}
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-gradient-to-br from-emerald-100/40 to-green-100/40 rounded-full blur-3xl -translate-y-1/2 translate-x-1/4 pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-gradient-to-tr from-teal-100/30 to-cyan-100/30 rounded-full blur-3xl translate-y-1/2 -translate-x-1/4 pointer-events-none" />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 relative">
        {/* Hero Section */}
        <div className="mb-12">
          <div className="flex items-center gap-4 mb-4">
            <div className="p-2.5 rounded-2xl bg-gradient-to-br from-emerald-500 via-green-500 to-teal-500 text-white shadow-xl shadow-emerald-500/25 animate-pulse" style={{ animationDuration: '3s' }}>
              <SparklesIcon />
            </div>
            <span className="text-sm font-semibold text-emerald-600 bg-gradient-to-r from-emerald-50 to-green-50 px-4 py-1.5 rounded-full border border-emerald-100/50 shadow-sm">
              {today.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
            </span>
          </div>
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-gray-900 tracking-tight leading-tight">
            {greeting}, <span className="bg-gradient-to-r from-emerald-600 via-green-600 to-teal-600 bg-clip-text text-transparent">{firstName}</span>!
          </h1>
          <p className="text-lg sm:text-xl text-gray-500 mt-4 max-w-lg">How are you feeling today? Take a moment to reflect and grow.</p>
        </div>

        {/* Action Cards */}
        <div className="grid gap-8 md:grid-cols-2 mb-12">
          <Link
            href="/journal"
            className="group relative overflow-hidden rounded-3xl bg-white p-8 shadow-lg shadow-gray-200/50 border border-gray-100/80 hover:shadow-2xl hover:shadow-lime-200/50 transition-all duration-500 hover:-translate-y-2"
          >
            <div className="absolute top-0 right-0 w-40 h-40 bg-gradient-to-br from-lime-200/60 to-green-200/60 rounded-full -translate-y-20 translate-x-20 group-hover:scale-[2] transition-transform duration-700" />
            <div className="absolute bottom-0 left-0 w-24 h-24 bg-gradient-to-tr from-emerald-100/40 to-lime-100/40 rounded-full translate-y-12 -translate-x-12 group-hover:scale-150 transition-transform duration-700" />
            <div className="relative">
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-lime-400 via-green-400 to-emerald-400 flex items-center justify-center text-white mb-6 shadow-xl shadow-green-300/50 group-hover:scale-110 group-hover:rotate-3 transition-all duration-500">
                <PenIcon />
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-3">Write in Journal</h2>
              <p className="text-gray-500 leading-relaxed">Capture your thoughts, track your mood, and reflect on your day.</p>
              <div className="mt-6 flex items-center text-green-600 font-semibold group-hover:text-emerald-600 transition-colors">
                Start writing
                <svg className="w-5 h-5 ml-2 group-hover:translate-x-3 transition-transform duration-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M17 8l4 4m0 0l-4 4m4-4H3" />
                </svg>
              </div>
            </div>
          </Link>

          <Link
            href="/coach"
            className="group relative overflow-hidden rounded-3xl bg-gradient-to-br from-emerald-600 via-green-600 to-teal-600 p-8 shadow-xl shadow-emerald-300/40 hover:shadow-2xl hover:shadow-green-400/50 transition-all duration-500 hover:-translate-y-2"
          >
            <div className="absolute top-0 right-0 w-48 h-48 bg-white/10 rounded-full -translate-y-24 translate-x-24 group-hover:scale-[2] transition-transform duration-700" />
            <div className="absolute bottom-0 left-0 w-32 h-32 bg-white/5 rounded-full translate-y-16 -translate-x-16 group-hover:scale-150 transition-transform duration-700" />
            <div className="absolute top-1/2 left-1/2 w-64 h-64 bg-gradient-to-br from-white/5 to-transparent rounded-full -translate-x-1/2 -translate-y-1/2 group-hover:scale-125 transition-transform duration-700" />
            <div className="relative">
              <div className="w-16 h-16 rounded-2xl bg-white/20 backdrop-blur-sm flex items-center justify-center text-white mb-6 ring-1 ring-white/30 group-hover:scale-110 group-hover:-rotate-3 transition-all duration-500">
                <ChatIcon />
              </div>
              <h2 className="text-2xl font-bold text-white mb-3">Talk to Coach</h2>
              <p className="text-emerald-100 leading-relaxed">Get personalized guidance, insights, and support for your journey.</p>
              <div className="mt-6 flex items-center text-white font-semibold">
                Start conversation
                <svg className="w-5 h-5 ml-2 group-hover:translate-x-3 transition-transform duration-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M17 8l4 4m0 0l-4 4m4-4H3" />
                </svg>
              </div>
            </div>
          </Link>
        </div>

        {/* Recent Entries */}
        <div className="bg-white/80 backdrop-blur-sm rounded-3xl shadow-xl shadow-gray-200/40 border border-gray-100/80 overflow-hidden">
          <div className="flex justify-between items-center px-8 py-6 border-b border-gray-100/80 bg-gradient-to-r from-slate-50/80 to-white/80">
            <div className="flex items-center gap-4">
              <div className="p-2.5 rounded-xl bg-gradient-to-br from-emerald-100 to-green-100 text-emerald-600 shadow-sm">
                <BookIcon />
              </div>
              <h2 className="text-xl font-bold text-gray-900">Recent Entries</h2>
            </div>
            <Link href="/journal" className="group text-emerald-600 hover:text-emerald-700 text-sm font-semibold flex items-center gap-2 px-4 py-2 rounded-xl hover:bg-emerald-50 transition-all duration-300">
              View all
              <svg className="w-4 h-4 group-hover:translate-x-1 transition-transform duration-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 5l7 7-7 7" />
              </svg>
            </Link>
          </div>

          <div className="p-8">
            {recentEntries.length === 0 ? (
              <div className="text-center py-16">
                <div className="w-24 h-24 mx-auto mb-8 rounded-3xl bg-gradient-to-br from-emerald-50 via-green-50 to-teal-50 flex items-center justify-center shadow-inner">
                  <svg className="w-12 h-12 text-emerald-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                  </svg>
                </div>
                <h3 className="text-xl font-bold text-gray-900 mb-3">No entries yet</h3>
                <p className="text-gray-500 mb-8 max-w-md mx-auto leading-relaxed">Start your journaling journey by writing your first entry. It only takes a few minutes to begin.</p>
                <Link
                  href="/journal"
                  className="inline-flex items-center gap-3 px-8 py-4 bg-gradient-to-r from-emerald-600 via-green-600 to-teal-600 text-white rounded-2xl font-semibold hover:from-emerald-700 hover:via-green-700 hover:to-teal-700 shadow-xl shadow-emerald-300/40 hover:shadow-2xl hover:shadow-green-400/50 transition-all duration-500 hover:-translate-y-1"
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125" />
                  </svg>
                  Write your first entry
                </Link>
              </div>
            ) : (
              <div className="space-y-4">
                {recentEntries.map((entry, index) => (
                  <div
                    key={entry.id}
                    className="group p-6 rounded-2xl border border-gray-100 hover:border-emerald-200 bg-gradient-to-r from-white to-gray-50/50 hover:from-emerald-50/50 hover:to-green-50/50 hover:shadow-lg hover:shadow-emerald-100/50 transition-all duration-500"
                    style={{ animationDelay: `${index * 100}ms` }}
                  >
                    <div className="flex justify-between items-start mb-3">
                      <p className="text-sm font-semibold text-gray-400">
                        {new Date(entry.date).toLocaleDateString('en-US', {
                          weekday: 'short',
                          month: 'short',
                          day: 'numeric'
                        })}
                      </p>
                      {entry.mood && (
                        <span className="text-xs px-4 py-1.5 rounded-full bg-gradient-to-r from-emerald-100 to-green-100 text-emerald-700 font-semibold shadow-sm">
                          {entry.mood}
                        </span>
                      )}
                    </div>
                    <p className="text-gray-600 line-clamp-2 group-hover:text-gray-900 transition-colors leading-relaxed">{entry.content}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
