package com.personalcoacher.ui.screens.journal

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.ui.components.journal.RichTextToolbar
import com.personalcoacher.ui.components.journal.WysiwygEditor
import com.personalcoacher.ui.theme.PersonalCoachTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalEditorScreen(
    onBack: () -> Unit,
    entryId: String? = null,
    viewModel: JournalEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // Content state - stores HTML content
    var content by rememberSaveable { mutableStateOf(uiState.content) }

    // Source mode toggle (false = WYSIWYG, true = HTML source view)
    var isSourceMode by rememberSaveable { mutableStateOf(false) }
    var showMoodTags by rememberSaveable { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Active formats from the editor (e.g., "bold", "italic", "h1")
    var activeFormats by remember { mutableStateOf(emptySet<String>()) }

    // WebView reference for toolbar commands
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Track the original content for unsaved changes detection
    val originalContent = remember(uiState.existingEntry) {
        uiState.existingEntry?.content ?: ""
    }

    // Detect if there are unsaved changes
    val hasUnsavedChanges by remember(content, originalContent) {
        derivedStateOf {
            content != originalContent && content.isNotBlank() && content != "<br>"
        }
    }

    // Handle back press with unsaved changes warning
    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedChangesDialog = true
    }

    // Sync content with uiState when loading existing entry
    LaunchedEffect(uiState.content) {
        if (content != uiState.content && uiState.existingEntry != null) {
            content = uiState.content
        }
    }

    // Update viewModel when content changes
    LaunchedEffect(content) {
        if (content != uiState.content) {
            viewModel.updateContent(content)
        }
    }

    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Navigate back on successful save
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onBack()
        }
    }

    val journalBackground = PersonalCoachTheme.extendedColors.journalBackground

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    // Allow dates up to today (no future dates)
                    val today = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    return utcTimeMillis < today
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            viewModel.updateSelectedDate(selectedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Unsaved changes confirmation dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Are you sure you want to leave? Your changes will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showDatePicker = true }
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.selectedDate.format(dateFormatter),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isSourceMode) "Source mode" else "WYSIWYG mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Change date",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showUnsavedChangesDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Mood & Tags toggle
                    TextButton(
                        onClick = { showMoodTags = !showMoodTags }
                    ) {
                        Text(
                            text = "Mood & Tags",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(
                            imageVector = if (showMoodTags) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Save button
                    IconButton(
                        onClick = viewModel::saveEntry,
                        enabled = content.isNotBlank() && content != "<br>" && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save",
                                tint = if (content.isNotBlank() && content != "<br>") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = journalBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = journalBackground
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                // Mood & Tags collapsible section
                AnimatedVisibility(
                    visible = showMoodTags,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    MoodTagsSection(
                        mood = uiState.mood,
                        onMoodChange = viewModel::updateMood,
                        tags = uiState.tags,
                        tagInput = uiState.tagInput,
                        onTagInputChange = viewModel::updateTagInput,
                        onAddTag = viewModel::addTag,
                        onRemoveTag = viewModel::removeTag,
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(journalBackground)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Rich text toolbar - communicates with WebView
                RichTextToolbar(
                    webView = webView,
                    activeFormats = activeFormats,
                    isSourceMode = isSourceMode,
                    onSourceModeChange = { isSourceMode = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                // WYSIWYG Editor - single view that toggles between modes
                WysiwygEditor(
                    content = content,
                    onContentChange = { newContent ->
                        content = newContent
                    },
                    isSourceMode = isSourceMode,
                    onActiveFormatsChange = { formats ->
                        activeFormats = formats
                    },
                    onWebViewReady = { view ->
                        webView = view
                    },
                    placeholder = stringResource(R.string.journal_content_placeholder),
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodTagsSection(
    mood: Mood?,
    onMoodChange: (Mood?) -> Unit,
    tags: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        // Mood selection
        Text(
            text = stringResource(R.string.journal_mood_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Mood.entries.forEach { moodOption ->
                val moodColor = getMoodColor(moodOption)
                FilterChip(
                    selected = mood == moodOption,
                    onClick = {
                        onMoodChange(if (mood == moodOption) null else moodOption)
                    },
                    label = { Text("${moodOption.emoji} ${moodOption.displayName}") },
                    enabled = enabled,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = moodColor.copy(alpha = 0.2f),
                        selectedLabelColor = moodColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tags section
        Text(
            text = stringResource(R.string.journal_tags_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = onTagInputChange,
                placeholder = { Text(stringResource(R.string.journal_tags_placeholder)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onAddTag()
                        focusManager.clearFocus()
                    }
                ),
                enabled = enabled
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onAddTag,
                enabled = tagInput.isNotBlank() && enabled
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text("#$tag") },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRemoveTag(tag) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun getMoodColor(mood: Mood): Color {
    val extendedColors = PersonalCoachTheme.extendedColors
    return when (mood) {
        Mood.GREAT -> extendedColors.moodHappy
        Mood.GOOD -> extendedColors.moodGrateful
        Mood.OKAY -> extendedColors.moodNeutral
        Mood.STRUGGLING -> extendedColors.moodAnxious
        Mood.DIFFICULT -> extendedColors.moodSad
    }
}
