'use client'

interface RecorderSettingsProps {
  chunkDuration: number // in seconds
  onChunkDurationChange: (duration: number) => void
  disabled?: boolean
}

const CHUNK_OPTIONS = [
  { value: 60, label: '1 minute' },
  { value: 300, label: '5 minutes' },
  { value: 600, label: '10 minutes' },
  { value: 900, label: '15 minutes' },
  { value: 1800, label: '30 minutes' },
  { value: 3600, label: '1 hour' }
]

export function RecorderSettings({
  chunkDuration,
  onChunkDurationChange,
  disabled = false
}: RecorderSettingsProps) {
  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
        Transcribe every
      </label>
      <select
        value={chunkDuration}
        onChange={(e) => onChunkDurationChange(Number(e.target.value))}
        disabled={disabled}
        className="w-full px-3 py-2 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-700 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-emerald-500 dark:focus:ring-violet-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        {CHUNK_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <p className="text-xs text-gray-500 dark:text-gray-400">
        Audio will be transcribed automatically after each interval
      </p>
    </div>
  )
}
