package com.personalcoacher.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personalcoacher.MainActivity
import com.personalcoacher.R
import com.personalcoacher.data.remote.GeminiTranscriptionService
import com.personalcoacher.domain.model.RecordingSessionStatus
import com.personalcoacher.domain.model.TranscriptionStatus
import com.personalcoacher.domain.repository.RecorderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class AudioRecorderService : Service() {

    companion object {
        private const val TAG = "AudioRecorderService"
        private const val CHANNEL_ID = "recorder_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.personalcoacher.recorder.START"
        const val ACTION_STOP = "com.personalcoacher.recorder.STOP"
        const val ACTION_PAUSE = "com.personalcoacher.recorder.PAUSE"
        const val ACTION_RESUME = "com.personalcoacher.recorder.RESUME"

        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_CHUNK_DURATION = "chunk_duration"
    }

    @Inject
    lateinit var recorderRepository: RecorderRepository

    @Inject
    lateinit var geminiService: GeminiTranscriptionService

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaRecorder: MediaRecorder? = null
    private var currentChunkFile: File? = null
    private var chunkTimerJob: Job? = null
    private var elapsedTimerJob: Job? = null

    private var sessionId: String? = null
    private var userId: String? = null
    private var chunkDurationSeconds: Int = 60 // Default 1 minute
    private var chunkIndex = 0
    private var chunkStartTime: Instant? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentChunkElapsed = MutableStateFlow(0)
    val currentChunkElapsed: StateFlow<Int> = _currentChunkElapsed.asStateFlow()

    private val _totalElapsed = MutableStateFlow(0)
    val totalElapsed: StateFlow<Int> = _totalElapsed.asStateFlow()

    private val _currentChunkIndex = MutableStateFlow(0)
    val currentChunkIndex: StateFlow<Int> = _currentChunkIndex.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): AudioRecorderService = this@AudioRecorderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                userId = intent.getStringExtra(EXTRA_USER_ID)
                chunkDurationSeconds = intent.getIntExtra(EXTRA_CHUNK_DURATION, 60)
                startRecording()
            }
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows recording status"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording in Progress")
            .setContentText("Tap to open app")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording in Progress")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startRecording() {
        if (_isRecording.value) return

        try {
            startForeground(NOTIFICATION_ID, createNotification())

            chunkIndex = 1
            _currentChunkIndex.value = chunkIndex
            _currentChunkElapsed.value = 0
            _totalElapsed.value = 0
            _error.value = null

            startNewChunk()
            startTimers()

            _isRecording.value = true
            _isPaused.value = false

            Log.d(TAG, "Recording started for session: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _error.value = e.message
            stopSelf()
        }
    }

    private fun startNewChunk() {
        // Create new audio file for this chunk - store in files dir for persistence (not cache)
        val audioDir = File(filesDir, "audio_chunks")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        currentChunkFile = File(audioDir, "chunk_${sessionId}_$chunkIndex.m4a")
        chunkStartTime = Instant.now()

        try {
            mediaRecorder?.release()
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentChunkFile?.absolutePath)
                prepare()
                start()
            }

            Log.d(TAG, "Started new chunk $chunkIndex")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start new chunk", e)
            _error.value = "Failed to start recording: ${e.message}"
        }
    }

    private fun startTimers() {
        // Elapsed time counter
        elapsedTimerJob?.cancel()
        elapsedTimerJob = serviceScope.launch {
            while (_isRecording.value) {
                delay(1000)
                if (!_isPaused.value && _isRecording.value) {
                    _currentChunkElapsed.value++
                    _totalElapsed.value++
                    updateNotification("Chunk ${_currentChunkIndex.value} - ${formatTime(_currentChunkElapsed.value)}")
                }
            }
        }

        // Chunk rotation timer
        scheduleChunkRotation()
    }

    private fun scheduleChunkRotation() {
        chunkTimerJob?.cancel()
        chunkTimerJob = serviceScope.launch {
            delay((chunkDurationSeconds - _currentChunkElapsed.value) * 1000L)
            if (_isRecording.value && !_isPaused.value) {
                rotateChunk()
            }
        }
    }

    private fun rotateChunk() {
        Log.d(TAG, "Rotating chunk $chunkIndex")

        val endTime = Instant.now()
        val startTime = chunkStartTime ?: endTime
        val duration = _currentChunkElapsed.value
        val finishedChunkIndex = chunkIndex
        val finishedChunkFile = currentChunkFile

        // Stop current recording
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder for chunk rotation", e)
        }

        // Start next chunk
        chunkIndex++
        _currentChunkIndex.value = chunkIndex
        _currentChunkElapsed.value = 0

        startNewChunk()
        scheduleChunkRotation()

        // Process the completed chunk in background
        serviceScope.launch(Dispatchers.IO) {
            processCompletedChunk(finishedChunkIndex, startTime, endTime, duration, finishedChunkFile)
        }
    }

    private suspend fun processCompletedChunk(
        index: Int,
        startTime: Instant,
        endTime: Instant,
        duration: Int,
        audioFile: File?
    ) {
        val sid = sessionId ?: return

        try {
            // Create transcription record with audio file path for retry support
            val transcription = recorderRepository.createTranscription(
                sessionId = sid,
                chunkIndex = index,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                audioFilePath = audioFile?.absolutePath
            )

            // Update status to processing
            recorderRepository.updateTranscriptionStatus(transcription.id, TranscriptionStatus.PROCESSING)

            // Transcribe the audio
            if (audioFile != null && audioFile.exists()) {
                val result = geminiService.transcribeAudio(audioFile, "audio/mp4")

                when (result) {
                    is GeminiTranscriptionService.TranscriptionResult.Success -> {
                        recorderRepository.updateTranscriptionContent(transcription.id, result.text)
                        // Clear audio file path and delete file only on success
                        recorderRepository.clearAudioFilePath(transcription.id)
                        audioFile.delete()
                        Log.d(TAG, "Transcription completed for chunk $index, audio file deleted")
                    }
                    is GeminiTranscriptionService.TranscriptionResult.Error -> {
                        recorderRepository.updateTranscriptionError(transcription.id, result.message)
                        // Keep audio file for retry - don't delete
                        Log.e(TAG, "Transcription failed for chunk $index: ${result.message}. Audio file kept for retry.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process chunk $index", e)
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
            chunkTimerJob?.cancel()
            _isPaused.value = true
            updateNotification("Recording paused")
            Log.d(TAG, "Recording paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause recording", e)
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
            scheduleChunkRotation()
            _isPaused.value = false
            updateNotification("Recording resumed")
            Log.d(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume recording", e)
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        Log.d(TAG, "Stopping recording")

        chunkTimerJob?.cancel()
        elapsedTimerJob?.cancel()

        val endTime = Instant.now()
        val startTime = chunkStartTime ?: endTime
        val duration = _currentChunkElapsed.value
        val finalChunkIndex = chunkIndex
        val finalChunkFile = currentChunkFile

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }
        mediaRecorder = null

        _isRecording.value = false
        _isPaused.value = false

        // Process final chunk
        serviceScope.launch(Dispatchers.IO) {
            if (duration > 0) {
                processCompletedChunk(finalChunkIndex, startTime, endTime, duration, finalChunkFile)
            }

            // Update session status
            sessionId?.let { sid ->
                try {
                    recorderRepository.endSession(sid, RecordingSessionStatus.COMPLETED)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to end session", e)
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
