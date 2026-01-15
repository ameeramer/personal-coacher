package com.personalcoacher.ui.screens.recorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalcoacher.domain.model.RecordingSession
import com.personalcoacher.domain.model.RecordingSessionStatus
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SessionsList(
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Lighter metadata
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(IOSSpacing.screenPadding), // Increased padding
            verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing) // Increased spacing
        ) {
            item {
                Text(
                    text = "Recording Sessions",
                    style = MaterialTheme.typography.headlineSmall, // Larger, bolder title
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
fun SessionCard(
    session: RecordingSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
    }

    // iOS-style translucent card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                extendedColors.translucentSurface
            }
        ),
        border = BorderStroke(0.5.dp, extendedColors.thinBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IOSSpacing.cardPadding), // Increased padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title ?: "Recording Session",
                    style = MaterialTheme.typography.titleMedium, // Slightly larger
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp)) // Increased spacing
                Text(
                    text = session.createdAt
                        .atZone(ZoneId.systemDefault())
                        .format(dateFormatter),
                    style = MaterialTheme.typography.labelSmall, // Smaller metadata
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Lighter
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${session.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall, // Smaller metadata
                    color = when (session.status) {
                        RecordingSessionStatus.RECORDING -> MaterialTheme.colorScheme.primary
                        RecordingSessionStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        RecordingSessionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
