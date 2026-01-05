'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState, useRef, useCallback } from 'react'
import { RichTextToolbar } from '@/components/journal/RichTextToolbar'
import { MOOD_OPTIONS, PROSE_CLASSES_FULLPAGE, isValidUrl } from '@/lib/journal-constants'

interface FullPageJournalEditorProps {
  initialContent?: string
  initialMood?: string
  initialTags?: string[]
  onSave: (data: { content: string; mood: string | null; tags: string[] }) => Promise<boolean>
  isLoading?: boolean
  placeholder?: string
}

export function FullPageJournalEditor({
  initialContent = '',
  initialMood = '',
  initialTags = [],
  onSave,
  isLoading = false,
  placeholder = 'Start writing...'
}: FullPageJournalEditorProps) {
  const router = useRouter()

  const [content, setContent] = useState(initialContent)
  const [mood, setMood] = useState(initialMood)
  const [tags, setTags] = useState<string[]>(initialTags)
  const [tagInput, setTagInput] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showMoodTags, setShowMoodTags] = useState(false)
  const [isSourceView, setIsSourceView] = useState(false)
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
  const editorRef = useRef<HTMLDivElement>(null)
  const sourceRef = useRef<HTMLTextAreaElement>(null)

  // Update state when initial values change (e.g., when loading entry)
  useEffect(() => {
    setContent(initialContent)
    if (editorRef.current && !isSourceView) {
      editorRef.current.innerHTML = initialContent
    }
  }, [initialContent, isSourceView])

  useEffect(() => {
    setMood(initialMood)
  }, [initialMood])

  useEffect(() => {
    setTags(initialTags)
  }, [initialTags])

  // Focus editor on mount
  useEffect(() => {
    if (!isLoading && editorRef.current && !isSourceView) {
      editorRef.current.focus()
    }
  }, [isLoading, isSourceView])

  const handleInput = useCallback(() => {
    if (editorRef.current) {
      setContent(editorRef.current.innerHTML)
      setHasUnsavedChanges(true)
      setError(null)
    }
  }, [])

  const handleSourceInput = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setContent(e.target.value)
    setHasUnsavedChanges(true)
    setError(null)
  }, [])

  const handleContentChange = useCallback((newContent: string) => {
    setContent(newContent)
    setHasUnsavedChanges(true)
    setError(null)
  }, [])

  const handleAddTag = useCallback(() => {
    if (tagInput.trim() && !tags.includes(tagInput.trim())) {
      setTags([...tags, tagInput.trim()])
      setTagInput('')
      setHasUnsavedChanges(true)
    }
  }, [tagInput, tags])

  const handleRemoveTag = useCallback((tagToRemove: string) => {
    setTags(tags.filter(tag => tag !== tagToRemove))
    setHasUnsavedChanges(true)
  }, [tags])

  const handleSave = useCallback(async () => {
    if (!content.trim() || content === '<br>' || content === '<div><br></div>') return

    setSaving(true)
    setError(null)
    try {
      const success = await onSave({ content, mood: mood || null, tags })
      if (success) {
        setHasUnsavedChanges(false)
        router.push('/journal')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save entry. Please try again.')
    } finally {
      setSaving(false)
    }
  }, [content, mood, tags, onSave, router])

  const handleBack = useCallback(() => {
    if (hasUnsavedChanges) {
      const confirmed = window.confirm('You have unsaved changes. Are you sure you want to leave?')
      if (!confirmed) return
    }
    router.push('/journal')
  }, [hasUnsavedChanges, router])

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
    // Escape to go back
    if (e.key === 'Escape') {
      e.preventDefault()
      handleBack()
      return
    }

    // Keyboard shortcuts for formatting
    if (e.ctrlKey || e.metaKey) {
      switch (e.key.toLowerCase()) {
        case 'b':
          e.preventDefault()
          document.execCommand('bold', false)
          handleInput()
          break
        case 'i':
          e.preventDefault()
          document.execCommand('italic', false)
          handleInput()
          break
        case 'k':
          e.preventDefault()
          const url = prompt('Enter URL:')
          if (url) {
            if (!isValidUrl(url)) {
              alert('Invalid URL. Please enter a valid http, https, mailto, or tel URL.')
              return
            }
            document.execCommand('createLink', false, url)
            handleInput()
          }
          break
        case 's':
          // Save shortcut
          e.preventDefault()
          handleSave()
          return
      }
    }
  }, [handleBack, handleInput, handleSave])

  const toggleSourceView = useCallback(() => {
    setIsSourceView(prev => {
      const newIsSourceView = !prev
      // When switching FROM source view TO WYSIWYG, sync the content
      if (prev && !newIsSourceView && editorRef.current) {
        // Use setTimeout to ensure state has updated
        setTimeout(() => {
          if (editorRef.current) {
            editorRef.current.innerHTML = content
          }
        }, 0)
      }
      return newIsSourceView
    })
  }, [content])

  const selectedMood = MOOD_OPTIONS.find(m => m.value === mood)

  // Check if content is empty (accounting for HTML)
  const isContentEmpty = !content.trim() || content === '<br>' || content === '<div><br></div>'

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-2 border-amber-500 dark:border-violet-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-amber-600 dark:text-gray-400 font-medium">Loading...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 z-[60] bg-gradient-to-b from-amber-50/50 to-white dark:from-gray-950 dark:to-[#0f0f0f] flex flex-col">
      {/* Floating back button */}
      <button
        onClick={handleBack}
        className="fixed top-4 left-4 z-[70] p-2 rounded-full bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm border border-amber-200/50 dark:border-gray-700/50 text-amber-700 dark:text-gray-300 hover:bg-white dark:hover:bg-gray-800 transition-colors shadow-lg"
        title="Back to journal (Esc)"
        aria-label="Back to journal"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>

      {/* Floating save button */}
      <button
        onClick={handleSave}
        disabled={isContentEmpty || saving}
        className="fixed top-4 right-4 z-[70] px-4 py-2 rounded-full bg-gradient-to-r from-amber-500 to-orange-500 dark:from-violet-600 dark:to-purple-600 text-white font-medium shadow-lg hover:shadow-xl transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
        title="Save entry (Ctrl+S)"
        aria-label="Save entry"
      >
        {saving ? (
          <>
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
            <span>Saving</span>
          </>
        ) : (
          <>
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            <span>Save</span>
          </>
        )}
      </button>

      {/* Error toast */}
      {error && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[80] px-4 py-3 rounded-lg bg-red-50 dark:bg-red-900/80 border border-red-200 dark:border-red-800 shadow-lg" role="alert">
          <p className="text-sm text-red-600 dark:text-red-200 flex items-center gap-2">
            <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {error}
            <button
              onClick={() => setError(null)}
              className="ml-2 text-red-500 hover:text-red-700 dark:text-red-300 dark:hover:text-red-100"
              aria-label="Dismiss error"
            >
              ×
            </button>
          </p>
        </div>
      )}

      {/* Formatting toolbar - fixed below the top buttons */}
      <div className="fixed top-16 left-1/2 -translate-x-1/2 z-[70]">
        <div className="bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm rounded-full shadow-lg border border-amber-200/50 dark:border-gray-700/50">
          <RichTextToolbar
            editorRef={editorRef}
            onContentChange={handleContentChange}
            minimal={true}
            showSourceView={true}
            onToggleSourceView={toggleSourceView}
            isSourceView={isSourceView}
            sourceRef={sourceRef}
          />
        </div>
      </div>

      {/* Editor area - full screen */}
      <div className="flex-1 relative overflow-auto pt-28">
        {/* Subtle line pattern */}
        <div
          className="absolute inset-0 pointer-events-none opacity-20 dark:opacity-5"
          style={{
            backgroundImage: 'repeating-linear-gradient(transparent, transparent 31px, #d4a574 32px)',
            backgroundPosition: '0 0'
          }}
        />

        {isSourceView ? (
          /* Source code view */
          <textarea
            ref={sourceRef}
            value={content}
            onChange={handleSourceInput}
            className="w-full h-full px-8 py-6 sm:px-16 md:px-24 lg:px-32 bg-transparent text-gray-800 dark:text-gray-200 resize-none focus:outline-none leading-8 font-mono text-sm"
            style={{ lineHeight: '32px', minHeight: '100%' }}
            placeholder="HTML source code..."
            aria-label="Source code editor"
          />
        ) : (
          /* WYSIWYG view */
          <div
            ref={editorRef}
            contentEditable
            onInput={handleInput}
            onKeyDown={handleKeyDown}
            role="textbox"
            aria-label="Journal entry editor"
            aria-multiline="true"
            className={`w-full h-full px-8 py-6 sm:px-16 md:px-24 lg:px-32 bg-transparent text-gray-800 dark:text-gray-200 resize-none focus:outline-none leading-8 font-serif text-xl ${PROSE_CLASSES_FULLPAGE}
              [&:empty]:before:content-['${placeholder.replace(/'/g, "\\'")}'] [&:empty]:before:text-amber-400/40 dark:[&:empty]:before:text-gray-600 [&:empty]:before:italic [&:empty]:before:font-serif`}
            style={{ lineHeight: '32px', minHeight: '100%' }}
            data-placeholder={placeholder}
          />
        )}
      </div>

      {/* Bottom toolbar for mood/tags - minimal floating bar */}
      <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-[70]">
        <button
          type="button"
          onClick={() => setShowMoodTags(!showMoodTags)}
          aria-expanded={showMoodTags}
          aria-controls="mood-tags-panel"
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
        <div
          id="mood-tags-panel"
          className="fixed inset-x-0 bottom-0 z-[80] bg-white/95 dark:bg-gray-900/95 backdrop-blur-sm border-t border-amber-200/50 dark:border-gray-700/50 shadow-2xl"
        >
          <div className="max-w-2xl mx-auto px-6 py-4 space-y-4">
            {/* Close button */}
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium text-amber-800 dark:text-gray-300">Add mood & tags</span>
              <button
                onClick={() => setShowMoodTags(false)}
                className="p-1 rounded-full text-amber-500 dark:text-gray-400 hover:bg-amber-100 dark:hover:bg-gray-800"
                aria-label="Close mood and tags panel"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Mood selection */}
            <div className="flex flex-wrap gap-2" role="group" aria-label="Mood selection">
              {MOOD_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    setMood(mood === option.value ? '' : option.value)
                    setHasUnsavedChanges(true)
                  }}
                  aria-pressed={mood === option.value}
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
                aria-label="Add a tag"
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
              <div className="flex flex-wrap gap-2" role="list" aria-label="Selected tags">
                {tags.map((tag) => (
                  <span
                    key={tag}
                    role="listitem"
                    className="inline-flex items-center px-3 py-1 rounded-full text-sm bg-gradient-to-r from-amber-100 to-orange-100 dark:from-violet-900/50 dark:to-purple-900/50 text-amber-800 dark:text-violet-300 border border-amber-200/50 dark:border-violet-700/50"
                  >
                    #{tag}
                    <button
                      type="button"
                      onClick={() => handleRemoveTag(tag)}
                      className="ml-1.5 text-amber-500 hover:text-amber-700 dark:text-violet-400 dark:hover:text-violet-300"
                      aria-label={`Remove tag ${tag}`}
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
