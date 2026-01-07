'use client'

interface ChunkProgressProps {
  currentChunkElapsed: number // seconds
  chunkDuration: number // seconds
  totalElapsed: number // seconds
  chunkIndex: number
  isRecording: boolean
  isPaused: boolean
}

function formatTime(seconds: number): string {
  const hrs = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

export function ChunkProgress({
  currentChunkElapsed,
  chunkDuration,
  totalElapsed,
  chunkIndex,
  isRecording,
  isPaused
}: ChunkProgressProps) {
  const progressPercent = Math.min((currentChunkElapsed / chunkDuration) * 100, 100)
  const remainingInChunk = Math.max(chunkDuration - currentChunkElapsed, 0)

  return (
    <div className="w-full max-w-md space-y-4">
      {/* Recording indicator */}
      <div className="flex items-center justify-center gap-2">
        {isRecording && (
          <span className={`w-3 h-3 rounded-full ${isPaused ? 'bg-amber-500' : 'bg-red-500 animate-pulse'}`} />
        )}
        <span className="text-sm font-medium text-gray-600 dark:text-gray-400">
          {!isRecording ? 'Ready to record' : isPaused ? 'Paused' : 'Recording...'}
        </span>
      </div>

      {/* Main timer */}
      <div className="text-center">
        <div className="text-5xl font-mono font-bold text-gray-900 dark:text-white tabular-nums">
          {formatTime(totalElapsed)}
        </div>
        <div className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          Total recorded
        </div>
      </div>

      {/* Chunk progress */}
      {isRecording && (
        <div className="space-y-2">
          <div className="flex justify-between text-sm text-gray-600 dark:text-gray-400">
            <span>Chunk {chunkIndex}</span>
            <span>{formatTime(remainingInChunk)} until transcription</span>
          </div>

          {/* Progress bar */}
          <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-emerald-500 to-teal-500 dark:from-violet-500 dark:to-purple-500 transition-all duration-1000 ease-linear"
              style={{ width: `${progressPercent}%` }}
            />
          </div>

          {/* Chunk time */}
          <div className="flex justify-between text-xs text-gray-500 dark:text-gray-500">
            <span>{formatTime(currentChunkElapsed)}</span>
            <span>{formatTime(chunkDuration)}</span>
          </div>
        </div>
      )}
    </div>
  )
}
