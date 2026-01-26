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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
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
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import java.time.format.DateTimeFormatter

@Composable
fun GoalsTab(
    goals: List<Goal>,
    onGoalClick: (Goal) -> Unit,
    onStatusChange: (Goal, GoalStatus) -> Unit,
    onDeleteGoal: (Goal) -> Unit,
    modifier: Modifier = Modifier
) {
    if (goals.isEmpty()) {
        EmptyGoalsState()
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(IOSSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing)
        ) {
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onClick = { onGoalClick(goal) },
                    onStatusChange = { status -> onStatusChange(goal, status) },
                    onDelete = { onDeleteGoal(goal) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun EmptyGoalsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.goal_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.goal_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    onClick: () -> Unit,
    onStatusChange: (GoalStatus) -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val isCompleted = goal.status == GoalStatus.COMPLETED
    val isArchived = goal.status == GoalStatus.ARCHIVED

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isArchived)
                extendedColors.translucentSurface.copy(alpha = 0.5f)
            else
                extendedColors.translucentSurface
        ),
        border = BorderStroke(0.5.dp, extendedColors.thinBorder),
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
                    // Priority indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(getPriorityColor(goal.priority))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                            ),
                            color = if (isArchived)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        goal.targetDate?.let { date ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Target: ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Row {
                    when (goal.status) {
                        GoalStatus.ACTIVE -> {
                            IconButton(
                                onClick = { onStatusChange(GoalStatus.COMPLETED) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.goal_mark_complete),
                                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        GoalStatus.COMPLETED -> {
                            IconButton(
                                onClick = { onStatusChange(GoalStatus.ACTIVE) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.goal_reactivate),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onStatusChange(GoalStatus.ARCHIVED) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = stringResource(R.string.goal_archive),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        GoalStatus.ARCHIVED -> {
                            IconButton(
                                onClick = { onStatusChange(GoalStatus.ACTIVE) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.goal_reactivate),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

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
                            contentDescription = stringResource(R.string.goal_delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (goal.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = goal.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isArchived) 0.4f else 0.8f
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(goal.priority.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = getPriorityColor(goal.priority).copy(alpha = 0.1f),
                        labelColor = getPriorityColor(goal.priority)
                    ),
                    border = null
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(goal.status.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = getStatusColor(goal.status).copy(alpha = 0.1f),
                        labelColor = getStatusColor(goal.status)
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

@Composable
private fun getStatusColor(status: GoalStatus): Color {
    return when (status) {
        GoalStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        GoalStatus.COMPLETED -> Color(0xFF43A047)
        GoalStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
