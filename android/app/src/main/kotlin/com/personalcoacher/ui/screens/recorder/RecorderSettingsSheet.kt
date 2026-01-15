package com.personalcoacher.ui.screens.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalcoacher.data.remote.GeminiTranscriptionService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderSettingsSheet(
    apiKey: String,
    selectedModel: String,
    customModelId: String,
    chunkDuration: Int,
    useVoiceCommunication: Boolean,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onCustomModelIdChange: (String) -> Unit,
    onChunkDurationChange: (Int) -> Unit,
    onUseVoiceCommunicationChange: (Boolean) -> Unit,
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

            Spacer(modifier = Modifier.height(24.dp))

            // Voice Communication mode toggle
            Text(
                text = "Phone Call Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Use an alternative audio source that may work better during phone calls. Enable this if recording doesn't work while on a call.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (useVoiceCommunication) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = useVoiceCommunication,
                    onCheckedChange = onUseVoiceCommunicationChange
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
