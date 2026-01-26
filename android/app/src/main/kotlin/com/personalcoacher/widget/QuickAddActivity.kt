package com.personalcoacher.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalcoacher.R
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.repository.GoalRepository
import com.personalcoacher.domain.repository.NoteRepository
import com.personalcoacher.domain.repository.TaskRepository
import com.personalcoacher.ui.theme.Amber500
import com.personalcoacher.ui.theme.Emerald600
import com.personalcoacher.ui.theme.PersonalCoachTheme
import com.personalcoacher.ui.theme.Violet600
import com.personalcoacher.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject
    lateinit var noteRepository: NoteRepository

    @Inject
    lateinit var goalRepository: GoalRepository

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeString = intent.getStringExtra(EXTRA_TYPE) ?: QuickAddType.NOTE.name
        val type = try {
            QuickAddType.valueOf(typeString)
        } catch (e: IllegalArgumentException) {
            QuickAddType.NOTE
        }

        setContent {
            PersonalCoachTheme {
                QuickAddScreen(
                    type = type,
                    onSave = { title, content -> saveItem(type, title, content) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private suspend fun saveItem(type: QuickAddType, title: String, content: String): Boolean {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            showToast(getString(R.string.widget_error_not_logged_in))
            return false
        }

        val result = when (type) {
            QuickAddType.NOTE -> noteRepository.createNote(
                userId = userId,
                title = title,
                content = content
            )
            QuickAddType.GOAL -> goalRepository.createGoal(
                userId = userId,
                title = title,
                description = content,
                targetDate = null,
                priority = Priority.MEDIUM
            )
            QuickAddType.TASK -> taskRepository.createTask(
                userId = userId,
                title = title,
                description = content,
                dueDate = null,
                priority = Priority.MEDIUM,
                linkedGoalId = null
            )
        }

        return when (result) {
            is Resource.Success -> {
                showToast(getString(R.string.widget_saved))
                true
            }
            is Resource.Error -> {
                showToast(getString(R.string.widget_error_save_failed))
                false
            }
            is Resource.Loading -> false
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.personalcoacher.widget.ACTION_QUICK_ADD"
        const val EXTRA_TYPE = "extra_type"
    }
}

@Composable
private fun QuickAddScreen(
    type: QuickAddType,
    onSave: suspend (String, String) -> Boolean,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val (icon, accentColor, titleText) = when (type) {
        QuickAddType.NOTE -> Triple(
            Icons.Default.Description,
            Amber500,
            stringResource(R.string.widget_add_note)
        )
        QuickAddType.GOAL -> Triple(
            Icons.Default.Flag,
            Violet600,
            stringResource(R.string.widget_add_goal)
        )
        QuickAddType.TASK -> Triple(
            Icons.Default.Task,
            Emerald600,
            stringResource(R.string.widget_add_task)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = titleText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.widget_title_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Content input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.widget_content_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    enabled = !isSaving
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                scope.launch {
                                    isSaving = true
                                    val success = onSave(title.trim(), content.trim())
                                    isSaving = false
                                    if (success) {
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = title.isNotBlank() && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
