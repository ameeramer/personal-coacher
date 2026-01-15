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
      className="group relative bg-[var(--glass-bg)] backdrop-blur-[20px] rounded-2xl border border-[var(--glass-border)] shadow-[var(--glass-shadow)] overflow-hidden hover:border-[var(--accent-primary)]/20 transition-all duration-300 cursor-pointer"
      onClick={toggleExpand}
    >
      {/* Header with date, mood/tags below, and actions */}
      <div className="relative px-6 py-5">
        <div className="flex justify-between items-start">
          <div className="flex-1 min-w-0">
            {/* Date and time row - iOS smaller metadata style */}
            <div className="flex items-center gap-2 mb-2">
              <p className="text-xs font-medium text-[var(--muted-foreground)] tracking-wide">
                {formattedDate}
              </p>
              <span className="text-[var(--muted-foreground)]/50">•</span>
              <p className="text-xs text-[var(--muted-foreground)]/70">
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
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 text-xs rounded-full shrink-0 bg-[var(--accent-primary)]/10 text-[var(--accent-primary)]">
                    <span>{moodConfig.emoji}</span>
                    <span className="font-medium">{mood}</span>
                  </span>
                )}
                {tags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center px-3 py-1 text-xs rounded-full bg-[var(--foreground)]/5 text-[var(--muted)] border border-[var(--glass-border)] shrink-0"
                  >
                    <span className="mr-0.5 opacity-60">#</span>
                    {tag}
                  </span>
                ))}
              </div>
              {hasOverflow && !showAllTags && (
                <button
                  onClick={handleToggleTags}
                  className="inline-flex items-center justify-center w-7 h-6 text-xs rounded-full bg-[var(--foreground)]/5 text-[var(--muted)] hover:bg-[var(--foreground)]/10 transition-colors shrink-0"
                  aria-label="Show more tags"
                >
                  •••
                </button>
              )}
              {showAllTags && (
                <button
                  onClick={handleToggleTags}
                  className="inline-flex items-center justify-center px-3 py-1 text-xs rounded-full bg-[var(--foreground)]/5 text-[var(--muted)] hover:bg-[var(--foreground)]/10 transition-colors shrink-0"
                  aria-label="Show less tags"
                >
                  less
                </button>
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-1 ml-3">
            {/* Expand/collapse indicator */}
            <div className="p-2 text-[var(--muted)]">
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
              className="opacity-0 group-hover:opacity-100 p-2 text-[var(--muted)] hover:text-[var(--accent-primary)] hover:bg-[var(--accent-primary)]/10 rounded-xl transition-all duration-200"
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
                className="opacity-0 group-hover:opacity-100 p-2 text-[var(--muted)] hover:text-red-500 hover:bg-red-500/10 rounded-xl transition-all duration-200"
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
          <p className="mt-3 text-sm text-[var(--foreground)] truncate leading-relaxed">
            {plainTextPreview || 'No content'}
          </p>
        )}
      </div>

      {/* Expanded content area */}
      {isExpanded && (
        <div className="relative px-6 py-5 border-t border-[var(--glass-border)]">
          <div className={`relative ${PROSE_CLASSES} [&>*:first-child]:mt-0 [&>*:last-child]:mb-0`}>
            <ReactMarkdown rehypePlugins={[rehypeRaw, [rehypeSanitize, sanitizeSchema]]}>{content}</ReactMarkdown>
          </div>
        </div>
      )}
    </article>
  )
}
