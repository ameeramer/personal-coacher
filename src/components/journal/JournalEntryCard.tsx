'use client'

interface JournalEntryCardProps {
  id: string
  content: string
  mood?: string | null
  tags: string[]
  date: string
  onDelete?: (id: string) => void
}

export function JournalEntryCard({ id, content, mood, tags, date, onDelete }: JournalEntryCardProps) {
  const formattedDate = new Date(date).toLocaleDateString('en-US', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })

  return (
    <div className="bg-white dark:bg-[#1a1a1a] rounded-lg shadow-sm dark:shadow-black/20 border border-gray-200 dark:border-gray-800 p-6">
      <div className="flex justify-between items-start mb-3">
        <div>
          <p className="text-sm text-gray-500 dark:text-gray-400">{formattedDate}</p>
          {mood && (
            <span className="inline-block mt-1 px-2 py-1 text-xs rounded-full bg-emerald-100 dark:bg-emerald-900/50 text-emerald-700 dark:text-emerald-400">
              {mood}
            </span>
          )}
        </div>
        {onDelete && (
          <button
            onClick={() => onDelete(id)}
            className="text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 text-sm"
          >
            Delete
          </button>
        )}
      </div>
      <p className="text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{content}</p>
      {tags.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {tags.map((tag) => (
            <span
              key={tag}
              className="px-2 py-1 text-xs rounded-full bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400"
            >
              {tag}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
