'use client'

import { useState } from 'react'

interface JournalEditorProps {
  onSave: (entry: { content: string; mood: string; tags: string[] }) => Promise<void>
  initialContent?: string
  initialMood?: string
  initialTags?: string[]
}

const MOOD_OPTIONS = ['Great', 'Good', 'Okay', 'Struggling', 'Difficult']

export function JournalEditor({
  onSave,
  initialContent = '',
  initialMood = '',
  initialTags = []
}: JournalEditorProps) {
  const [content, setContent] = useState(initialContent)
  const [mood, setMood] = useState(initialMood)
  const [tagInput, setTagInput] = useState('')
  const [tags, setTags] = useState<string[]>(initialTags)
  const [saving, setSaving] = useState(false)

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
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="content" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          How was your day?
        </label>
        <textarea
          id="content"
          rows={6}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg shadow-sm focus:ring-emerald-500 dark:focus:ring-violet-500 focus:border-emerald-500 dark:focus:border-violet-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
          placeholder="Write about your day, thoughts, feelings..."
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          How are you feeling?
        </label>
        <div className="flex flex-wrap gap-2">
          {MOOD_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setMood(mood === option ? '' : option)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                mood === option
                  ? 'bg-emerald-600 dark:bg-violet-600 text-white'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
              }`}
            >
              {option}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label htmlFor="tags" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Tags
        </label>
        <div className="flex gap-2 mb-2">
          <input
            id="tags"
            type="text"
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
            className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg shadow-sm focus:ring-emerald-500 dark:focus:ring-violet-500 focus:border-emerald-500 dark:focus:border-violet-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
            placeholder="Add a tag..."
          />
          <button
            type="button"
            onClick={handleAddTag}
            className="px-4 py-2 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700"
          >
            Add
          </button>
        </div>
        {tags.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center px-3 py-1 rounded-full text-sm bg-emerald-100 dark:bg-violet-900/50 text-emerald-700 dark:text-violet-400"
              >
                {tag}
                <button
                  type="button"
                  onClick={() => handleRemoveTag(tag)}
                  className="ml-2 text-emerald-500 hover:text-emerald-700 dark:text-violet-400 dark:hover:text-violet-300"
                >
                  &times;
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      <button
        type="submit"
        disabled={!content.trim() || saving}
        className="w-full py-3 px-4 bg-emerald-600 dark:bg-violet-600 text-white rounded-lg font-medium hover:bg-emerald-700 dark:hover:bg-violet-700 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {saving ? 'Saving...' : 'Save Entry'}
      </button>
    </form>
  )
}
