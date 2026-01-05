'use client'

import { useState, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { RichTextToolbar } from './RichTextToolbar'

interface JournalEditorProps {
  onSave: (entry: { content: string; mood: string; tags: string[] }) => Promise<void>
  initialContent?: string
  initialMood?: string
  initialTags?: string[]
}

const MOOD_OPTIONS = [
  { value: 'Great', emoji: '😊', color: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800' },
  { value: 'Good', emoji: '🙂', color: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border-blue-200 dark:border-blue-800' },
  { value: 'Okay', emoji: '😐', color: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-800' },
  { value: 'Struggling', emoji: '😔', color: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-200 dark:border-orange-800' },
  { value: 'Difficult', emoji: '😢', color: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 border-red-200 dark:border-red-800' },
]

export function JournalEditor({
  onSave,
  initialContent = '',
  initialMood = '',
  initialTags = []
}: JournalEditorProps) {
  const router = useRouter()
  const [content, setContent] = useState(initialContent)
  const [mood, setMood] = useState(initialMood)
  const [tagInput, setTagInput] = useState('')
  const [tags, setTags] = useState<string[]>(initialTags)
  const [saving, setSaving] = useState(false)
  const [showMoodTags, setShowMoodTags] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Auto-resize textarea
  useEffect(() => {
    const textarea = textareaRef.current
    if (textarea) {
      textarea.style.height = 'auto'
      textarea.style.height = `${Math.max(200, textarea.scrollHeight)}px`
    }
  }, [content])

  // Navigate to fullscreen editor page
  const handleExpandClick = () => {
    router.push('/journal/new')
  }

  const handleAddTag = () => {
    if (tagInput.trim() && !tags.includes(tagInput.trim())) {
      setTags([...tags, tagInput.trim()])
      setTagInput('')
    }
  }

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter(tag => tag !== tagToRemove))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!content.trim()) return

    setSaving(true)
    try {
      await onSave({ content, mood, tags })
      setContent('')
      setMood('')
      setTags([])
      setShowMoodTags(false)
    } finally {
      setSaving(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
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
  }

  const currentDate = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })

  const selectedMood = MOOD_OPTIONS.find(m => m.value === mood)

  return (
    <form onSubmit={handleSubmit} className="relative">
      {/* Paper-like container */}
      <div className="bg-gradient-to-b from-amber-50 to-white dark:from-gray-900 dark:to-[#1a1a1a] rounded-xl shadow-lg dark:shadow-black/30 border border-amber-100 dark:border-gray-800 overflow-hidden">
        {/* Date header */}
        <div className="px-6 py-4 border-b border-amber-100/50 dark:border-gray-800/50 bg-amber-50/30 dark:bg-gray-900/30">
          <p className="text-sm font-medium text-amber-700 dark:text-amber-500/70 tracking-wide">
            {currentDate}
          </p>
        </div>

        {/* Formatting toolbar */}
        <RichTextToolbar
          textareaRef={textareaRef}
          onContentChange={setContent}
          content={content}
          onToggleFullscreen={handleExpandClick}
        />

        {/* Editor area with lined paper effect */}
        <div className="relative">
          {/* Subtle line pattern */}
          <div
            className="absolute inset-0 pointer-events-none opacity-30 dark:opacity-10"
            style={{
              backgroundImage: 'repeating-linear-gradient(transparent, transparent 27px, #d4a574 28px)',
              backgroundPosition: '0 12px'
            }}
          />

          <textarea
            ref={textareaRef}
            id="content"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
            className="w-full px-6 py-4 bg-transparent text-gray-800 dark:text-gray-200 placeholder-amber-400/50 dark:placeholder-gray-600 resize-none focus:outline-none leading-7 font-serif text-lg min-h-[200px]"
            placeholder="What's on your mind today? Start writing..."
            style={{ lineHeight: '28px' }}
          />
        </div>

        {/* Mood and Tags section */}
        <div className="border-t border-amber-100/50 dark:border-gray-800/50">
          {/* Toggle button for mood/tags */}
          <button
            type="button"
            onClick={() => setShowMoodTags(!showMoodTags)}
            className="w-full px-6 py-3 flex items-center justify-between text-sm text-amber-600 dark:text-gray-400 hover:bg-amber-50/50 dark:hover:bg-gray-800/50 transition-colors"
          >
            <span className="flex items-center gap-2">
              {selectedMood ? (
                <>
                  <span>{selectedMood.emoji}</span>
                  <span>Feeling {selectedMood.value.toLowerCase()}</span>
                </>
              ) : (
                <>
                  <span>🎯</span>
                  <span>Add mood & tags</span>
                </>
              )}
              {tags.length > 0 && (
                <span className="ml-2 px-2 py-0.5 text-xs rounded-full bg-amber-100 dark:bg-gray-700 text-amber-700 dark:text-gray-300">
                  {tags.length} tag{tags.length > 1 ? 's' : ''}
                </span>
              )}
            </span>
            <svg
              className={`w-4 h-4 transition-transform ${showMoodTags ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {/* Expandable mood and tags panel */}
          <div className={`overflow-hidden transition-all duration-300 ${showMoodTags ? 'max-h-96' : 'max-h-0'}`}>
            <div className="px-6 py-4 space-y-4 bg-amber-50/30 dark:bg-gray-900/30">
              {/* Mood selection */}
              <div>
                <label className="block text-sm font-medium text-amber-800 dark:text-gray-300 mb-3">
                  How are you feeling?
                </label>
                <div className="flex flex-wrap gap-2">
                  {MOOD_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => setMood(mood === option.value ? '' : option.value)}
                      className={`px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200 border ${
                        mood === option.value
                          ? option.color + ' scale-105 shadow-md'
                          : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 border-gray-200 dark:border-gray-700 hover:border-amber-300 dark:hover:border-gray-600'
                      }`}
                    >
                      <span className="mr-1.5">{option.emoji}</span>
                      {option.value}
                    </button>
                  ))}
                </div>
              </div>

              {/* Tags */}
              <div>
                <label htmlFor="tags" className="block text-sm font-medium text-amber-800 dark:text-gray-300 mb-3">
                  Tags
                </label>
                <div className="flex gap-2 mb-3">
                  <input
                    id="tags"
                    type="text"
                    value={tagInput}
                    onChange={(e) => setTagInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
                    className="flex-1 px-4 py-2 border border-amber-200 dark:border-gray-700 rounded-xl shadow-sm focus:ring-2 focus:ring-amber-400/50 dark:focus:ring-violet-500/50 focus:border-amber-400 dark:focus:border-violet-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 text-sm"
                    placeholder="Add a tag and press Enter..."
                  />
                  <button
                    type="button"
                    onClick={handleAddTag}
                    className="px-4 py-2 bg-amber-100 dark:bg-gray-700 text-amber-700 dark:text-gray-300 rounded-xl hover:bg-amber-200 dark:hover:bg-gray-600 transition-colors text-sm font-medium"
                  >
                    Add
                  </button>
                </div>
                {tags.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {tags.map((tag) => (
                      <span
                        key={tag}
                        className="inline-flex items-center px-3 py-1.5 rounded-full text-sm bg-gradient-to-r from-amber-100 to-orange-100 dark:from-violet-900/50 dark:to-purple-900/50 text-amber-800 dark:text-violet-300 border border-amber-200/50 dark:border-violet-700/50"
                      >
                        <span className="mr-1 opacity-60">#</span>
                        {tag}
                        <button
                          type="button"
                          onClick={() => handleRemoveTag(tag)}
                          className="ml-2 text-amber-500 hover:text-amber-700 dark:text-violet-400 dark:hover:text-violet-300"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Save button */}
        <div className="px-6 py-4 bg-amber-50/50 dark:bg-gray-900/50 border-t border-amber-100/50 dark:border-gray-800/50">
          <button
            type="submit"
            disabled={!content.trim() || saving}
            className="w-full py-3.5 px-6 bg-gradient-to-r from-amber-500 to-orange-500 dark:from-violet-600 dark:to-purple-600 text-white rounded-xl font-semibold shadow-lg shadow-amber-500/25 dark:shadow-violet-500/25 hover:shadow-xl hover:shadow-amber-500/30 dark:hover:shadow-violet-500/30 hover:scale-[1.02] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100 disabled:hover:shadow-lg"
          >
            {saving ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                Saving...
              </span>
            ) : (
              <span className="flex items-center justify-center gap-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                </svg>
                Save Entry
              </span>
            )}
          </button>
        </div>
      </div>
    </form>
  )
}
