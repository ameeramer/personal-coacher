'use client'

import { useState } from 'react'

interface RecorderSettingsProps {
  chunkDuration: number // in seconds
  onChunkDurationChange: (duration: number) => void
  selectedModel: string
  onModelChange: (model: string) => void
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

const MODEL_OPTIONS = [
  { value: 'gemini-3-pro-preview', label: 'Gemini 3 Pro Preview' },
  { value: 'gemini-3-flash', label: 'Gemini 3 Flash' },
  { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
  { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
  { value: 'custom', label: 'Custom Model' }
]

export function RecorderSettings({
  chunkDuration,
  onChunkDurationChange,
  selectedModel,
  onModelChange,
  disabled = false
}: RecorderSettingsProps) {
  const isCustomModel = !MODEL_OPTIONS.slice(0, -1).some(opt => opt.value === selectedModel)
  const [showCustomInput, setShowCustomInput] = useState(isCustomModel)
  const [customModelValue, setCustomModelValue] = useState(isCustomModel ? selectedModel : '')

  const handleModelSelectChange = (value: string) => {
    if (value === 'custom') {
      setShowCustomInput(true)
      // If there's already a custom value, use it; otherwise keep current model until user types
      if (customModelValue) {
        onModelChange(customModelValue)
      }
    } else {
      setShowCustomInput(false)
      onModelChange(value)
    }
  }

  const handleCustomModelChange = (value: string) => {
    setCustomModelValue(value)
    if (value.trim()) {
      onModelChange(value.trim())
    }
  }

  const selectValue = showCustomInput ? 'custom' : selectedModel

  return (
    <div className="space-y-4">
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

      <div className="space-y-2">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
          Transcription Model
        </label>
        <select
          value={selectValue}
          onChange={(e) => handleModelSelectChange(e.target.value)}
          disabled={disabled}
          className="w-full px-3 py-2 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-700 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-emerald-500 dark:focus:ring-violet-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {MODEL_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>

        {showCustomInput && (
          <input
            type="text"
            value={customModelValue}
            onChange={(e) => handleCustomModelChange(e.target.value)}
            placeholder="Enter custom model ID (e.g., gemini-1.5-pro)"
            disabled={disabled}
            className="w-full px-3 py-2 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-700 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-emerald-500 dark:focus:ring-violet-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition-colors placeholder:text-gray-400 dark:placeholder:text-gray-500"
          />
        )}

        <p className="text-xs text-gray-500 dark:text-gray-400">
          {showCustomInput
            ? 'Enter any Gemini model ID supported by your API key'
            : 'Select a model or choose "Custom Model" for a different one'}
        </p>
      </div>
    </div>
  )
}
