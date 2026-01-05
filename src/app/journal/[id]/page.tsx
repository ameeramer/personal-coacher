'use client'

import { useSession } from 'next-auth/react'
import { useRouter, useParams } from 'next/navigation'
import { useEffect, useState, useRef, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeRaw from 'rehype-raw'
import { RichTextToolbar } from '@/components/journal/RichTextToolbar'

const MOOD_OPTIONS = [
  { value: 'Great', emoji: '😊', color: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800' },
  { value: 'Good', emoji: '🙂', color: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border-blue-200 dark:border-blue-800' },
  { value: 'Okay', emoji: '😐', color: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-800' },
  { value: 'Struggling', emoji: '😔', color: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-200 dark:border-orange-800' },
  { value: 'Difficult', emoji: '😢', color: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 border-red-200 dark:border-red-800' },
]

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
  const [content, setContent] = useState('')
  const [mood, setMood] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [tagInput, setTagInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [showMoodTags, setShowMoodTags] = useState(false)
  const [showPreview, setShowPreview] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }
  }, [status, router])

  const fetchEntry = async () => {
    try {
      const res = await fetch(`/api/journal/${entryId}`)
      if (res.ok) {
        const data = await res.json()
        setEntry(data)
        setContent(data.content)
        setMood(data.mood || '')
        setTags(data.tags || [])
      } else {
        router.push('/journal')
      }
    } catch (error) {
      console.error('Failed to fetch entry:', error)
      router.push('/journal')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (session && entryId) {
      fetchEntry()
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, entryId])

  // Auto-resize textarea
  useEffect(() => {
    const textarea = textareaRef.current
    if (textarea) {
      textarea.style.height = 'auto'
      textarea.style.height = `${Math.max(400, textarea.scrollHeight)}px`
    }
  }, [content])

  // Focus textarea on mount
  useEffect(() => {
    if (!loading && textareaRef.current) {
      textareaRef.current.focus()
    }
  }, [loading])

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Escape to go back
    if (e.key === 'Escape') {
      router.push('/journal')
      return
    }

    // Keyboard shortcuts for formatting
    if (e.ctrlKey || e.metaKey) {
      const textarea = textareaRef.current
      if (!textarea) return

      const start = textarea.selectionStart
      const end = textarea.selectionEnd
      const selectedText = content.substring(start, end)

      let format: { prefix: string; suffix: string } | null = null

      switch (e.key.toLowerCase()) {
        case 'b':
          format = { prefix: '**', suffix: '**' }
          break
        case 'i':
          format = { prefix: '_', suffix: '_' }
          break
        case 'k':
          format = { prefix: '[', suffix: '](url)' }
          break
        case 's':
          // Save shortcut
          e.preventDefault()
          handleSave()
          return
      }

      if (format) {
        e.preventDefault()
        const newContent =
          content.substring(0, start) +
          format.prefix +
          selectedText +
          format.suffix +
          content.substring(end)
        setContent(newContent)

        setTimeout(() => {
          const newPos = start + format!.prefix.length + selectedText.length + format!.suffix.length
          textarea.setSelectionRange(newPos, newPos)
        }, 0)
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [content, router])

  const handleAddTag = () => {
    if (tagInput.trim() && !tags.includes(tagInput.trim())) {
      setTags([...tags, tagInput.trim()])
      setTagInput('')
    }
  }

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter(tag => tag !== tagToRemove))
  }

  const handleSave = async () => {
    if (!content.trim()) return

    setSaving(true)
    try {
      const res = await fetch(`/api/journal/${entryId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, mood: mood || null, tags })
      })

      if (res.ok) {
        router.push('/journal')
      }
    } catch (error) {
      console.error('Failed to save entry:', error)
    } finally {
      setSaving(false)
    }
  }

  const handleBack = () => {
    router.push('/journal')
  }

  const selectedMood = MOOD_OPTIONS.find(m => m.value === mood)

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

  if (!session || !entry) {
    return null
  }

  const formattedDate = new Date(entry.date).toLocaleDateString('en-US', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })

  return (
    <div className="fixed inset-0 z-[60] bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f] flex flex-col">
      {/* Floating back button */}
      <button
        onClick={handleBack}
        className="fixed top-4 left-4 z-[70] p-2 rounded-full bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm border border-amber-200/50 dark:border-gray-700/50 text-amber-700 dark:text-gray-300 hover:bg-white dark:hover:bg-gray-800 transition-colors shadow-lg"
        title="Back to journal (Esc)"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>

      {/* Floating save button */}
      <button
        onClick={handleSave}
        disabled={!content.trim() || saving}
        className="fixed top-4 right-4 z-[70] px-4 py-2 rounded-full bg-gradient-to-r from-amber-500 to-orange-500 dark:from-violet-600 dark:to-purple-600 text-white font-medium shadow-lg hover:shadow-xl transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
        title="Save entry (Ctrl+S)"
      >
        {saving ? (
          <>
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
            <span>Saving</span>
          </>
        ) : (
          <>
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            <span>Save</span>
          </>
        )}
      </button>

      {/* Formatting toolbar - fixed at top center */}
      <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[70]">
        <div className="bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm rounded-full shadow-lg border border-amber-200/50 dark:border-gray-700/50">
          <RichTextToolbar
            textareaRef={textareaRef}
            onContentChange={setContent}
            content={content}
            showPreview={showPreview}
            onTogglePreview={() => setShowPreview(!showPreview)}
            minimal={true}
          />
        </div>
      </div>

      {/* Editor area - full screen */}
      <div className="flex-1 relative overflow-auto pt-16">
        {/* Subtle line pattern */}
        <div
          className="absolute inset-0 pointer-events-none opacity-20 dark:opacity-5"
          style={{
            backgroundImage: 'repeating-linear-gradient(transparent, transparent 31px, #d4a574 32px)',
            backgroundPosition: '0 0'
          }}
        />

        {showPreview ? (
          <div className="w-full h-full px-8 py-6 sm:px-16 md:px-24 lg:px-32 prose prose-amber dark:prose-invert prose-lg max-w-none
            prose-headings:font-serif prose-headings:text-amber-900 dark:prose-headings:text-amber-100
            prose-p:text-gray-700 dark:prose-p:text-gray-300 prose-p:leading-8 prose-p:font-serif
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
            {content ? (
              <ReactMarkdown rehypePlugins={[rehypeRaw]}>{content}</ReactMarkdown>
            ) : (
              <p className="text-amber-400/50 dark:text-gray-600 italic font-serif">Nothing to preview yet...</p>
            )}
          </div>
        ) : (
          <textarea
            ref={textareaRef}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
            className="w-full h-full px-8 py-6 sm:px-16 md:px-24 lg:px-32 bg-transparent text-gray-800 dark:text-gray-200 placeholder-amber-400/40 dark:placeholder-gray-600 resize-none focus:outline-none leading-8 font-serif text-xl"
            placeholder="Continue writing..."
            style={{ lineHeight: '32px', minHeight: '100%' }}
          />
        )}
      </div>

      {/* Bottom toolbar for mood/tags - minimal floating bar */}
      <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-[70]">
        <button
          type="button"
          onClick={() => setShowMoodTags(!showMoodTags)}
          className="px-4 py-2 rounded-full bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm border border-amber-200/50 dark:border-gray-700/50 text-amber-600 dark:text-gray-400 hover:bg-white dark:hover:bg-gray-800 transition-colors shadow-lg flex items-center gap-2 text-sm"
        >
          {selectedMood ? (
            <>
              <span>{selectedMood.emoji}</span>
              <span>{selectedMood.value}</span>
            </>
          ) : (
            <>
              <span>🎯</span>
              <span>Mood & Tags</span>
            </>
          )}
          {tags.length > 0 && (
            <span className="px-1.5 py-0.5 text-xs rounded-full bg-amber-100 dark:bg-gray-700 text-amber-700 dark:text-gray-300">
              {tags.length}
            </span>
          )}
        </button>
      </div>

      {/* Mood/Tags panel - slides up from bottom */}
      {showMoodTags && (
        <div className="fixed inset-x-0 bottom-0 z-[80] bg-white/95 dark:bg-gray-900/95 backdrop-blur-sm border-t border-amber-200/50 dark:border-gray-700/50 shadow-2xl">
          <div className="max-w-2xl mx-auto px-6 py-4 space-y-4">
            {/* Close button */}
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium text-amber-800 dark:text-gray-300">Add mood & tags</span>
              <button
                onClick={() => setShowMoodTags(false)}
                className="p-1 rounded-full text-amber-500 dark:text-gray-400 hover:bg-amber-100 dark:hover:bg-gray-800"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Mood selection */}
            <div className="flex flex-wrap gap-2">
              {MOOD_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setMood(mood === option.value ? '' : option.value)}
                  className={`px-3 py-1.5 rounded-full text-sm font-medium transition-all duration-200 border ${
                    mood === option.value
                      ? option.color + ' scale-105 shadow-md'
                      : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 border-gray-200 dark:border-gray-700 hover:border-amber-300 dark:hover:border-gray-600'
                  }`}
                >
                  <span className="mr-1">{option.emoji}</span>
                  {option.value}
                </button>
              ))}
            </div>

            {/* Tags */}
            <div className="flex gap-2">
              <input
                id="tags"
                type="text"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
                className="flex-1 px-4 py-2 border border-amber-200 dark:border-gray-700 rounded-full shadow-sm focus:ring-2 focus:ring-amber-400/50 dark:focus:ring-violet-500/50 focus:border-amber-400 dark:focus:border-violet-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 text-sm"
                placeholder="Add tag..."
              />
              <button
                type="button"
                onClick={handleAddTag}
                className="px-4 py-2 bg-amber-100 dark:bg-gray-700 text-amber-700 dark:text-gray-300 rounded-full hover:bg-amber-200 dark:hover:bg-gray-600 transition-colors text-sm font-medium"
              >
                Add
              </button>
            </div>
            {tags.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {tags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center px-3 py-1 rounded-full text-sm bg-gradient-to-r from-amber-100 to-orange-100 dark:from-violet-900/50 dark:to-purple-900/50 text-amber-800 dark:text-violet-300 border border-amber-200/50 dark:border-violet-700/50"
                  >
                    #{tag}
                    <button
                      type="button"
                      onClick={() => handleRemoveTag(tag)}
                      className="ml-1.5 text-amber-500 hover:text-amber-700 dark:text-violet-400 dark:hover:text-violet-300"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
