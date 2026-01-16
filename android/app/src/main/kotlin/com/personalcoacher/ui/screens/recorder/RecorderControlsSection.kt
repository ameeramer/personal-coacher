package com.personalcoacher.ui.screens.recorder

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.personalcoacher.R
import androidx.compose.ui.unit.dp
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme

@Composable
fun RecordingControlsSection(
    uiState: RecorderUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    // iOS-style translucent card with thin border
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = extendedColors.translucentSurface
        ),
        border = BorderStroke(0.5.dp, extendedColors.thinBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IOSSpacing.cardPaddingLarge), // Increased padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_recorder),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
