'use client'

export interface Transcription {
  id: string
  chunkIndex: number
  content: string
  startTime: string
  endTime: string
  duration: number
  status: 'pending' | 'processing' | 'completed' | 'failed'
  errorMessage?: string | null
}

interface TranscriptionListProps {
  transcriptions: Transcription[]
  isLoading?: boolean
}

function formatTime(dateString: string): string {
  const date = new Date(dateString)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

function StatusBadge({ status }: { status: Transcription['status'] }) {
  const styles = {
    pending: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
    processing: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
    completed: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
    failed: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
  }

  const labels = {
    pending: 'Pending',
    processing: 'Transcribing...',
    completed: 'Completed',
    failed: 'Failed'
  }

  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full ${styles[status]}`}>
      {status === 'processing' && (
        <svg className="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      )}
      {labels[status]}
    </span>
  )
}

export function TranscriptionList({ transcriptions, isLoading = false }: TranscriptionListProps) {
  if (transcriptions.length === 0 && !isLoading) {
    return (
      <div className="text-center py-12 text-gray-500 dark:text-gray-400">
        <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <p>No transcriptions yet</p>
        <p className="text-sm mt-1">Start recording and transcriptions will appear here</p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {transcriptions.map((transcription) => (
        <div
          key={transcription.id}
          className="p-4 bg-white dark:bg-[#1a1a1a] rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm"
        >
          <div className="flex items-start justify-between gap-3 mb-2">
            <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
              <span className="font-medium">Chunk {transcription.chunkIndex}</span>
              <span>-</span>
              <span>{formatTime(transcription.startTime)} - {formatTime(transcription.endTime)}</span>
              <span className="text-gray-400 dark:text-gray-600">({formatDuration(transcription.duration)})</span>
            </div>
            <StatusBadge status={transcription.status} />
          </div>

          {transcription.status === 'completed' && transcription.content && (
            <p className="text-gray-700 dark:text-gray-300 text-sm leading-relaxed whitespace-pre-wrap">
              {transcription.content}
            </p>
          )}

          {transcription.status === 'failed' && transcription.errorMessage && (
            <p className="text-red-600 dark:text-red-400 text-sm">
              Error: {transcription.errorMessage}
            </p>
          )}

          {transcription.status === 'pending' && (
            <p className="text-gray-400 dark:text-gray-600 text-sm italic">
              Waiting to be transcribed...
            </p>
          )}

          {transcription.status === 'processing' && (
            <p className="text-amber-600 dark:text-amber-400 text-sm italic">
              Transcription in progress...
            </p>
          )}
        </div>
      ))}

      {isLoading && (
        <div className="flex items-center justify-center py-4">
          <svg className="w-5 h-5 animate-spin text-gray-400" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        </div>
      )}
    </div>
  )
}
