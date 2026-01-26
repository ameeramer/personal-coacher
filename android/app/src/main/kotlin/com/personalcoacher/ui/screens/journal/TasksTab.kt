package com.personalcoacher.ui.screens.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalcoacher.R
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.Task
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TasksTab(
    tasks: List<Task>,
    availableGoals: List<Goal>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        EmptyTasksState()
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(IOSSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing)
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    linkedGoal = availableGoals.find { it.id == task.linkedGoalId },
                    onClick = { onTaskClick(task) },
                    onToggleComplete = { onToggleComplete(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun EmptyTasksState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Task,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.task_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.task_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TaskCard(
    task: Task,
    linkedGoal: Goal?,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val isOverdue = task.dueDate?.isBefore(LocalDate.now()) == true && !task.isCompleted
    val isDueToday = task.dueDate == LocalDate.now() && !task.isCompleted

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                extendedColors.translucentSurface.copy(alpha = 0.5f)
            else
                extendedColors.translucentSurface
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (isOverdue) Color(0xFFE53935).copy(alpha = 0.5f) else extendedColors.thinBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IOSSpacing.cardPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onToggleComplete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isCompleted)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.CheckCircleOutline,
                            contentDescription = if (task.isCompleted)
                                stringResource(R.string.task_mark_incomplete)
                            else
                                stringResource(R.string.task_mark_complete),
                            tint = if (task.isCompleted)
                                Color(0xFF43A047)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Priority indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(getPriorityColor(task.priority))
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                            ),
                            color = if (task.isCompleted)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        task.dueDate?.let { date ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    isOverdue -> stringResource(R.string.task_overdue) + ": ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
                                    isDueToday -> stringResource(R.string.task_due_today)
                                    else -> "Due: ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isOverdue -> Color(0xFFE53935)
                                    isDueToday -> Color(0xFFFB8C00)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.task_delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (task.isCompleted) 0.4f else 0.8f
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (linkedGoal != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text("Goal: ${linkedGoal.title}") },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        labelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    ),
                    border = null
                )
            }
        }
    }
}

@Composable
private fun getPriorityColor(priority: Priority): Color {
    return when (priority) {
        Priority.HIGH -> Color(0xFFE53935)
        Priority.MEDIUM -> Color(0xFFFB8C00)
        Priority.LOW -> Color(0xFF43A047)
    }
}
