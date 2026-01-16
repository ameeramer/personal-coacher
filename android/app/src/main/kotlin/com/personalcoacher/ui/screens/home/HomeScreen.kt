package com.personalcoacher.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onNavigateToJournal: () -> Unit,
    onNavigateToCoach: () -> Unit,
    onNavigateToSummaries: () -> Unit,
    onNavigateToAgenda: () -> Unit = {},
    onNavigateToRecorder: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val extendedColors = PersonalCoachTheme.extendedColors

    // Refresh time of day when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshTimeOfDay()
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(IOSSpacing.screenPadding)
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Greeting Section
                GreetingSection(
                    userName = uiState.userName,
                    timeOfDay = uiState.timeOfDay
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Stats Cards
                StatsRow(
                    totalEntries = uiState.totalEntries,
                    currentStreak = uiState.currentStreak,
                    hasEntryToday = uiState.hasEntryToday
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Event Suggestions Section (if any)
                if (uiState.pendingEventSuggestions.isNotEmpty()) {
                    EventSuggestionsSection(
                        suggestions = uiState.pendingEventSuggestions,
                        onAccept = viewModel::acceptEventSuggestion,
                        onReject = viewModel::rejectEventSuggestion
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Upcoming Events Section (if any)
                if (uiState.upcomingAgendaItems.isNotEmpty()) {
                    UpcomingEventsSection(
                        items = uiState.upcomingAgendaItems,
                        onNavigateToAgenda = onNavigateToAgenda
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Journal Encouragement Card
                JournalEncouragementCard(
                    hasEntryToday = uiState.hasEntryToday,
                    recentMood = uiState.recentMood?.emoji,
                    onStartJournaling = onNavigateToJournal
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Actions
                QuickActionsSection(
                    onNavigateToJournal = onNavigateToJournal,
                    onNavigateToCoach = onNavigateToCoach,
                    onNavigateToSummaries = onNavigateToSummaries,
                    onNavigateToRecorder = onNavigateToRecorder
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun GreetingSection(
    userName: String,
    timeOfDay: TimeOfDay
) {
    val (greetingResId, icon, gradientColors) = when (timeOfDay) {
        TimeOfDay.MORNING -> Triple(
            R.string.home_greeting_morning,
            Icons.Outlined.WbSunny,
            listOf(Color(0xFFFFD54F), Color(0xFFFFB74D))
        )
        TimeOfDay.AFTERNOON -> Triple(
            R.string.home_greeting_afternoon,
            Icons.Outlined.LightMode,
            listOf(Color(0xFF81D4FA), Color(0xFF4FC3F7))
        )
        TimeOfDay.EVENING -> Triple(
            R.string.home_greeting_evening,
            Icons.Outlined.WbTwilight,
            listOf(Color(0xFFFFAB91), Color(0xFFFF8A65))
        )
        TimeOfDay.NIGHT -> Triple(
            R.string.home_greeting_night,
            Icons.Outlined.NightsStay,
            listOf(Color(0xFF9575CD), Color(0xFF7E57C2))
        )
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(gradientColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(greetingResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    totalEntries: Int,
    currentStreak: Int,
    hasEntryToday: Boolean
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Total Entries Card
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.MenuBook,
            iconTint = MaterialTheme.colorScheme.primary,
            value = totalEntries.toString(),
            label = stringResource(R.string.home_stat_entries)
        )

        // Streak Card
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LocalFireDepartment,
            iconTint = if (currentStreak > 0) Color(0xFFFF7043) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            value = currentStreak.toString(),
            label = stringResource(R.string.home_stat_streak)
        )

        // Today Status Card
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Edit,
            iconTint = if (hasEntryToday) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            value = if (hasEntryToday) "✓" else "—",
            label = stringResource(R.string.home_stat_today)
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Card(
        modifier = modifier,
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun JournalEncouragementCard(
    hasEntryToday: Boolean,
    recentMood: String?,
    onStartJournaling: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IOSSpacing.cardPaddingLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (hasEntryToday) {
                        stringResource(R.string.home_journal_completed_title)
                    } else {
                        stringResource(R.string.home_journal_prompt_title)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (hasEntryToday) {
                    if (recentMood != null) {
                        stringResource(R.string.home_journal_completed_with_mood, recentMood)
                    } else {
                        stringResource(R.string.home_journal_completed_message)
                    }
                } else {
                    stringResource(R.string.home_journal_prompt_message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartJournaling,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasEntryToday) {
                        stringResource(R.string.home_journal_add_more)
                    } else {
                        stringResource(R.string.home_journal_start)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onNavigateToJournal: () -> Unit,
    onNavigateToCoach: () -> Unit,
    onNavigateToSummaries: () -> Unit,
    onNavigateToRecorder: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Column {
        Text(
            text = stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Coach Action
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Chat,
                label = stringResource(R.string.home_action_coach),
                gradientColors = listOf(Color(0xFF7DD3C0), Color(0xFF6BC4B3)),
                onClick = onNavigateToCoach
            )

            // Summaries Action
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Insights,
                label = stringResource(R.string.home_action_summaries),
                gradientColors = listOf(Color(0xFF9D8FE8), Color(0xFF8B82D1)),
                onClick = onNavigateToSummaries
            )

            // Recorder Action
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Mic,
                label = stringResource(R.string.home_action_recorder),
                gradientColors = listOf(Color(0xFFE57373), Color(0xFFEF5350)),
                onClick = onNavigateToRecorder
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(gradientColors),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EventSuggestionsSection(
    suggestions: List<EventSuggestion>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.event_suggestions_title),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        suggestions.forEach { suggestion ->
            EventSuggestionCard(
                suggestion = suggestion,
                onAccept = { onAccept(suggestion.id) },
                onReject = { onReject(suggestion.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EventSuggestionCard(
    suggestion: EventSuggestion,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val zonedStartTime = suggestion.suggestedStartTime.atZone(ZoneId.systemDefault())

    val timeText = if (suggestion.isAllDay) {
        stringResource(R.string.agenda_all_day)
    } else {
        zonedStartTime.format(timeFormatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${zonedStartTime.format(dateFormatter)} at $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!suggestion.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.event_suggestion_reject),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.event_suggestion_accept),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingEventsSection(
    items: List<AgendaItem>,
    onNavigateToAgenda: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.agenda_title),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNavigateToAgenda, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.agenda_add_item),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            onClick = onNavigateToAgenda,
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
                    .padding(16.dp)
            ) {
                items.forEachIndexed { index, item ->
                    UpcomingEventItem(item = item)
                    if (index < items.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingEventItem(item: AgendaItem) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val zonedStartTime = item.startTime.atZone(ZoneId.systemDefault())

    val timeText = if (item.isAllDay) {
        stringResource(R.string.agenda_all_day)
    } else {
        zonedStartTime.format(timeFormatter)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${zonedStartTime.format(dateFormatter)} • $timeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
