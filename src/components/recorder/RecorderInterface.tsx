'use client'

import { useState, useCallback, useEffect, useRef } from 'react'
import { useAudioRecorder, AudioChunk } from '@/hooks/useAudioRecorder'
import { RecordingControls } from './RecordingControls'
import { ChunkProgress } from './ChunkProgress'
import { TranscriptionList, Transcription } from './TranscriptionList'
import { RecorderSettings } from './RecorderSettings'

interface RecorderInterfaceProps {
  sessionId?: string
  initialTranscriptions?: Transcription[]
  onSessionCreated?: (sessionId: string) => void
}

export function RecorderInterface({
  sessionId: initialSessionId,
  initialTranscriptions = [],
  onSessionCreated
}: RecorderInterfaceProps) {
  const [sessionId, setSessionId] = useState<string | undefined>(initialSessionId)
  const [chunkDuration, setChunkDuration] = useState(1800) // 30 minutes default
  const [transcriptions, setTranscriptions] = useState<Transcription[]>(initialTranscriptions)
  const [error, setError] = useState<string | null>(null)
  const [isCreatingSession, setIsCreatingSession] = useState(false)
  const [hasPendingTranscriptions, setHasPendingTranscriptions] = useState(false)
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const handleChunkComplete = useCallback(async (chunk: AudioChunk) => {
    if (!sessionId) return

    try {
      // Create form data with the audio chunk
      const formData = new FormData()
      formData.append('audio', chunk.blob, `chunk-${chunk.chunkIndex}.webm`)
      formData.append('sessionId', sessionId)
      formData.append('chunkIndex', chunk.chunkIndex.toString())
      formData.append('startTime', chunk.startTime.toISOString())
      formData.append('endTime', chunk.endTime.toISOString())
      formData.append('duration', chunk.duration.toString())

      // Upload chunk for transcription
      const res = await fetch('/api/recorder/transcribe', {
        method: 'POST',
        body: formData
      })

      if (!res.ok) {
        throw new Error('Failed to upload audio chunk')
      }

      const data = await res.json()

      // Add pending transcription to the list
      setTranscriptions(prev => [...prev, {
        id: data.transcriptionId,
        chunkIndex: chunk.chunkIndex,
        content: '',
        startTime: chunk.startTime.toISOString(),
        endTime: chunk.endTime.toISOString(),
        duration: chunk.duration,
        status: 'pending'
      }])
    } catch (err) {
      console.error('Failed to upload chunk:', err)
      setError(err instanceof Error ? err.message : 'Failed to upload audio chunk')
    }
  }, [sessionId])

  const {
    isRecording,
    isPaused,
    currentChunkElapsed,
    totalElapsed,
    chunkIndex,
    error: recorderError,
    hasPermission,
    startRecording,
    stopRecording,
    pauseRecording,
    resumeRecording,
    requestPermission
  } = useAudioRecorder({
    chunkDuration,
    onChunkComplete: handleChunkComplete
  })

  // Update hasPendingTranscriptions when transcriptions change
  useEffect(() => {
    const hasPending = transcriptions.some(t => t.status === 'pending' || t.status === 'processing')
    setHasPendingTranscriptions(hasPending)
  }, [transcriptions])

  // Poll for transcription updates
  useEffect(() => {
    if (!sessionId) return

    const pollTranscriptions = async () => {
      try {
        const res = await fetch(`/api/recorder/sessions/${sessionId}`)
        if (res.ok) {
          const data = await res.json()
          setTranscriptions(data.transcriptions || [])
        }
      } catch (err) {
        console.error('Failed to poll transcriptions:', err)
      }
    }

    // Start polling when recording or when there are pending transcriptions
    if (isRecording || hasPendingTranscriptions) {
      pollingRef.current = setInterval(pollTranscriptions, 3000)
    }

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current)
        pollingRef.current = null
      }
    }
  }, [sessionId, isRecording, hasPendingTranscriptions])

  const handleStart = async () => {
    setError(null)

    try {
      // Create session first if needed
      if (!sessionId) {
        setIsCreatingSession(true)
        const res = await fetch('/api/recorder/sessions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ chunkDuration })
        })

        if (!res.ok) {
          throw new Error('Failed to create recording session')
        }

        const data = await res.json()
        setSessionId(data.id)
        onSessionCreated?.(data.id)
        setIsCreatingSession(false)
      }

      // Start recording
      await startRecording()
    } catch (err) {
      setIsCreatingSession(false)
      setError(err instanceof Error ? err.message : 'Failed to start recording')
    }
  }

  const handleStop = async () => {
    stopRecording()

    // Update session status
    if (sessionId) {
      try {
        await fetch(`/api/recorder/sessions/${sessionId}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status: 'completed' })
        })
      } catch (err) {
        console.error('Failed to update session status:', err)
      }
    }
  }

  const displayError = error || recorderError

  return (
    <div className="flex flex-col h-full">
      {/* Recording controls section */}
      <div className="flex-shrink-0 p-6 space-y-6">
        {/* Settings (only when not recording) */}
        {!isRecording && (
          <div className="max-w-xs mx-auto">
            <RecorderSettings
              chunkDuration={chunkDuration}
              onChunkDurationChange={setChunkDuration}
              disabled={isRecording}
            />
          </div>
        )}

        {/* Permission request */}
        {hasPermission === false && (
          <div className="text-center space-y-3">
            <p className="text-amber-600 dark:text-amber-400 text-sm">
              Microphone access is required to record
            </p>
            <button
              onClick={requestPermission}
              className="px-4 py-2 bg-emerald-500 dark:bg-violet-500 text-white rounded-lg hover:bg-emerald-600 dark:hover:bg-violet-600 transition-colors"
            >
              Grant Permission
            </button>
          </div>
        )}

        {/* Progress display */}
        <div className="flex justify-center">
          <ChunkProgress
            currentChunkElapsed={currentChunkElapsed}
            chunkDuration={chunkDuration}
            totalElapsed={totalElapsed}
            chunkIndex={chunkIndex}
            isRecording={isRecording}
            isPaused={isPaused}
          />
        </div>

        {/* Controls */}
        <RecordingControls
          isRecording={isRecording}
          isPaused={isPaused}
          onStart={handleStart}
          onStop={handleStop}
          onPause={pauseRecording}
          onResume={resumeRecording}
          disabled={hasPermission === false || isCreatingSession}
        />

        {/* Error display */}
        {displayError && (
          <div className="text-center text-red-600 dark:text-red-400 text-sm">
            {displayError}
          </div>
        )}
      </div>

      {/* Transcriptions section */}
      <div className="flex-1 overflow-auto p-6 pt-0">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Transcriptions
        </h3>
        <TranscriptionList
          transcriptions={transcriptions}
          isLoading={isRecording && transcriptions.length === 0}
        />
      </div>
    </div>
  )
}
