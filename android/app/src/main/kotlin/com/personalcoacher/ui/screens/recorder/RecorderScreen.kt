package com.personalcoacher.ui.screens.recorder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.data.remote.GeminiTranscriptionService
import com.personalcoacher.domain.model.RecordingSession
import com.personalcoacher.domain.model.RecordingSessionStatus
import com.personalcoacher.domain.model.Transcription
import com.personalcoacher.domain.model.TranscriptionStatus
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorder") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Recording controls section
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
                modifier = Modifier.padding(16.dp)
            )

            // Sessions and transcriptions
            if (uiState.isRecording && selectedSession != null) {
                // Show current session transcriptions while recording
                TranscriptionsList(
                    transcriptions = transcriptions,
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
            onApiKeyChange = { viewModel.setGeminiApiKey(it) },
            onModelChange = { viewModel.setGeminiModel(it) },
            onCustomModelIdChange = { viewModel.setCustomModelId(it) },
            onChunkDurationChange = { viewModel.setChunkDuration(it) },
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

@Composable
private fun RecordingControlsSection(
    uiState: RecorderUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer display
            Text(
                text = formatTime(uiState.totalElapsed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            if (uiState.isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chunk ${uiState.currentChunkIndex} - ${formatTime(uiState.currentChunkElapsed)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Chunk progress bar
                Spacer(modifier = Modifier.height(16.dp))
                val progress = uiState.currentChunkElapsed.toFloat() / uiState.chunkDuration.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Next transcription in ${formatTime(uiState.chunkDuration - uiState.currentChunkElapsed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isRecording) {
                    // Pause/Resume button
                    FloatingActionButton(
                        onClick = { if (uiState.isPaused) onResumeRecording() else onPauseRecording() },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (uiState.isPaused) "Resume" else "Pause"
                        )
                    }

                    // Stop button
                    FloatingActionButton(
                        onClick = onStopRecording,
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                } else {
                    // Start recording button
                    val buttonColor by animateColorAsState(
                        targetValue = MaterialTheme.colorScheme.primary,
                        label = "buttonColor"
                    )
                    FloatingActionButton(
                        onClick = onStartRecording,
                        modifier = Modifier.size(72.dp),
                        containerColor = buttonColor
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Recording",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if (uiState.isPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recording Paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SessionsList(
    sessions: List<RecordingSession>,
    selectedSessionId: String?,
    onSelectSession: (RecordingSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recording sessions yet.\nTap the microphone to start recording.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Recording Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    isSelected = session.id == selectedSessionId,
                    onClick = { onSelectSession(session) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: RecordingSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title ?: "Recording Session",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.createdAt
                        .atZone(ZoneId.systemDefault())
                        .format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Status: ${session.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (session.status) {
                        RecordingSessionStatus.RECORDING -> MaterialTheme.colorScheme.primary
                        RecordingSessionStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        RecordingSessionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TranscriptionsList(
    transcriptions: List<Transcription>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Transcriptions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (transcriptions.isEmpty()) {
            item {
                Text(
                    text = "No transcriptions yet. They will appear here as each chunk is transcribed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(transcriptions, key = { it.id }) { transcription ->
                TranscriptionCard(transcription = transcription)
            }
        }
    }
}

@Composable
private fun TranscriptionCard(transcription: Transcription) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chunk ${transcription.chunkIndex}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                StatusBadge(status = transcription.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (transcription.status) {
                TranscriptionStatus.PENDING, TranscriptionStatus.PROCESSING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = if (transcription.status == TranscriptionStatus.PENDING) {
                                "Waiting to transcribe..."
                            } else {
                                "Transcribing..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TranscriptionStatus.COMPLETED -> {
                    Text(
                        text = transcription.content.ifBlank { "[No speech detected]" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TranscriptionStatus.FAILED -> {
                    Text(
                        text = transcription.errorMessage ?: "Transcription failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Duration: ${formatTime(transcription.duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBadge(status: TranscriptionStatus) {
    val (backgroundColor, textColor, text) = when (status) {
        TranscriptionStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Pending"
        )
        TranscriptionStatus.PROCESSING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Processing"
        )
        TranscriptionStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Completed"
        )
        TranscriptionStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Failed"
        )
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecorderSettingsSheet(
    apiKey: String,
    selectedModel: String,
    customModelId: String,
    chunkDuration: Int,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onCustomModelIdChange: (String) -> Unit,
    onChunkDurationChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedModelDropdown by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(apiKey) }
    var tempCustomModelId by remember { mutableStateOf(customModelId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Recorder Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Gemini API Key
            OutlinedTextField(
                value = tempApiKey,
                onValueChange = { tempApiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Get your API key from Google AI Studio")
                }
            )

            Button(
                onClick = { onApiKeyChange(tempApiKey) },
                modifier = Modifier.align(Alignment.End),
                enabled = tempApiKey.isNotBlank() && tempApiKey != apiKey
            ) {
                Text("Save Key")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Model selector
            Text(
                text = "Transcription Model",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box {
                OutlinedButton(
                    onClick = { expandedModelDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modelName = GeminiTranscriptionService.AVAILABLE_MODELS
                        .find { it.id == selectedModel }?.displayName ?: selectedModel
                    Text(modelName)
                }

                DropdownMenu(
                    expanded = expandedModelDropdown,
                    onDismissRequest = { expandedModelDropdown = false }
                ) {
                    GeminiTranscriptionService.AVAILABLE_MODELS.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                onModelChange(model.id)
                                expandedModelDropdown = false
                            }
                        )
                    }
                }
            }

            // Custom model ID input (shown when "Custom Model" is selected)
            if (selectedModel == GeminiTranscriptionService.CUSTOM_MODEL_ID) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = tempCustomModelId,
                    onValueChange = { tempCustomModelId = it },
                    label = { Text("Custom Model ID") },
                    placeholder = { Text("e.g., gemini-2.5-flash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("Enter any valid Gemini model ID")
                    }
                )
                Button(
                    onClick = { onCustomModelIdChange(tempCustomModelId) },
                    modifier = Modifier.align(Alignment.End),
                    enabled = tempCustomModelId.isNotBlank() && tempCustomModelId != customModelId
                ) {
                    Text("Apply Model")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chunk duration slider
            Text(
                text = "Chunk Duration: ${formatTime(chunkDuration)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "How often to transcribe audio chunks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = chunkDuration.toFloat(),
                onValueChange = { onChunkDurationChange(it.toInt()) },
                valueRange = 60f..3600f, // 1 minute to 1 hour
                steps = 58 // Every minute
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1 min", style = MaterialTheme.typography.labelSmall)
                Text("1 hour", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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
