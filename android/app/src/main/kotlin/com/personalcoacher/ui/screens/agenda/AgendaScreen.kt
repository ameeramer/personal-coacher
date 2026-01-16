package com.personalcoacher.ui.screens.agenda

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class CalendarViewMode {
    WEEK,
    MONTH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val extendedColors = PersonalCoachTheme.extendedColors
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openNewItem() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.agenda_new_item)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with view mode toggle
                    AgendaHeader(
                        selectedDate = uiState.selectedDate,
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        onPreviousMonth = {
                            viewModel.selectDate(uiState.selectedDate.minusMonths(1))
                        },
                        onNextMonth = {
                            viewModel.selectDate(uiState.selectedDate.plusMonths(1))
                        },
                        onToday = {
                            viewModel.selectDate(LocalDate.now())
                        }
                    )

                    // Calendar view based on mode
                    when (viewMode) {
                        CalendarViewMode.WEEK -> {
                            WeekDateSelector(
                                selectedDate = uiState.selectedDate,
                                onDateSelected = { viewModel.selectDate(it) },
                                items = uiState.items
                            )
                        }
                        CalendarViewMode.MONTH -> {
                            MonthCalendarView(
                                selectedDate = uiState.selectedDate,
                                onDateSelected = { viewModel.selectDate(it) },
                                items = uiState.items
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Items for selected date
                    val itemsForDate = viewModel.getItemsForDate(uiState.selectedDate)

                    if (itemsForDate.isEmpty()) {
                        EmptyAgendaMessage(
                            onAddItem = { viewModel.openNewItem() }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = IOSSpacing.screenPadding,
                                vertical = 8.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(itemsForDate, key = { it.id }) { item ->
                                AgendaItemCard(
                                    item = item,
                                    onEdit = { viewModel.openEditItem(item) },
                                    onDelete = { viewModel.deleteItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Editor bottom sheet
    if (uiState.showEditor) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeEditor() },
            sheetState = bottomSheetState
        ) {
            AgendaItemEditor(
                state = uiState.editorState,
                onTitleChange = viewModel::updateTitle,
                onDescriptionChange = viewModel::updateDescription,
                onStartDateChange = viewModel::updateStartDate,
                onStartTimeChange = viewModel::updateStartTime,
                onEndDateChange = viewModel::updateEndDate,
                onEndTimeChange = viewModel::updateEndTime,
                onIsAllDayChange = viewModel::updateIsAllDay,
                onLocationChange = viewModel::updateLocation,
                onSave = viewModel::saveItem,
                onCancel = viewModel::closeEditor
            )
        }
    }
}

@Composable
private fun AgendaHeader(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IOSSpacing.screenPadding, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.agenda_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToday, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = stringResource(R.string.agenda_today),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous month"
                    )
                }
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next month"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // View mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewMode == CalendarViewMode.WEEK,
                onClick = { onViewModeChange(CalendarViewMode.WEEK) },
                label = { Text(stringResource(R.string.agenda_view_week)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarViewWeek,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = viewMode == CalendarViewMode.MONTH,
                onClick = { onViewModeChange(CalendarViewMode.MONTH) },
                label = { Text(stringResource(R.string.agenda_view_month)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun WeekDateSelector(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    items: List<AgendaItem>
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    // Get dates for the week containing the selected date
    val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
    val dates = (0..6).map { startOfWeek.plusDays(it.toLong()) }

    // Get dates that have items
    val datesWithItems = items.map { item ->
        item.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
    }.toSet()

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = IOSSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dates) { date ->
            val isSelected = date == selectedDate
            val hasItems = datesWithItems.contains(date)
            val isToday = date == LocalDate.now()

            DateChip(
                date = date,
                isSelected = isSelected,
                hasItems = hasItems,
                isToday = isToday,
                onClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
private fun DateChip(
    date: LocalDate,
    isSelected: Boolean,
    hasItems: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> extendedColors.translucentSurface
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .width(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (!isSelected && !isToday) BorderStroke(0.5.dp, extendedColors.thinBorder) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
            if (hasItems) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarView(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    items: List<AgendaItem>
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val yearMonth = YearMonth.from(selectedDate)
    val firstDayOfMonth = yearMonth.atDay(1)
    val lastDayOfMonth = yearMonth.atEndOfMonth()

    // Get the first day of the week for the first day of month
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // Monday = 1, Sunday = 7
    val daysFromPreviousMonth = firstDayOfWeek - 1 // How many days to show from previous month

    // Get dates that have items
    val datesWithItems = items.map { item ->
        item.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
    }.toSet()

    // Build list of all days to display (including padding from prev/next months)
    val calendarDays = mutableListOf<LocalDate?>()

    // Add empty slots for days before the 1st of the month
    repeat(daysFromPreviousMonth) {
        calendarDays.add(null)
    }

    // Add all days of the current month
    var currentDay = firstDayOfMonth
    while (!currentDay.isAfter(lastDayOfMonth)) {
        calendarDays.add(currentDay)
        currentDay = currentDay.plusDays(1)
    }

    // Add empty slots to complete the last week if needed
    while (calendarDays.size % 7 != 0) {
        calendarDays.add(null)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IOSSpacing.screenPadding)
    ) {
        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        calendarDays.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                    ) {
                        if (date != null) {
                            MonthDayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == LocalDate.now(),
                                hasItems = datesWithItems.contains(date),
                                isCurrentMonth = true,
                                onClick = { onDateSelected(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasItems: Boolean,
    isCurrentMonth: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                ),
                color = textColor,
                textAlign = TextAlign.Center
            )
            if (hasItems && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else if (hasItems && isSelected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}

@Composable
private fun EmptyAgendaMessage(
    onAddItem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(IOSSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.agenda_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.agenda_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddItem) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.agenda_add_item))
        }
    }
}

@Composable
private fun AgendaItemCard(
    item: AgendaItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    var showDeleteDialog by remember { mutableStateOf(false) }

    val startTime = item.startTime.atZone(ZoneId.systemDefault())
    val endTime = item.endTime?.atZone(ZoneId.systemDefault())

    val timeText = if (item.isAllDay) {
        stringResource(R.string.agenda_all_day)
    } else {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        if (endTime != null) {
            "${startTime.format(formatter)} - ${endTime.format(formatter)}"
        } else {
            startTime.format(formatter)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = extendedColors.translucentSurface),
        border = BorderStroke(0.5.dp, extendedColors.thinBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!item.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.agenda_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.agenda_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!item.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.agenda_delete_title)) },
            text = { Text(stringResource(R.string.agenda_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaItemEditor(
    state: AgendaEditorState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onStartTimeChange: (LocalTime) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onEndTimeChange: (LocalTime) -> Unit,
    onIsAllDayChange: (Boolean) -> Unit,
    onLocationChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IOSSpacing.screenPadding)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.editingItemId != null) {
                    stringResource(R.string.agenda_edit_item)
                } else {
                    stringResource(R.string.agenda_new_item)
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.agenda_item_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.agenda_item_description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        // All day switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.agenda_all_day),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = state.isAllDay,
                onCheckedChange = onIsAllDayChange
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Start date/time
        Text(
            text = stringResource(R.string.agenda_start),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.startDate.format(dateFormatter),
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Event, contentDescription = null)
                    }
                )
                // Invisible clickable overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showStartDatePicker = true }
                )
            }
            if (!state.isAllDay) {
                Box(modifier = Modifier.width(100.dp)) {
                    OutlinedTextField(
                        value = state.startTime.format(timeFormatter),
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    )
                    // Invisible clickable overlay
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showStartTimePicker = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // End date/time
        Text(
            text = stringResource(R.string.agenda_end),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.endDate.format(dateFormatter),
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Event, contentDescription = null)
                    }
                )
                // Invisible clickable overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showEndDatePicker = true }
                )
            }
            if (!state.isAllDay) {
                Box(modifier = Modifier.width(100.dp)) {
                    OutlinedTextField(
                        value = state.endTime.format(timeFormatter),
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    )
                    // Invisible clickable overlay
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showEndTimePicker = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Location
        OutlinedTextField(
            value = state.location,
            onValueChange = onLocationChange,
            label = { Text(stringResource(R.string.agenda_location)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = null)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Save button
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving && state.title.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.save))
            }
        }
    }

    // Time pickers
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = state.startTime,
            onTimeSelected = {
                onStartTimeChange(it)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = state.endTime,
            onTimeSelected = {
                onEndTimeChange(it)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    // Date pickers
    if (showStartDatePicker) {
        CustomDatePickerDialog(
            initialDate = state.startDate,
            onDateSelected = {
                onStartDateChange(it)
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        CustomDatePickerDialog(
            initialDate = state.endDate,
            onDateSelected = {
                onEndDateChange(it)
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateSelected(selectedDate)
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}
