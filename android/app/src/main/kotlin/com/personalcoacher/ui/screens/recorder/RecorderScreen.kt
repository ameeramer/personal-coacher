package com.personalcoacher.ui.screens.recorder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    viewModel: RecorderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val selectedSession by viewModel.selectedSession.collectAsState()
    val transcriptions by viewModel.selectedSessionTranscriptions.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setPermissionStatus(isGranted)
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for recording")
            }
        }
    }

    // Check permission on launch
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionStatus(hasPermission)
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val extendedColors = PersonalCoachTheme.extendedColors

    Scaffold(
        topBar = {
            // iOS-style translucent top bar with large, bold title
            Surface(
                color = extendedColors.translucentSurface,
                border = BorderStroke(0.5.dp, extendedColors.thinBorder),
                shadowElevation = 0.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Recorder",
                            style = MaterialTheme.typography.headlineMedium // Larger, bolder title
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Recording controls section with increased padding
            RecordingControlsSection(
                uiState = uiState,
                onStartRecording = {
                    if (uiState.hasPermission == true) {
                        viewModel.startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = { viewModel.stopRecording() },
                onPauseRecording = { viewModel.pauseRecording() },
                onResumeRecording = { viewModel.resumeRecording() },
                modifier = Modifier.padding(IOSSpacing.screenPadding) // Increased from 16.dp
            )

            // Sessions and transcriptions
            if (uiState.isRecording && selectedSession != null) {
                // Show current session transcriptions while recording
                TranscriptionsList(
                    transcriptions = transcriptions,
                    onRetryTranscription = { transcriptionId ->
                        viewModel.retryTranscription(transcriptionId)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Show session list
                SessionsList(
                    sessions = sessions,
                    selectedSessionId = uiState.selectedSessionId,
                    onSelectSession = { viewModel.selectSession(it.id) },
                    onDeleteSession = { showDeleteConfirm = it },
                    modifier = Modifier.weight(1f)
                )

                // Show transcriptions for selected session
                if (selectedSession != null) {
                    TranscriptionsList(
                        transcriptions = transcriptions,
                        onRetryTranscription = { transcriptionId ->
                            viewModel.retryTranscription(transcriptionId)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Settings bottom sheet
    if (showSettings) {
        RecorderSettingsSheet(
            apiKey = uiState.geminiApiKey,
            selectedModel = uiState.selectedGeminiModel,
            customModelId = uiState.customModelId,
            chunkDuration = uiState.chunkDuration,
            useVoiceCommunication = uiState.useVoiceCommunication,
            onApiKeyChange = { viewModel.setGeminiApiKey(it) },
            onModelChange = { viewModel.setGeminiModel(it) },
            onCustomModelIdChange = { viewModel.setCustomModelId(it) },
            onChunkDurationChange = { viewModel.setChunkDuration(it) },
            onUseVoiceCommunicationChange = { viewModel.setUseVoiceCommunication(it) },
            onDismiss = { showSettings = false }
        )
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Session?") },
            text = { Text("This will permanently delete this recording session and all its transcriptions.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(sessionId)
                        showDeleteConfirm = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
