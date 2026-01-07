'use client'

import { useState, useRef, useCallback, useEffect } from 'react'

export interface AudioChunk {
  blob: Blob
  startTime: Date
  endTime: Date
  duration: number // in seconds
  chunkIndex: number
}

export interface UseAudioRecorderOptions {
  chunkDuration: number // in seconds
  onChunkComplete: (chunk: AudioChunk) => void
}

export interface UseAudioRecorderReturn {
  isRecording: boolean
  isPaused: boolean
  currentChunkElapsed: number // seconds elapsed in current chunk
  totalElapsed: number // total seconds recorded
  chunkIndex: number
  error: string | null
  hasPermission: boolean | null
  startRecording: () => Promise<void>
  stopRecording: () => void
  pauseRecording: () => void
  resumeRecording: () => void
  requestPermission: () => Promise<boolean>
}

export function useAudioRecorder({
  chunkDuration,
  onChunkComplete
}: UseAudioRecorderOptions): UseAudioRecorderReturn {
  const [isRecording, setIsRecording] = useState(false)
  const [isPaused, setIsPaused] = useState(false)
  const [currentChunkElapsed, setCurrentChunkElapsed] = useState(0)
  const [totalElapsed, setTotalElapsed] = useState(0)
  const [chunkIndex, setChunkIndex] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [hasPermission, setHasPermission] = useState<boolean | null>(null)

  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const chunkStartTimeRef = useRef<Date | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const chunkTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const chunkIndexRef = useRef(0)
  const isRecordingRef = useRef(false)
  const isPausedRef = useRef(false)
  const onChunkCompleteRef = useRef(onChunkComplete)
  const chunkDurationRef = useRef(chunkDuration)
  const scheduleNextChunkRef = useRef<(remainingTime?: number) => void>(() => {})
  const remainingChunkTimeRef = useRef<number | null>(null)
  const chunkTimerStartRef = useRef<number | null>(null)

  // Keep refs in sync with state/props
  useEffect(() => {
    onChunkCompleteRef.current = onChunkComplete
  }, [onChunkComplete])

  useEffect(() => {
    chunkDurationRef.current = chunkDuration
  }, [chunkDuration])

  // Cleanup function
  const cleanup = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    if (chunkTimerRef.current) {
      clearTimeout(chunkTimerRef.current)
      chunkTimerRef.current = null
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop()
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop())
      streamRef.current = null
    }
    mediaRecorderRef.current = null
    chunksRef.current = []
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    return cleanup
  }, [cleanup])

  const requestPermission = useCallback(async (): Promise<boolean> => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      // Stop the stream immediately - we just wanted to check permission
      stream.getTracks().forEach(track => track.stop())
      setHasPermission(true)
      setError(null)
      return true
    } catch {
      setHasPermission(false)
      setError('Microphone permission denied. Please allow access to use the recorder.')
      return false
    }
  }, [])

  // Check permission on mount
  useEffect(() => {
    if (typeof navigator !== 'undefined' && navigator.permissions) {
      navigator.permissions.query({ name: 'microphone' as PermissionName }).then(result => {
        setHasPermission(result.state === 'granted')
        result.onchange = () => {
          setHasPermission(result.state === 'granted')
        }
      }).catch(() => {
        // Permissions API not fully supported, we'll check on first use
        setHasPermission(null)
      })
    }
  }, [])

  const finalizeCurrentChunk = useCallback((nextChunkIndex: number) => {
    if (chunksRef.current.length > 0 && chunkStartTimeRef.current) {
      const blob = new Blob(chunksRef.current, { type: 'audio/webm;codecs=opus' })
      const endTime = new Date()
      const chunk: AudioChunk = {
        blob,
        startTime: chunkStartTimeRef.current,
        endTime,
        duration: Math.round((endTime.getTime() - chunkStartTimeRef.current.getTime()) / 1000),
        chunkIndex: nextChunkIndex - 1
      }
      onChunkCompleteRef.current(chunk)
    }
    chunksRef.current = []
    chunkStartTimeRef.current = new Date()
    setCurrentChunkElapsed(0)
  }, [])

  // Define scheduleNextChunk as a regular function and store in ref
  useEffect(() => {
    const scheduleNextChunk = (remainingTime?: number) => {
      // Use remaining time if provided, otherwise use full chunk duration
      const timeoutDuration = remainingTime ?? chunkDurationRef.current * 1000
      chunkTimerStartRef.current = Date.now()
      remainingChunkTimeRef.current = timeoutDuration

      chunkTimerRef.current = setTimeout(() => {
        if (!mediaRecorderRef.current || !isRecordingRef.current || isPausedRef.current) return

        const nextChunkIndex = chunkIndexRef.current + 1
        chunkIndexRef.current = nextChunkIndex
        setChunkIndex(nextChunkIndex)

        // Stop current recording to get final data
        if (mediaRecorderRef.current.state !== 'inactive') {
          mediaRecorderRef.current.stop()
        }

        finalizeCurrentChunk(nextChunkIndex)

        // Start new recording immediately
        setTimeout(() => {
          if (streamRef.current && isRecordingRef.current && !isPausedRef.current) {
            const newRecorder = new MediaRecorder(streamRef.current, {
              mimeType: 'audio/webm;codecs=opus'
            })
            newRecorder.ondataavailable = (e) => {
              if (e.data.size > 0) {
                chunksRef.current.push(e.data)
              }
            }
            newRecorder.start(1000) // Collect data every second
            mediaRecorderRef.current = newRecorder
          }
        }, 10)

        // Schedule next chunk using ref with full duration for new chunk
        scheduleNextChunkRef.current()
      }, timeoutDuration)
    }
    scheduleNextChunkRef.current = scheduleNextChunk
  }, [finalizeCurrentChunk])

  const startRecording = useCallback(async () => {
    try {
      setError(null)

      // Get microphone stream
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      setHasPermission(true)

      // Create MediaRecorder
      const mediaRecorder = new MediaRecorder(stream, {
        mimeType: 'audio/webm;codecs=opus'
      })
      mediaRecorderRef.current = mediaRecorder

      // Handle data available
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          chunksRef.current.push(e.data)
        }
      }

      // Initialize state
      chunksRef.current = []
      chunkStartTimeRef.current = new Date()
      chunkIndexRef.current = 1
      isRecordingRef.current = true
      isPausedRef.current = false
      setChunkIndex(1)
      setCurrentChunkElapsed(0)
      setTotalElapsed(0)
      setIsRecording(true)
      setIsPaused(false)

      // Start recording
      mediaRecorder.start(1000) // Collect data every second

      // Start elapsed timer
      timerRef.current = setInterval(() => {
        setCurrentChunkElapsed(prev => prev + 1)
        setTotalElapsed(prev => prev + 1)
      }, 1000)

      // Schedule first chunk completion
      scheduleNextChunkRef.current()

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start recording')
      setHasPermission(false)
      cleanup()
    }
  }, [cleanup])

  const stopRecording = useCallback(() => {
    if (chunkTimerRef.current) {
      clearTimeout(chunkTimerRef.current)
      chunkTimerRef.current = null
    }
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }

    isRecordingRef.current = false
    isPausedRef.current = false

    // Finalize any remaining data
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop()
      // Wait a bit for final ondataavailable to fire
      setTimeout(() => {
        if (chunksRef.current.length > 0 && chunkStartTimeRef.current) {
          finalizeCurrentChunk(chunkIndexRef.current + 1)
        }
        cleanup()
      }, 100)
    } else {
      cleanup()
    }

    setIsRecording(false)
    setIsPaused(false)
    setCurrentChunkElapsed(0)
    setChunkIndex(0)
    chunkIndexRef.current = 0
  }, [finalizeCurrentChunk, cleanup])

  const pauseRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.pause()
      isPausedRef.current = true
      setIsPaused(true)
      if (timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
      if (chunkTimerRef.current) {
        clearTimeout(chunkTimerRef.current)
        chunkTimerRef.current = null
        // Calculate remaining time in the chunk
        if (chunkTimerStartRef.current && remainingChunkTimeRef.current) {
          const elapsed = Date.now() - chunkTimerStartRef.current
          remainingChunkTimeRef.current = Math.max(0, remainingChunkTimeRef.current - elapsed)
        }
      }
    }
  }, [])

  const resumeRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'paused') {
      mediaRecorderRef.current.resume()
      isPausedRef.current = false
      setIsPaused(false)

      // Resume timers
      timerRef.current = setInterval(() => {
        setCurrentChunkElapsed(prev => prev + 1)
        setTotalElapsed(prev => prev + 1)
      }, 1000)

      // Schedule next chunk with the remaining time from before pause
      const remainingTime = remainingChunkTimeRef.current
      scheduleNextChunkRef.current(remainingTime ?? undefined)
    }
  }, [])

  return {
    isRecording,
    isPaused,
    currentChunkElapsed,
    totalElapsed,
    chunkIndex,
    error,
    hasPermission,
    startRecording,
    stopRecording,
    pauseRecording,
    resumeRecording,
    requestPermission
  }
}
