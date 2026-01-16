package com.personalcoacher.ui.screens.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personalcoacher.R
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageRole
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.ui.components.PageHeader
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    viewModel: CoachViewModel = hiltViewModel(),
    initialCoachMessage: String? = null,
    initialConversationId: String? = null,
    onConsumeInitialMessage: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle initial coach message from notification (dynamic AI notification)
    LaunchedEffect(initialCoachMessage) {
        if (initialCoachMessage != null) {
            viewModel.startConversationWithCoachMessage(initialCoachMessage)
            onConsumeInitialMessage()
        }
    }

    // Handle initial conversation ID from notification (chat response notification)
    LaunchedEffect(initialConversationId) {
        if (initialConversationId != null) {
            viewModel.selectConversation(initialConversationId)
            onConsumeInitialMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Debug dialog
    if (uiState.showDebugDialog) {
        DebugLogDialog(
            logs = uiState.debugLogs,
            onDismiss = viewModel::dismissDebugDialog
        )
    }

    if (uiState.showConversationList) {
        ConversationListScreen(
            conversations = uiState.conversations,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            onConversationClick = viewModel::selectConversation,
            onNewConversation = viewModel::startNewConversation,
            onDeleteConversation = { viewModel.deleteConversation(it.conversation) },
            snackbarHostState = snackbarHostState
        )
    } else {
        ChatScreen(
            messages = uiState.messages,
            messageInput = uiState.messageInput,
            isSending = uiState.isSending,
            pendingMessageId = uiState.pendingMessageId,
            streamingContent = uiState.streamingContent,
            isStreaming = uiState.isStreaming,
            isDebugMode = uiState.isDebugMode,
            onMessageInputChange = viewModel::updateMessageInput,
            onSendMessage = viewModel::sendMessage,
            onSendMessageWithDebug = viewModel::sendMessageWithDebug,
            onShowDebugLogs = viewModel::showDebugLogs,
            onBack = viewModel::backToConversationList,
            snackbarHostState = snackbarHostState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListScreen(
    conversations: List<ConversationWithLastMessage>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onConversationClick: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (ConversationWithLastMessage) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Scaffold(
        topBar = {
            PageHeader(
                title = stringResource(R.string.coach_title),
                icon = Icons.AutoMirrored.Filled.Chat,
                gradientColors = listOf(Color(0xFF7DD3C0), Color(0xFF6BC4B3)),
                subtitle = stringResource(R.string.coach_subtitle)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.coach_new_conversation))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (conversations.isEmpty()) {
                EmptyConversationsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(IOSSpacing.screenPadding), // Increased padding
                    verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing) // Increased spacing
                ) {
                    items(conversations, key = { it.conversation.id }) { item ->
                        ConversationCard(
                            item = item,
                            onClick = { onConversationClick(item.conversation.id) },
                            onDelete = { onDeleteConversation(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.coach_welcome_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.coach_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ConversationCard(
    item: ConversationWithLastMessage,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IOSSpacing.cardPadding), // Increased padding
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.conversation.title ?: "New Conversation",
                    style = MaterialTheme.typography.titleMedium, // Slightly larger
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp)) // Increased spacing
                item.lastMessage?.let { msg ->
                    Text(
                        text = msg.content.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.conversation.updatedAt.atZone(ZoneId.systemDefault()).format(dateFormatter),
                    style = MaterialTheme.typography.labelSmall, // Smaller metadata
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Lighter
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    messages: List<Message>,
    messageInput: String,
    isSending: Boolean,
    pendingMessageId: String?,
    streamingContent: String,
    isStreaming: Boolean,
    isDebugMode: Boolean,
    onMessageInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendMessageWithDebug: () -> Unit,
    onShowDebugLogs: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val listState = rememberLazyListState()

    // Scroll to bottom when messages change or streaming starts
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty() || isStreaming) {
            // Calculate target index: messages count, plus 1 if streaming placeholder is shown
            val hasStreamingPlaceholder = isStreaming && pendingMessageId != null && messages.none { it.id == pendingMessageId }
            val targetIndex = messages.size - 1 + (if (hasStreamingPlaceholder) 1 else 0)
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.coach_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show debug logs button while in debug mode streaming
                    if (isDebugMode && isStreaming) {
                        IconButton(onClick = onShowDebugLogs) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = "View debug logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.coach_welcome_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.coach_welcome_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { message ->
                        val isThisMessageStreaming = message.id == pendingMessageId && isStreaming
                        MessageBubble(
                            message = message,
                            isPending = message.id == pendingMessageId && !isStreaming,
                            isStreaming = isThisMessageStreaming,
                            streamingContent = if (isThisMessageStreaming) streamingContent else null
                        )
                    }

                    // Show streaming bubble if we're streaming but the message isn't in the list yet
                    if (isStreaming && pendingMessageId != null && messages.none { it.id == pendingMessageId }) {
                        item(key = "streaming_placeholder") {
                            StreamingMessageBubble(
                                streamingContent = streamingContent
                            )
                        }
                    }
                }
            }

            // Input area - sits directly above the bottom navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = onMessageInputChange,
                        placeholder = { Text(stringResource(R.string.coach_input_placeholder)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isSending
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Debug send button
                    IconButton(
                        onClick = onSendMessageWithDebug,
                        enabled = messageInput.isNotBlank() && !isSending
                    ) {
                        Icon(
                            Icons.Filled.BugReport,
                            contentDescription = "Send with debug",
                            tint = if (messageInput.isNotBlank())
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Regular send button
                    IconButton(
                        onClick = onSendMessage,
                        enabled = messageInput.isNotBlank() && !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.coach_send),
                                tint = if (messageInput.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isPending: Boolean,
    isStreaming: Boolean = false,
    streamingContent: String? = null
) {
    val isUser = message.role == MessageRole.USER
    val extendedColors = PersonalCoachTheme.extendedColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) extendedColors.userBubble else extendedColors.assistantBubble,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                when {
                    // Show streaming content with typing indicator
                    isStreaming && !streamingContent.isNullOrEmpty() -> {
                        Column {
                            MarkdownText(
                                markdown = streamingContent,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = extendedColors.onAssistantBubble
                                )
                            )
                            // Typing cursor indicator
                            Text(
                                text = "▊",
                                style = MaterialTheme.typography.bodyMedium,
                                color = extendedColors.onAssistantBubble.copy(alpha = 0.5f)
                            )
                        }
                    }
                    // Show loading dots while waiting for stream to start
                    isStreaming && streamingContent.isNullOrEmpty() -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(8.dp),
                                    strokeWidth = 2.dp,
                                    color = extendedColors.onAssistantBubble.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    // Show loading dots for pending (non-streaming)
                    message.status == MessageStatus.PENDING || isPending -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(8.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isUser)
                                        extendedColors.onUserBubble.copy(alpha = 0.7f)
                                    else
                                        extendedColors.onAssistantBubble.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    // Show completed message content
                    else -> {
                        if (isUser) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = extendedColors.onUserBubble
                            )
                        } else {
                            MarkdownText(
                                markdown = message.content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = extendedColors.onAssistantBubble
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingMessageBubble(
    streamingContent: String
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            color = extendedColors.assistantBubble,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                if (streamingContent.isNotEmpty()) {
                    Column {
                        MarkdownText(
                            markdown = streamingContent,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = extendedColors.onAssistantBubble
                            )
                        )
                        // Typing cursor indicator
                        Text(
                            text = "▊",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extendedColors.onAssistantBubble.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // Show loading dots while waiting for stream to start
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(8.dp),
                                strokeWidth = 2.dp,
                                color = extendedColors.onAssistantBubble.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLogDialog(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Debug Logs")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    logs.forEach { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = if (log.contains("Error") || log.contains("error"))
                                MaterialTheme.colorScheme.error
                            else if (log.contains("Complete") || log.contains("success"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs captured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
