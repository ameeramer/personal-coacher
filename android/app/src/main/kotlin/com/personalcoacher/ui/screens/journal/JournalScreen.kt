package com.personalcoacher.ui.screens.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.model.Note
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.model.Task
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class JournalTab(val titleResId: Int, val icon: ImageVector) {
    ENTRIES(R.string.journal_tab_entries, Icons.Default.AutoStories),
    NOTES(R.string.journal_tab_notes, Icons.Default.Notes),
    GOALS(R.string.journal_tab_goals, Icons.Default.Flag),
    TASKS(R.string.journal_tab_tasks, Icons.Default.Task)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onEntryClick: (JournalEntry) -> Unit,
    onNewEntry: () -> Unit,
    onNoteClick: (Note) -> Unit = {},
    onNewNote: () -> Unit = {},
    onGoalClick: (Goal) -> Unit = {},
    onNewGoal: () -> Unit = {},
    onTaskClick: (Task) -> Unit = {},
    onNewTask: () -> Unit = {},
    onNavigateToCall: () -> Unit = {},
    journalViewModel: JournalViewModel = hiltViewModel(),
    noteViewModel: NoteViewModel = hiltViewModel(),
    goalViewModel: GoalViewModel = hiltViewModel(),
    taskViewModel: TaskViewModel = hiltViewModel()
) {
    val journalState by journalViewModel.uiState.collectAsState()
    val notesState by noteViewModel.notesState.collectAsState()
    val goalsState by goalViewModel.goalsState.collectAsState()
    val tasksState by taskViewModel.tasksState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val extendedColors = PersonalCoachTheme.extendedColors
    val journalBackground = extendedColors.journalBackground

    val pagerState = rememberPagerState(pageCount = { JournalTab.entries.size })
    val coroutineScope = rememberCoroutineScope()

    var showNewItemDialog by remember { mutableStateOf(false) }

    // Show errors from all view models
    LaunchedEffect(journalState.error, notesState.error, goalsState.error, tasksState.error) {
        listOfNotNull(
            journalState.error,
            notesState.error,
            goalsState.error,
            tasksState.error
        ).firstOrNull()?.let { error ->
            snackbarHostState.showSnackbar(error)
            journalViewModel.clearError()
            noteViewModel.clearError()
            goalViewModel.clearError()
            taskViewModel.clearError()
        }
    }

    if (showNewItemDialog) {
        NewItemDialog(
            onDismiss = { showNewItemDialog = false },
            onItemSelected = { itemType ->
                showNewItemDialog = false
                when (itemType) {
                    NewItemType.JOURNAL_ENTRY -> onNewEntry()
                    NewItemType.NOTE -> onNewNote()
                    NewItemType.GOAL -> onNewGoal()
                    NewItemType.TASK -> onNewTask()
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Voice call button - for voice journaling
                FloatingActionButton(
                    onClick = onNavigateToCall,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Voice Journal",
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Primary add button
                FloatingActionButton(
                    onClick = { showNewItemDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.journal_new_entry))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = journalBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Page title with gradient icon
            Row(
                modifier = Modifier.padding(
                    start = IOSSpacing.screenPadding,
                    end = IOSSpacing.screenPadding,
                    top = 16.dp,
                    bottom = 8.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.journal_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = IOSSpacing.screenPadding
            ) {
                JournalTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(stringResource(tab.titleResId))
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (JournalTab.entries[page]) {
                    JournalTab.ENTRIES -> {
                        if (journalState.entries.isEmpty() && !journalState.isLoading) {
                            EmptyJournalState()
                        } else {
                            JournalEntriesList(
                                entries = journalState.entries,
                                processingEntryIds = journalState.processingEntryIds,
                                onEntryClick = onEntryClick,
                                onDeleteEntry = { journalViewModel.deleteEntry(it) },
                                onAnalyzeEvents = { journalViewModel.analyzeEntryForEvents(it) }
                            )
                        }
                    }
                    JournalTab.NOTES -> {
                        NotesTab(
                            notes = notesState.notes,
                            onNoteClick = onNoteClick,
                            onDeleteNote = { noteViewModel.deleteNote(it) }
                        )
                    }
                    JournalTab.GOALS -> {
                        GoalsTab(
                            goals = goalsState.goals,
                            onGoalClick = onGoalClick,
                            onStatusChange = { goal, status -> goalViewModel.updateGoalStatus(goal, status) },
                            onDeleteGoal = { goalViewModel.deleteGoal(it) }
                        )
                    }
                    JournalTab.TASKS -> {
                        TasksTab(
                            tasks = tasksState.tasks,
                            availableGoals = tasksState.availableGoals,
                            onTaskClick = onTaskClick,
                            onToggleComplete = { taskViewModel.toggleTaskCompletion(it) },
                            onDeleteTask = { taskViewModel.deleteTask(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEntriesList(
    entries: List<JournalEntry>,
    processingEntryIds: Set<String>,
    onEntryClick: (JournalEntry) -> Unit,
    onDeleteEntry: (JournalEntry) -> Unit,
    onAnalyzeEvents: (JournalEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(IOSSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing)
    ) {
        items(entries, key = { it.id }) { entry ->
            JournalEntryCard(
                entry = entry,
                onClick = { onEntryClick(entry) },
                onDelete = { onDeleteEntry(entry) },
                onAnalyzeEvents = { onAnalyzeEvents(entry) },
                isProcessing = processingEntryIds.contains(entry.id)
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EmptyJournalState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.journal_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.journal_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAnalyzeEvents: () -> Unit = {},
    isProcessing: Boolean = false
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    val timeFormatter = remember {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = extendedColors.translucentSurface
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    entry.mood?.let { mood ->
                        Text(
                            text = mood.emoji,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = entry.date.atZone(ZoneId.systemDefault()).format(dateFormatter),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.date.atZone(ZoneId.systemDefault()).format(timeFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.journal_analyzing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (entry.syncStatus == SyncStatus.LOCAL_ONLY) {
                        Text(
                            text = stringResource(R.string.sync_local_only),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onAnalyzeEvents,
                        modifier = Modifier.size(36.dp),
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = stringResource(R.string.journal_analyze_events),
                            tint = if (isProcessing)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
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
                            contentDescription = stringResource(R.string.journal_delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val displayContent = entry.content
                .replace(Regex("<span[^>]*>"), "")
                .replace("</span>", "")
                .take(300)

            MarkdownText(
                markdown = displayContent + if (entry.content.length > 300) "..." else "",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 4
            )

            if (entry.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entry.tags) { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
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
