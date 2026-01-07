'use client'

interface RecordingControlsProps {
  isRecording: boolean
  isPaused: boolean
  onStart: () => void
  onStop: () => void
  onPause: () => void
  onResume: () => void
  disabled?: boolean
}

export function RecordingControls({
  isRecording,
  isPaused,
  onStart,
  onStop,
  onPause,
  onResume,
  disabled = false
}: RecordingControlsProps) {
  return (
    <div className="flex items-center justify-center gap-4">
      {!isRecording ? (
        <button
          onClick={onStart}
          disabled={disabled}
          className="flex items-center justify-center w-20 h-20 rounded-full bg-gradient-to-br from-red-500 to-red-600 text-white shadow-lg shadow-red-500/30 hover:shadow-xl hover:shadow-red-500/40 hover:scale-105 transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
          aria-label="Start recording"
        >
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 18.75a6 6 0 006-6v-1.5m-6 7.5a6 6 0 01-6-6v-1.5m6 7.5v3.75m-3.75 0h7.5M12 15.75a3 3 0 01-3-3V4.5a3 3 0 116 0v8.25a3 3 0 01-3 3z" />
          </svg>
        </button>
      ) : (
        <>
          {/* Pause/Resume button */}
          <button
            onClick={isPaused ? onResume : onPause}
            className="flex items-center justify-center w-14 h-14 rounded-full bg-gradient-to-br from-amber-500 to-orange-500 text-white shadow-lg shadow-amber-500/30 hover:shadow-xl hover:shadow-amber-500/40 hover:scale-105 transition-all duration-300"
            aria-label={isPaused ? 'Resume recording' : 'Pause recording'}
          >
            {isPaused ? (
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
            ) : (
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
              </svg>
            )}
          </button>

          {/* Stop button */}
          <button
            onClick={onStop}
            className="flex items-center justify-center w-20 h-20 rounded-full bg-gradient-to-br from-gray-600 to-gray-700 text-white shadow-lg shadow-gray-600/30 hover:shadow-xl hover:shadow-gray-600/40 hover:scale-105 transition-all duration-300"
            aria-label="Stop recording"
          >
            <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24">
              <rect x="6" y="6" width="12" height="12" rx="2" />
            </svg>
          </button>
        </>
      )}
    </div>
  )
}
