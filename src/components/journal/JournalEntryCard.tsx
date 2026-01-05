'use client'

import ReactMarkdown from 'react-markdown'

interface JournalEntryCardProps {
  id: string
  content: string
  mood?: string | null
  tags: string[]
  date: string
  onDelete?: (id: string) => void
}

const MOOD_CONFIG: Record<string, { emoji: string; color: string }> = {
  'Great': { emoji: '😊', color: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' },
  'Good': { emoji: '🙂', color: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' },
  'Okay': { emoji: '😐', color: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400' },
  'Struggling': { emoji: '😔', color: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400' },
  'Difficult': { emoji: '😢', color: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400' },
}

export function JournalEntryCard({ id, content, mood, tags, date, onDelete }: JournalEntryCardProps) {
  const formattedDate = new Date(date).toLocaleDateString('en-US', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })

  const formattedTime = new Date(date).toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true
  })

  const moodConfig = mood ? MOOD_CONFIG[mood] : null

  return (
    <article className="group relative bg-gradient-to-b from-amber-50/80 to-white dark:from-gray-900/80 dark:to-[#1a1a1a] rounded-xl shadow-md dark:shadow-black/30 border border-amber-100 dark:border-gray-800 overflow-hidden hover:shadow-lg dark:hover:shadow-black/40 transition-all duration-300">
      {/* Decorative corner fold */}
      <div className="absolute top-0 right-0 w-12 h-12 bg-gradient-to-bl from-amber-200/50 to-transparent dark:from-gray-700/30 pointer-events-none" />

      {/* Paper texture overlay */}
      <div className="absolute inset-0 opacity-[0.02] dark:opacity-[0.01] pointer-events-none"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E")`,
        }}
      />

      {/* Header with date and actions */}
      <div className="relative px-6 py-4 border-b border-amber-100/50 dark:border-gray-800/50 bg-amber-50/30 dark:bg-gray-900/30">
        <div className="flex justify-between items-start">
          <div className="space-y-1">
            <p className="text-sm font-medium text-amber-700 dark:text-amber-500/80 tracking-wide">
              {formattedDate}
            </p>
            <p className="text-xs text-amber-500/60 dark:text-gray-500">
              {formattedTime}
            </p>
          </div>

          <div className="flex items-center gap-3">
            {moodConfig && (
              <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-full ${moodConfig.color}`}>
                <span>{moodConfig.emoji}</span>
                <span className="font-medium">{mood}</span>
              </span>
            )}

            {onDelete && (
              <button
                onClick={() => onDelete(id)}
                className="opacity-0 group-hover:opacity-100 p-2 text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-all duration-200"
                title="Delete entry"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Content area with lined paper effect */}
      <div className="relative px-6 py-5">
        {/* Subtle line pattern */}
        <div
          className="absolute inset-0 pointer-events-none opacity-20 dark:opacity-5"
          style={{
            backgroundImage: 'repeating-linear-gradient(transparent, transparent 27px, #d4a574 28px)',
            backgroundPosition: '0 4px'
          }}
        />

        <div className="relative prose prose-amber dark:prose-invert prose-sm max-w-none
          prose-headings:font-serif prose-headings:text-amber-900 dark:prose-headings:text-amber-100
          prose-p:text-gray-700 dark:prose-p:text-gray-300 prose-p:leading-7 prose-p:font-serif
          prose-strong:text-amber-800 dark:prose-strong:text-amber-200
          prose-em:text-amber-700 dark:prose-em:text-amber-300
          prose-a:text-amber-600 dark:prose-a:text-violet-400 prose-a:no-underline hover:prose-a:underline
          prose-ul:my-2 prose-ol:my-2 prose-li:my-0.5
          prose-blockquote:border-amber-300 dark:prose-blockquote:border-gray-600
          prose-blockquote:bg-amber-50/50 dark:prose-blockquote:bg-gray-800/50
          prose-blockquote:rounded-r-lg prose-blockquote:py-1 prose-blockquote:pr-4
          prose-blockquote:text-amber-800 dark:prose-blockquote:text-gray-300
          prose-code:text-amber-700 dark:prose-code:text-violet-400
          prose-code:bg-amber-100/50 dark:prose-code:bg-gray-800
          prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:font-mono prose-code:text-sm
          [&>*:first-child]:mt-0 [&>*:last-child]:mb-0"
        >
          <ReactMarkdown>{content}</ReactMarkdown>
        </div>
      </div>

      {/* Tags section */}
      {tags.length > 0 && (
        <div className="relative px-6 py-4 border-t border-amber-100/50 dark:border-gray-800/50 bg-amber-50/20 dark:bg-gray-900/20">
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center px-3 py-1 text-xs rounded-full bg-gradient-to-r from-amber-100/80 to-orange-100/80 dark:from-gray-800 dark:to-gray-700 text-amber-700 dark:text-gray-300 border border-amber-200/50 dark:border-gray-600/50"
              >
                <span className="mr-1 opacity-60">#</span>
                {tag}
              </span>
            ))}
          </div>
        </div>
      )}
    </article>
  )
}
