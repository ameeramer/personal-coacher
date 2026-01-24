package com.personalcoacher.ui.screens.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.personalcoacher.R

enum class NewItemType {
    JOURNAL_ENTRY,
    NOTE,
    GOAL,
    TASK
}

@Composable
fun NewItemDialog(
    onDismiss: () -> Unit,
    onItemSelected: (NewItemType) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.journal_new_item_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                NewItemOption(
                    icon = Icons.Default.AutoStories,
                    title = stringResource(R.string.journal_new_item_journal),
                    description = stringResource(R.string.journal_new_item_journal_desc),
                    onClick = { onItemSelected(NewItemType.JOURNAL_ENTRY) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                NewItemOption(
                    icon = Icons.Default.Notes,
                    title = stringResource(R.string.journal_new_item_note),
                    description = stringResource(R.string.journal_new_item_note_desc),
                    onClick = { onItemSelected(NewItemType.NOTE) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                NewItemOption(
                    icon = Icons.Default.Flag,
                    title = stringResource(R.string.journal_new_item_goal),
                    description = stringResource(R.string.journal_new_item_goal_desc),
                    onClick = { onItemSelected(NewItemType.GOAL) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                NewItemOption(
                    icon = Icons.Default.Task,
                    title = stringResource(R.string.journal_new_item_task),
                    description = stringResource(R.string.journal_new_item_task_desc),
                    onClick = { onItemSelected(NewItemType.TASK) }
                )
            }
        }
    }
}

@Composable
private fun NewItemOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
