package com.personalcoacher.ui.screens.summaries

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    viewModel: SummariesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val extendedColors = PersonalCoachTheme.extendedColors
    var showGenerateDialog by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showGenerateDialog) {
        GenerateSummaryDialog(
            isGenerating = uiState.isGenerating,
            onDismiss = { showGenerateDialog = false },
            onGenerate = { type ->
                viewModel.generateSummary(type)
                showGenerateDialog = false
            }
        )
    }

    if (uiState.selectedSummary != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::clearSelectedSummary,
            sheetState = bottomSheetState
        ) {
            SummaryDetailContent(
                summary = uiState.selectedSummary!!,
                onClose = viewModel::clearSelectedSummary
            )
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showGenerateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.summaries_generate))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                                    listOf(Color(0xFF9D8FE8), Color(0xFF8B82D1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.summaries_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedType == null,
                            onClick = { viewModel.filterByType(null) },
                            label = { Text("All") }
                        )
                    }
                    items(SummaryType.entries) { type ->
                        FilterChip(
                            selected = uiState.selectedType == type,
                            onClick = { viewModel.filterByType(type) },
                            label = { Text(type.displayName()) }
                        )
                    }
                }

                if (uiState.summaries.isEmpty() && !uiState.isLoading) {
                    EmptySummariesState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(IOSSpacing.screenPadding), // Increased padding
                        verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing) // Increased spacing
                    ) {
                        items(uiState.summaries, key = { it.id }) { summary ->
                            SummaryCard(
                                summary = summary,
                                onClick = { viewModel.selectSummary(summary) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySummariesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Insights,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.summaries_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.summaries_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SummaryCard(
    summary: Summary,
    onClick: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }

    // iOS-style translucent card
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = extendedColors.translucentSurface
        ),
        border = BorderStroke(0.5.dp, extendedColors.thinBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IOSSpacing.cardPadding) // Increased padding
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.type.displayName(),
                    style = MaterialTheme.typography.titleMedium, // Slightly larger
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = summary.createdAt.atZone(ZoneId.systemDefault()).format(dateFormatter),
                    style = MaterialTheme.typography.labelSmall, // Smaller metadata
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Lighter
                )
            }

            Spacer(modifier = Modifier.height(12.dp)) // Increased spacing

            MarkdownText(
                markdown = summary.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 4,
                truncateOnTextOverflow = true
            )
        }
    }
}

@Composable
private fun GenerateSummaryDialog(
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (SummaryType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.summaries_generate)) },
        text = {
            Column {
                Text("Select a time period for your summary:")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryType.entries.forEach { type ->
                        TextButton(
                            onClick = { onGenerate(type) },
                            enabled = !isGenerating
                        ) {
                            Text(type.displayName())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryDetailContent(
    summary: Summary,
    onClose: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${summary.type.displayName()} Summary",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${summary.startDate.atZone(ZoneId.systemDefault()).format(dateFormatter)} - ${summary.endDate.atZone(ZoneId.systemDefault()).format(dateFormatter)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        MarkdownText(
            markdown = summary.content,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
