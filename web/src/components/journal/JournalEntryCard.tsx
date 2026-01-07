'use client'

import { useState, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import ReactMarkdown from 'react-markdown'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import { MOOD_CONFIG, PROSE_CLASSES } from '@/lib/journal-constants'

interface JournalEntryCardProps {
  id: string
  content: string
  mood?: string | null
  tags: string[]
  date: string
  onDelete?: (id: string) => void
}

// Custom sanitization schema that allows style attributes for color support
const sanitizeSchema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    span: [...(defaultSchema.attributes?.span || []), ['style', /^color:\s*#[0-9a-fA-F]{3,6}$/]],
    '*': [...(defaultSchema.attributes?.['*'] || []), 'className', 'class']
  },
  tagNames: [
    ...(defaultSchema.tagNames || []),
    'span'
  ]
}

export function JournalEntryCard({ id, content, mood, tags, date, onDelete }: JournalEntryCardProps) {
  const router = useRouter()
  const [isExpanded, setIsExpanded] = useState(false)
  const [showAllTags, setShowAllTags] = useState(false)
  const [hasOverflow, setHasOverflow] = useState(false)
  const tagsContainerRef = useRef<HTMLDivElement>(null)

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation()
    router.push(`/journal/${id}`)
  }

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation()
    if (onDelete) {
      onDelete(id)
    }
  }

  const toggleExpand = () => {
    setIsExpanded(!isExpanded)
  }

  const handleToggleTags = (e: React.MouseEvent) => {
    e.stopPropagation()
    setShowAllTags(!showAllTags)
  }

  // Check if tags overflow the container
  useEffect(() => {
    const checkOverflow = () => {
      if (tagsContainerRef.current) {
        const container = tagsContainerRef.current
        setHasOverflow(container.scrollWidth > container.clientWidth)
      }
    }
    checkOverflow()
    window.addEventListener('resize', checkOverflow)
    return () => window.removeEventListener('resize', checkOverflow)
  }, [tags, mood])

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

  // Extract plain text preview from content (strip HTML/markdown)
  const getPlainTextPreview = (htmlContent: string) => {
    // Remove HTML tags and get plain text
    const plainText = htmlContent.replace(/<[^>]*>/g, '').replace(/[#*_`~]/g, '').trim()
    return plainText
  }

  const plainTextPreview = getPlainTextPreview(content)

  return (
    <article
      className="group relative bg-gradient-to-b from-amber-50/80 to-white dark:from-gray-900/80 dark:to-[#1a1a1a] rounded-xl shadow-md dark:shadow-black/30 border border-amber-100 dark:border-gray-800 overflow-hidden hover:shadow-lg dark:hover:shadow-black/40 transition-all duration-300 cursor-pointer"
      onClick={toggleExpand}
    >
      {/* Decorative corner fold */}
      <div className="absolute top-0 right-0 w-12 h-12 bg-gradient-to-bl from-amber-200/50 to-transparent dark:from-gray-700/30 pointer-events-none" />

      {/* Paper texture overlay */}
      <div className="absolute inset-0 opacity-[0.02] dark:opacity-[0.01] pointer-events-none"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E")`,
        }}
      />

      {/* Header with date, mood/tags below, and actions */}
      <div className="relative px-4 py-3">
        <div className="flex justify-between items-start">
          <div className="flex-1 min-w-0">
            {/* Date and time row */}
            <div className="flex items-center gap-2 mb-1.5">
              <p className="text-sm font-medium text-amber-700 dark:text-amber-500/80 tracking-wide">
                {formattedDate}
              </p>
              <span className="text-amber-300 dark:text-gray-600">•</span>
              <p className="text-xs text-amber-500/60 dark:text-gray-500">
                {formattedTime}
              </p>
            </div>

            {/* Mood and tags row - single line with overflow */}
            <div className="flex items-center gap-2">
              <div
                ref={tagsContainerRef}
                className={`flex items-center gap-2 min-w-0 ${showAllTags ? 'flex-wrap' : 'overflow-hidden'}`}
              >
                {moodConfig && (
                  <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full shrink-0 ${moodConfig.color}`}>
                    <span>{moodConfig.emoji}</span>
                    <span className="font-medium">{mood}</span>
                  </span>
                )}
                {tags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center px-2 py-0.5 text-xs rounded-full bg-gradient-to-r from-amber-100/80 to-orange-100/80 dark:from-gray-800 dark:to-gray-700 text-amber-700 dark:text-gray-300 border border-amber-200/50 dark:border-gray-600/50 shrink-0"
                  >
                    <span className="mr-0.5 opacity-60">#</span>
                    {tag}
                  </span>
                ))}
              </div>
              {hasOverflow && !showAllTags && (
                <button
                  onClick={handleToggleTags}
                  className="inline-flex items-center justify-center w-6 h-5 text-xs rounded-full bg-amber-100 dark:bg-gray-700 text-amber-600 dark:text-gray-400 hover:bg-amber-200 dark:hover:bg-gray-600 transition-colors shrink-0"
                  aria-label="Show more tags"
                >
                  •••
                </button>
              )}
              {showAllTags && (
                <button
                  onClick={handleToggleTags}
                  className="inline-flex items-center justify-center px-2 py-0.5 text-xs rounded-full bg-amber-100 dark:bg-gray-700 text-amber-600 dark:text-gray-400 hover:bg-amber-200 dark:hover:bg-gray-600 transition-colors shrink-0"
                  aria-label="Show less tags"
                >
                  less
                </button>
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-1 ml-2">
            {/* Expand/collapse indicator */}
            <div className="p-1.5 text-amber-400 dark:text-gray-500">
              <svg
                className={`w-4 h-4 transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
            <button
              onClick={handleEdit}
              className="opacity-0 group-hover:opacity-100 p-1.5 text-gray-400 dark:text-gray-500 hover:text-amber-600 dark:hover:text-amber-400 hover:bg-amber-50 dark:hover:bg-amber-900/20 rounded-lg transition-all duration-200"
              title="Edit entry"
              aria-label="Edit journal entry"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
              </svg>
            </button>
            {onDelete && (
              <button
                onClick={handleDelete}
                className="opacity-0 group-hover:opacity-100 p-1.5 text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-all duration-200"
                title="Delete entry"
                aria-label="Delete journal entry"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            )}
          </div>
        </div>

        {/* Compact preview - one line of text */}
        {!isExpanded && (
          <p className="mt-2 text-sm text-gray-600 dark:text-gray-400 truncate">
            {plainTextPreview || 'No content'}
          </p>
        )}
      </div>

      {/* Expanded content area */}
      {isExpanded && (
        <div className="relative px-4 py-4 border-t border-amber-100/50 dark:border-gray-800/50">
          {/* Subtle line pattern */}
          <div
            className="absolute inset-0 pointer-events-none opacity-20 dark:opacity-5"
            style={{
              backgroundImage: 'repeating-linear-gradient(transparent, transparent 27px, #d4a574 28px)',
              backgroundPosition: '0 4px'
            }}
          />

          <div className={`relative ${PROSE_CLASSES} [&>*:first-child]:mt-0 [&>*:last-child]:mb-0`}>
            <ReactMarkdown rehypePlugins={[rehypeRaw, [rehypeSanitize, sanitizeSchema]]}>{content}</ReactMarkdown>
          </div>
        </div>
      )}
    </article>
  )
}
