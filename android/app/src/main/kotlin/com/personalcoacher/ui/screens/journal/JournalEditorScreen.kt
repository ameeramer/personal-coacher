package com.personalcoacher.ui.screens.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.Mood
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalEditorScreen(
    onBack: () -> Unit,
    entryId: String? = null,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val editorState = uiState.editorState
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    }

    LaunchedEffect(entryId) {
        if (entryId != null) {
            // Load entry for editing
            // This would need to be implemented in the ViewModel
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.showEditor) {
        if (!uiState.showEditor && editorState.editingEntryId == null) {
            // Entry was saved, go back
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LocalDate.now().format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.closeEditor()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
        ) {
            // Content editor
            OutlinedTextField(
                value = editorState.content,
                onValueChange = viewModel::updateContent,
                placeholder = { Text(stringResource(R.string.journal_content_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                enabled = !editorState.isSaving
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mood selection
            Text(
                text = stringResource(R.string.journal_mood_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Mood.entries.forEach { mood ->
                    FilterChip(
                        selected = editorState.mood == mood,
                        onClick = {
                            viewModel.updateMood(
                                if (editorState.mood == mood) null else mood
                            )
                        },
                        label = { Text("${mood.emoji} ${mood.displayName}") },
                        enabled = !editorState.isSaving
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tags
            Text(
                text = stringResource(R.string.journal_tags_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = editorState.tagInput,
                    onValueChange = viewModel::updateTagInput,
                    placeholder = { Text(stringResource(R.string.journal_tags_placeholder)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.addTag()
                            focusManager.clearFocus()
                        }
                    ),
                    enabled = !editorState.isSaving
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = viewModel::addTag,
                    enabled = editorState.tagInput.isNotBlank() && !editorState.isSaving
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
            }

            if (editorState.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    editorState.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text("#$tag") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.removeTag(tag) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            enabled = !editorState.isSaving
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = viewModel::saveEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = editorState.content.isNotBlank() && !editorState.isSaving
            ) {
                if (editorState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.journal_save),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
