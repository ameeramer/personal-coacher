package com.personalcoacher.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalcoacher.R
import com.personalcoacher.data.local.entity.DayOfWeek
import com.personalcoacher.data.local.entity.IntervalUnit
import com.personalcoacher.domain.model.RuleType
import com.personalcoacher.domain.model.ScheduleRule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class ScheduleRuleTypeOption {
    INTERVAL, DAILY, WEEKLY, ONETIME
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddScheduleRuleDialog(
    onDismiss: () -> Unit,
    onSave: (ScheduleRule) -> Unit,
    userId: String,
    existingRule: ScheduleRule? = null
) {
    val isEditing = existingRule != null

    // Rule type selection
    var selectedType by remember {
        mutableStateOf(
            when (existingRule?.type) {
                is RuleType.Interval -> ScheduleRuleTypeOption.INTERVAL
                is RuleType.Daily -> ScheduleRuleTypeOption.DAILY
                is RuleType.Weekly -> ScheduleRuleTypeOption.WEEKLY
                is RuleType.OneTime -> ScheduleRuleTypeOption.ONETIME
                else -> ScheduleRuleTypeOption.INTERVAL
            }
        )
    }

    // Interval settings
    var intervalValue by remember {
        mutableStateOf(
            when (val type = existingRule?.type) {
                is RuleType.Interval -> type.value.toString()
                else -> "6"
            }
        )
    }
    var intervalUnit by remember {
        mutableStateOf(
            when (val type = existingRule?.type) {
                is RuleType.Interval -> type.unit
                else -> IntervalUnit.HOURS
            }
        )
    }

    // Time settings (for Daily, Weekly, OneTime)
    var selectedHour by remember {
        mutableIntStateOf(
            when (val type = existingRule?.type) {
                is RuleType.Daily -> type.hour
                is RuleType.Weekly -> type.hour
                is RuleType.OneTime -> type.hour
                else -> 9
            }
        )
    }
    var selectedMinute by remember {
        mutableIntStateOf(
            when (val type = existingRule?.type) {
                is RuleType.Daily -> type.minute
                is RuleType.Weekly -> type.minute
                is RuleType.OneTime -> type.minute
                else -> 0
            }
        )
    }

    // Weekly settings
    var selectedDays by remember {
        mutableIntStateOf(
            when (val type = existingRule?.type) {
                is RuleType.Weekly -> type.daysBitmask
                else -> DayOfWeek.MONDAY // Default to Monday
            }
        )
    }

    // One-time date
    var selectedDate by remember {
        mutableStateOf(
            when (val type = existingRule?.type) {
                is RuleType.OneTime -> type.date
                else -> LocalDate.now().plusDays(1).toString()
            }
        )
    }

    // Time picker dialog state
    var showTimePicker by remember { mutableStateOf(false) }

    // Validation error
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditing) R.string.edit_schedule_rule_title
                    else R.string.add_schedule_rule_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Rule Type Selection
                Text(
                    text = stringResource(R.string.schedule_rule_type),
                    style = MaterialTheme.typography.titleSmall
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleTypeCard(
                        title = stringResource(R.string.schedule_rule_type_interval),
                        description = stringResource(R.string.schedule_rule_type_interval_desc),
                        icon = Icons.Default.Repeat,
                        isSelected = selectedType == ScheduleRuleTypeOption.INTERVAL,
                        onClick = { selectedType = ScheduleRuleTypeOption.INTERVAL }
                    )
                    RuleTypeCard(
                        title = stringResource(R.string.schedule_rule_type_daily),
                        description = stringResource(R.string.schedule_rule_type_daily_desc),
                        icon = Icons.Default.Schedule,
                        isSelected = selectedType == ScheduleRuleTypeOption.DAILY,
                        onClick = { selectedType = ScheduleRuleTypeOption.DAILY }
                    )
                    RuleTypeCard(
                        title = stringResource(R.string.schedule_rule_type_weekly),
                        description = stringResource(R.string.schedule_rule_type_weekly_desc),
                        icon = Icons.Default.CalendarMonth,
                        isSelected = selectedType == ScheduleRuleTypeOption.WEEKLY,
                        onClick = { selectedType = ScheduleRuleTypeOption.WEEKLY }
                    )
                    RuleTypeCard(
                        title = stringResource(R.string.schedule_rule_type_onetime),
                        description = stringResource(R.string.schedule_rule_type_onetime_desc),
                        icon = Icons.Default.Today,
                        isSelected = selectedType == ScheduleRuleTypeOption.ONETIME,
                        onClick = { selectedType = ScheduleRuleTypeOption.ONETIME }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Type-specific settings
                when (selectedType) {
                    ScheduleRuleTypeOption.INTERVAL -> {
                        IntervalSettings(
                            intervalValue = intervalValue,
                            onIntervalValueChange = { intervalValue = it },
                            intervalUnit = intervalUnit,
                            onIntervalUnitChange = { intervalUnit = it }
                        )
                    }
                    ScheduleRuleTypeOption.DAILY -> {
                        TimeSelector(
                            hour = selectedHour,
                            minute = selectedMinute,
                            onClick = { showTimePicker = true }
                        )
                    }
                    ScheduleRuleTypeOption.WEEKLY -> {
                        DaySelector(
                            selectedDays = selectedDays,
                            onDaysChange = { selectedDays = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TimeSelector(
                            hour = selectedHour,
                            minute = selectedMinute,
                            onClick = { showTimePicker = true }
                        )
                    }
                    ScheduleRuleTypeOption.ONETIME -> {
                        DateSelector(
                            selectedDate = selectedDate,
                            onDateClick = {
                                val currentDate = try {
                                    LocalDate.parse(selectedDate)
                                } catch (e: Exception) {
                                    LocalDate.now().plusDays(1)
                                }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
                                    },
                                    currentDate.year,
                                    currentDate.monthValue - 1,
                                    currentDate.dayOfMonth
                                ).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TimeSelector(
                            hour = selectedHour,
                            minute = selectedMinute,
                            onClick = { showTimePicker = true }
                        )
                    }
                }

                // Error message
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validate and save
                    val validationError = validateRule(
                        type = selectedType,
                        intervalValue = intervalValue,
                        selectedDays = selectedDays,
                        selectedDate = selectedDate,
                        context = context
                    )

                    if (validationError != null) {
                        errorMessage = validationError
                        return@Button
                    }

                    val ruleType = when (selectedType) {
                        ScheduleRuleTypeOption.INTERVAL -> RuleType.Interval(
                            value = intervalValue.toIntOrNull() ?: 6,
                            unit = intervalUnit
                        )
                        ScheduleRuleTypeOption.DAILY -> RuleType.Daily(
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                        ScheduleRuleTypeOption.WEEKLY -> RuleType.Weekly(
                            daysBitmask = selectedDays,
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                        ScheduleRuleTypeOption.ONETIME -> RuleType.OneTime(
                            date = selectedDate,
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                    }

                    val rule = ScheduleRule(
                        id = existingRule?.id ?: UUID.randomUUID().toString(),
                        userId = userId,
                        type = ruleType,
                        enabled = existingRule?.enabled ?: true,
                        createdAt = existingRule?.createdAt ?: System.currentTimeMillis()
                    )

                    onSave(rule)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    // Time picker dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.schedule_rule_time)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RuleTypeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalSettings(
    intervalValue: String,
    onIntervalValueChange: (String) -> Unit,
    intervalUnit: String,
    onIntervalUnitChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_rule_interval_value),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = intervalValue,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                    onIntervalValueChange(newValue)
                }
            },
            modifier = Modifier.width(80.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = when (intervalUnit) {
                    IntervalUnit.MINUTES -> stringResource(R.string.schedule_rule_unit_minutes)
                    IntervalUnit.HOURS -> stringResource(R.string.schedule_rule_unit_hours)
                    IntervalUnit.DAYS -> stringResource(R.string.schedule_rule_unit_days)
                    IntervalUnit.WEEKS -> stringResource(R.string.schedule_rule_unit_weeks)
                    else -> intervalUnit
                },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_rule_unit_minutes)) },
                    onClick = {
                        onIntervalUnitChange(IntervalUnit.MINUTES)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_rule_unit_hours)) },
                    onClick = {
                        onIntervalUnitChange(IntervalUnit.HOURS)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_rule_unit_days)) },
                    onClick = {
                        onIntervalUnitChange(IntervalUnit.DAYS)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_rule_unit_weeks)) },
                    onClick = {
                        onIntervalUnitChange(IntervalUnit.WEEKS)
                        expanded = false
                    }
                )
            }
        }
    }

    // Show warning for minimum interval
    if (intervalUnit == IntervalUnit.MINUTES) {
        val value = intervalValue.toIntOrNull() ?: 0
        if (value > 0 && value < 15) {
            Text(
                text = stringResource(R.string.schedule_rule_min_interval),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun TimeSelector(
    hour: Int,
    minute: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.schedule_rule_time),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format(Locale.US, "%02d:%02d", hour, minute),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DateSelector(
    selectedDate: String,
    onDateClick: () -> Unit
) {
    val formattedDate = try {
        val date = LocalDate.parse(selectedDate)
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        date.format(formatter)
    } catch (e: Exception) {
        selectedDate
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDateClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.schedule_rule_date),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySelector(
    selectedDays: Int,
    onDaysChange: (Int) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.schedule_rule_days),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Quick selection chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedDays == DayOfWeek.allDays(),
                onClick = { onDaysChange(DayOfWeek.allDays()) },
                label = { Text(stringResource(R.string.day_every_day)) }
            )
            FilterChip(
                selected = selectedDays == DayOfWeek.weekdays(),
                onClick = { onDaysChange(DayOfWeek.weekdays()) },
                label = { Text(stringResource(R.string.day_weekdays)) }
            )
            FilterChip(
                selected = selectedDays == DayOfWeek.weekends(),
                onClick = { onDaysChange(DayOfWeek.weekends()) },
                label = { Text(stringResource(R.string.day_weekends)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Individual day circles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DayCircle(
                label = stringResource(R.string.day_monday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.MONDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.MONDAY)) }
            )
            DayCircle(
                label = stringResource(R.string.day_tuesday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.TUESDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.TUESDAY)) }
            )
            DayCircle(
                label = stringResource(R.string.day_wednesday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.WEDNESDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.WEDNESDAY)) }
            )
            DayCircle(
                label = stringResource(R.string.day_thursday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.THURSDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.THURSDAY)) }
            )
            DayCircle(
                label = stringResource(R.string.day_friday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.FRIDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.FRIDAY)) }
            )
            DayCircle(
                label = stringResource(R.string.day_saturday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.SATURDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.SATURDAY)) }
            )
            DayCircle(
                label = stringResource(R.string.day_sunday),
                isSelected = DayOfWeek.isDaySelected(selectedDays, DayOfWeek.SUNDAY),
                onClick = { onDaysChange(DayOfWeek.toggleDay(selectedDays, DayOfWeek.SUNDAY)) }
            )
        }
    }
}

@Composable
private fun DayCircle(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.take(1),
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun validateRule(
    type: ScheduleRuleTypeOption,
    intervalValue: String,
    selectedDays: Int,
    selectedDate: String,
    context: android.content.Context
): String? {
    return when (type) {
        ScheduleRuleTypeOption.INTERVAL -> {
            val value = intervalValue.toIntOrNull()
            if (value == null || value <= 0) {
                context.getString(R.string.schedule_rule_error_invalid_interval)
            } else null
        }
        ScheduleRuleTypeOption.WEEKLY -> {
            if (selectedDays == 0) {
                context.getString(R.string.schedule_rule_error_no_days)
            } else null
        }
        ScheduleRuleTypeOption.ONETIME -> {
            try {
                val date = LocalDate.parse(selectedDate)
                if (date.isBefore(LocalDate.now())) {
                    context.getString(R.string.schedule_rule_error_past_date)
                } else null
            } catch (e: Exception) {
                context.getString(R.string.schedule_rule_error_past_date)
            }
        }
        else -> null
    }
}
