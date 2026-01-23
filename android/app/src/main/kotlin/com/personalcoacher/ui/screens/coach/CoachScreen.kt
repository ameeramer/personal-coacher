package com.personalcoacher.ui.screens.coach

import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageRole
import com.personalcoacher.domain.model.MessageStatus
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
            isTyping = uiState.isTyping,
            onMessageInputChange = viewModel::updateMessageInput,
            onSendMessage = viewModel::sendMessage,
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
                                    listOf(Color(0xFF7DD3C0), Color(0xFF6BC4B3))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.coach_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (conversations.isEmpty()) {
                    EmptyConversationsState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(IOSSpacing.screenPadding),
                        verticalArrangement = Arrangement.spacedBy(IOSSpacing.listItemSpacing)
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
                .padding(IOSSpacing.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.conversation.title ?: "New Conversation",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    isTyping: Boolean,
    onMessageInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val listState = rememberLazyListState()

    // Scroll to bottom when messages change or typing indicator shows
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty() || isTyping) {
            // Calculate target index: messages count, plus 1 if typing indicator is shown
            val targetIndex = messages.size - 1 + (if (isTyping) 1 else 0)
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
                        MessageBubble(
                            message = message,
                            isPending = message.status == MessageStatus.PENDING
                        )
                    }

                    // Show typing indicator (WhatsApp-style) when waiting for response
                    if (isTyping) {
                        item(key = "typing_indicator") {
                            TypingIndicatorBubble()
                        }
                    }
                }
            }

            // Input area
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
                Spacer(modifier = Modifier.width(8.dp))
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
    isPending: Boolean
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
                    // Show loading dots for pending messages
                    (message.status == MessageStatus.PENDING || isPending) && message.content.isEmpty() -> {
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

/**
 * WhatsApp-style typing indicator bubble.
 * Shows animated dots to indicate the coach is "typing".
 */
@Composable
private fun TypingIndicatorBubble() {
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
            modifier = Modifier.widthIn(max = 100.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated typing dots
                    repeat(3) { index ->
                        TypingDot(delayMs = index * 200)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingDot(delayMs: Int) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(
        label = "typing_dot"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 1000
                0.3f at 0
                1f at 400
                0.3f at 800
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
            initialStartOffset = androidx.compose.animation.core.StartOffset(delayMs)
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(extendedColors.onAssistantBubble.copy(alpha = alpha))
    )
}
