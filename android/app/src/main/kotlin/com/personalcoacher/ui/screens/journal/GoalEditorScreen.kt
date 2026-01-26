package com.personalcoacher.ui.screens.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    goalId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val editorState by viewModel.editorState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(goalId) {
        if (goalId != null) {
            viewModel.loadGoalForEditing(goalId)
        } else {
            viewModel.clearEditorState()
        }
    }

    LaunchedEffect(editorState.saveSuccess) {
        if (editorState.saveSuccess) {
            onNavigateBack()
        }
    }

    LaunchedEffect(editorState.error) {
        editorState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = editorState.targetDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.updateTargetDate(date)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (goalId != null) stringResource(R.string.goal_edit) else stringResource(R.string.goal_new)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            OutlinedTextField(
                value = editorState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.goal_title_label)) },
                placeholder = { Text(stringResource(R.string.goal_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editorState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text(stringResource(R.string.goal_description_label)) },
                placeholder = { Text(stringResource(R.string.goal_description_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Target Date
            Text(
                text = stringResource(R.string.goal_target_date) + " " + stringResource(R.string.goal_target_date_optional),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Text(
                        text = editorState.targetDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            ?: "Select date",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (editorState.targetDate != null) {
                    TextButton(onClick = { viewModel.updateTargetDate(null) }) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority
            Text(
                text = stringResource(R.string.goal_priority),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Priority.entries.forEach { priority ->
                    FilterChip(
                        selected = editorState.priority == priority,
                        onClick = { viewModel.updatePriority(priority) },
                        label = { Text(priority.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveGoal() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !editorState.isSaving && editorState.title.isNotBlank()
            ) {
                if (editorState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.goal_save))
                }
            }
        }
    }
}
