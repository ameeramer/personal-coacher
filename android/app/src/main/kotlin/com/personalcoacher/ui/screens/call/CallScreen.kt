package com.personalcoacher.ui.screens.call

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.voice.VoiceCallManager

/**
 * Screen for voice journaling calls.
 * Provides a phone-call-like interface for talking to the AI coach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToJournalEditor: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val currentTranscript by viewModel.currentTranscript.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val currentAmplitude by viewModel.currentAmplitude.collectAsState()
    val debugLogs by viewModel.debugLogs.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showEndCallDialog by remember { mutableStateOf(false) }
    var showDebugPanel by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Navigate to journal editor when entry is created
    LaunchedEffect(uiState.createdJournalEntryId) {
        uiState.createdJournalEntryId?.let { entryId ->
            onNavigateToJournalEditor(entryId)
            viewModel.clearCreatedJournalEntry()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!uiState.isInCall && callState is VoiceCallManager.CallState.Idle) {
                TopAppBar(
                    title = { Text("Voice Journal") },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                callState !is VoiceCallManager.CallState.Idle -> {
                    // In-call UI
                    InCallScreen(
                        callState = callState,
                        callDuration = callDuration,
                        currentTranscript = currentTranscript,
                        aiResponse = aiResponse,
                        amplitude = currentAmplitude,
                        conversationTurns = uiState.conversationTurns,
                        isSpeakerOn = isSpeakerOn,
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onShowDebugLogs = { showDebugPanel = true },
                        onEndCall = { showEndCallDialog = true },
                        onCancelCall = { viewModel.cancelCall() }
                    )
                }

                !uiState.canStartCall -> {
                    // API keys not configured
                    ApiKeyMissingScreen(
                        hasClaudeKey = uiState.hasClaudeApiKey,
                        hasGeminiKey = uiState.hasGeminiApiKey,
                        hasElevenLabsKey = uiState.hasElevenLabsApiKey,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }

                else -> {
                    // Ready to call
                    ReadyToCallScreen(
                        hasElevenLabsKey = uiState.hasElevenLabsApiKey,
                        onStartCall = { viewModel.startCall() }
                    )
                }
            }
        }
    }

    // End call confirmation dialog
    if (showEndCallDialog) {
        AlertDialog(
            onDismissRequest = { showEndCallDialog = false },
            title = { Text("End Call?") },
            text = {
                Text("Your conversation will be saved as a journal entry that you can edit.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndCallDialog = false
                        viewModel.endCall()
                    }
                ) {
                    Text("End & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndCallDialog = false }) {
                    Text("Continue Call")
                }
            }
        )
    }

    // Debug logs panel
    if (showDebugPanel) {
        DebugLogsDialog(
            logs = debugLogs,
            onDismiss = { showDebugPanel = false },
            onCopyLogs = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Debug Logs", viewModel.getDebugLogsAsText())
                clipboard.setPrimaryClip(clip)
            },
            onClearLogs = { viewModel.clearDebugLogs() }
        )
    }
}

@Composable
private fun InCallScreen(
    callState: VoiceCallManager.CallState,
    callDuration: Long,
    currentTranscript: String,
    aiResponse: String,
    amplitude: Float,
    conversationTurns: List<ConversationTurn>,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    onShowDebugLogs: () -> Unit,
    onEndCall: () -> Unit,
    onCancelCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val isListening = callState is VoiceCallManager.CallState.Listening
    val isSpeaking = callState is VoiceCallManager.CallState.Speaking
    val isProcessing = callState is VoiceCallManager.CallState.Processing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Timer
        Text(
            text = formatDuration(callDuration),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status indicator
        StatusIndicator(
            callState = callState,
            scale = if (isListening) scale else 1f
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Conversation history
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            ConversationHistory(
                turns = conversationTurns,
                currentTranscript = if (isProcessing) currentTranscript else "",
                currentResponse = if (isProcessing || isSpeaking) aiResponse else "",
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Call control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speaker toggle button
            FloatingActionButton(
                onClick = onToggleSpeaker,
                containerColor = if (isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (isSpeakerOn) "Speaker On" else "Speaker Off",
                    modifier = Modifier.size(24.dp),
                    tint = if (isSpeakerOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // End call button
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }

            // Debug button
            FloatingActionButton(
                onClick = onShowDebugLogs,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = "Debug Logs",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DebugLogsDialog(
    logs: List<String>,
    onDismiss: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug Logs")
                Row {
                    IconButton(onClick = onCopyLogs) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No logs yet...",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = when {
                                    log.contains("ERROR") -> MaterialTheme.colorScheme.error
                                    log.contains("SUCCESS") -> Color(0xFF4CAF50)
                                    log.contains("FAILED") -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun StatusIndicator(
    callState: VoiceCallManager.CallState,
    scale: Float
) {
    val (icon, text, color) = when (callState) {
        is VoiceCallManager.CallState.Connecting -> Triple(
            Icons.Default.Phone,
            "Connecting...",
            MaterialTheme.colorScheme.primary
        )
        is VoiceCallManager.CallState.Listening -> Triple(
            Icons.Default.Mic,
            "Listening...",
            MaterialTheme.colorScheme.primary
        )
        is VoiceCallManager.CallState.Processing -> Triple(
            Icons.Default.MicOff,
            (callState as VoiceCallManager.CallState.Processing).stage,
            MaterialTheme.colorScheme.secondary
        )
        is VoiceCallManager.CallState.Speaking -> Triple(
            Icons.Default.MicOff,
            "Coach is speaking",
            MaterialTheme.colorScheme.tertiary
        )
        is VoiceCallManager.CallState.Ending -> Triple(
            Icons.Default.MicOff,
            "Creating journal entry...",
            MaterialTheme.colorScheme.secondary
        )
        else -> Triple(Icons.Default.Mic, "", MaterialTheme.colorScheme.primary)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = color
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}

@Composable
private fun ConversationHistory(
    turns: List<ConversationTurn>,
    currentTranscript: String,
    currentResponse: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(turns.size, currentTranscript, currentResponse) {
        if (turns.isNotEmpty() || currentTranscript.isNotEmpty() || currentResponse.isNotEmpty()) {
            listState.animateScrollToItem(
                maxOf(0, turns.size - 1 + if (currentTranscript.isNotEmpty()) 1 else 0 + if (currentResponse.isNotEmpty()) 1 else 0)
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (turns.isEmpty() && currentTranscript.isEmpty() && currentResponse.isEmpty()) {
            item {
                Text(
                    text = "Your conversation will appear here...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(turns) { turn ->
            ConversationBubble(
                text = turn.text,
                isUser = turn.isUser
            )
        }

        // Current transcript (user is speaking)
        if (currentTranscript.isNotEmpty()) {
            item {
                ConversationBubble(
                    text = currentTranscript,
                    isUser = true,
                    isInProgress = true
                )
            }
        }

        // Current response (AI is responding)
        if (currentResponse.isNotEmpty()) {
            item {
                ConversationBubble(
                    text = currentResponse,
                    isUser = false,
                    isInProgress = true
                )
            }
        }
    }
}

@Composable
private fun ConversationBubble(
    text: String,
    isUser: Boolean,
    isInProgress: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isInProgress) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyToCallScreen(
    hasElevenLabsKey: Boolean,
    onStartCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Phone,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Voice Journal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Talk about your day and automatically create a journal entry",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (!hasElevenLabsKey) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    text = "Note: Add an ElevenLabs API key in Settings for voice responses",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        FloatingActionButton(
            onClick = onStartCall,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = "Start Call",
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tap to call your journal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ApiKeyMissingScreen(
    hasClaudeKey: Boolean,
    hasGeminiKey: Boolean,
    hasElevenLabsKey: Boolean,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "API Keys Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To use voice journaling, please configure the following API keys:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.Start
        ) {
            ApiKeyStatus("Claude API Key", hasClaudeKey, required = true)
            ApiKeyStatus("Gemini API Key (STT)", hasGeminiKey, required = true)
            ApiKeyStatus("ElevenLabs API Key (TTS)", hasElevenLabsKey, required = false)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onNavigateToSettings) {
            Text("Go to Settings")
        }
    }
}

@Composable
private fun ApiKeyStatus(
    name: String,
    isConfigured: Boolean,
    required: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (isConfigured) Color.Green else if (required) Color.Red else Color.Yellow,
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
        if (!required) {
            Text(
                text = " (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Formats milliseconds into MM:SS format.
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
