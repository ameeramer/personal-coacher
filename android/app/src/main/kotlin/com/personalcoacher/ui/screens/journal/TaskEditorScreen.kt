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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    taskId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel()
) {
    val editorState by viewModel.editorState.collectAsState()
    val tasksState by viewModel.tasksState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var goalDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            viewModel.loadTaskForEditing(taskId)
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
            initialSelectedDateMillis = editorState.dueDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.updateDueDate(date)
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
                        text = if (taskId != null) stringResource(R.string.task_edit) else stringResource(R.string.task_new)
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
                label = { Text(stringResource(R.string.task_title_label)) },
                placeholder = { Text(stringResource(R.string.task_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editorState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text(stringResource(R.string.task_description_label)) },
                placeholder = { Text(stringResource(R.string.task_description_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Due Date
            Text(
                text = stringResource(R.string.task_due_date) + " " + stringResource(R.string.task_due_date_optional),
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
                        text = editorState.dueDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            ?: "Select date",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (editorState.dueDate != null) {
                    TextButton(onClick = { viewModel.updateDueDate(null) }) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority
            Text(
                text = stringResource(R.string.task_priority),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Linked Goal
            if (tasksState.availableGoals.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.task_linked_goal) + " " + stringResource(R.string.task_linked_goal_optional),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                val selectedGoal = tasksState.availableGoals.find { it.id == editorState.linkedGoalId }

                ExposedDropdownMenuBox(
                    expanded = goalDropdownExpanded,
                    onExpandedChange = { goalDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGoal?.title ?: stringResource(R.string.task_linked_goal_none),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = goalDropdownExpanded,
                        onDismissRequest = { goalDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.task_linked_goal_none)) },
                            onClick = {
                                viewModel.updateLinkedGoal(null)
                                goalDropdownExpanded = false
                            }
                        )
                        tasksState.availableGoals.forEach { goal ->
                            DropdownMenuItem(
                                text = { Text(goal.title) },
                                onClick = {
                                    viewModel.updateLinkedGoal(goal.id)
                                    goalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveTask() },
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
                    Text(stringResource(R.string.task_save))
                }
            }
        }
    }
}
