package com.chatroom.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chatroom.app.R
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.SessionType
import com.chatroom.app.ui.components.ChangeReviewSheet
import com.chatroom.app.ui.components.ChatBubble
import com.chatroom.app.ui.components.ThinkingIndicator
import com.chatroom.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val activeApiAccount by viewModel.activeApiAccount.collectAsState()
    val activeIdentity by viewModel.activeIdentity.collectAsState()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val messages = activeSession?.messages ?: emptyList()
    var showChangeReview by remember { mutableStateOf(false) }

    // Show scroll-to-bottom button when not at the last message
    val showScrollToBottom by remember {
        derivedStateOf {
            val msgs = activeSession?.messages ?: emptyList<ChatMessage>()
            if (msgs.isEmpty()) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisible != null && lastVisible.index < msgs.size - 1
            }
        }
    }

    // Auto-scroll to bottom only if user is near the bottom
    // Track the last AI message by ID for reliable regenerate callback
    val lastAiMessageId = remember(messages) {
        messages.lastOrNull { it.role == "assistant" }?.id
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages.lastOrNull()
            if (lastMessage?.role == "user") {
                // Always scroll to bottom when user sends a message
                listState.scrollToItem(messages.size - 1)
            } else {
                // For AI responses, only scroll if user is near the bottom
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                val nearBottom = lastVisible != null && lastVisible.index >= messages.size - 3
                if (nearBottom) {
                    listState.scrollToItem(messages.size - 1)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Custom top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.menu),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
                // Active indicators
                activeIdentity?.let { identity ->
                    if (identity.photoUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(identity.photoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = identity.name,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = identity.avatarEmoji,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = identity.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeApiAccount != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeApiAccount!!.name.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.toggleChatSettings() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Chat topic header (pinned, auto-generated)
        if (activeSession != null && messages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = activeSession!!.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val subtitle = buildString {
                        if (activeIdentity != null) append(activeIdentity!!.name)
                        if (activeApiAccount != null) {
                            if (isNotEmpty()) append(" · ")
                            append(activeApiAccount!!.name)
                        }
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Chat messages area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !uiState.isSending) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (activeApiAccount != null) stringResource(R.string.empty_chat_start)
                               else stringResource(R.string.no_api_key_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isLastAi = message.role == "assistant" && message.id == lastAiMessageId
                        val isRecallable = message.role == "user" && messages.indexOfLast { it.role == "user" } == messages.indexOf(message)
                        ChatBubble(
                            message = message,
                            isLastMessage = message == messages.lastOrNull(),
                            onRegenerate = if (isLastAi && !uiState.isSending)
                                ({ viewModel.regenerateLastResponse() }) else null,
                            onFollowUp = if (!uiState.isSending) ({ content ->
                                viewModel.setReferencedContent(content)
                            }) else null,
                            onRecall = if (!uiState.isSending && isRecallable)
                                ({ viewModel.recallLastMessage() }) else null,
                            onDelete = if (!uiState.isSending && message.role == "assistant")
                                ({ viewModel.deleteMessage(message.id) }) else null,
                            onRewrite = if (!uiState.isSending && message.role == "assistant")
                                ({ viewModel.rewriteMessage(message.id) }) else null,
                            searchSources = if (isLastAi) uiState.searchSources else emptyList()
                        )
                    }

                    if (uiState.isSending) {
                        item {
                            ThinkingIndicator(elapsedSeconds = uiState.thinkingElapsed)
                        }
                    }
                }
            }

            // Scroll-to-bottom floating button
            ScrollToBottomFab(
                visible = showScrollToBottom,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // Error display
        AnimatedVisibility(
            visible = uiState.error != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Text(
                text = uiState.error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Reference/quote bar — shows above input when user follows up on a message
        AnimatedVisibility(
            visible = uiState.referencedContent != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QuestionAnswer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiState.referencedContent ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.clearReferencedContent() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove reference",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Input area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Tool buttons column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Deep thinking toggle
                    val thinkBg by animateColorAsState(
                        targetValue = if (uiState.deepThinkingEnabled)
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200)
                    )
                    IconButton(
                        onClick = { viewModel.toggleDeepThinking() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(thinkBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = stringResource(R.string.deep_thinking),
                            tint = if (uiState.deepThinkingEnabled)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Web search toggle
                    val webSearchBg by animateColorAsState(
                        targetValue = if (uiState.webSearchEnabled)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200)
                    )
                    IconButton(
                        onClick = { viewModel.toggleWebSearch() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(webSearchBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TravelExplore,
                            contentDescription = stringResource(R.string.web_search),
                            tint = if (uiState.webSearchEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text input
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    placeholder = {
                        Text(
                            stringResource(R.string.input_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 1,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send/Stop button
                val isGenerating = uiState.isSending
                val btnBg by animateColorAsState(
                    targetValue = if (isGenerating)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else if (uiState.inputText.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(200)
                )

                IconButton(
                    onClick = {
                        if (isGenerating) {
                            viewModel.stopSending()
                        } else {
                            viewModel.sendMessage()
                            focusManager.clearFocus()
                        }
                    },
                    enabled = isGenerating || uiState.inputText.isNotBlank(),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(btnBg)
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.Send,
                        contentDescription = stringResource(
                            if (isGenerating) R.string.stop else R.string.send
                        ),
                        tint = if (isGenerating)
                            MaterialTheme.colorScheme.onError
                        else if (uiState.inputText.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // Bottom sheet for chat settings
    if (uiState.showChatSettings) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val allSessions by viewModel.sessions.collectAsState()

        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleChatSettings() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Text(
                    text = "Chat Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ── System Prompt ──
                Text(
                    text = "System Prompt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.editableSystemPrompt,
                    onValueChange = { viewModel.updateSystemPrompt(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveSystemPrompt() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Prompt")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // ── Share Account Info ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAccountInfoSharing() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Share Account Info",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Let AI know your current account & model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.shareAccountInfo,
                        onCheckedChange = { viewModel.toggleAccountInfoSharing() }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // ── Review Changes (coding assistant only) ──
                if (activeSession?.isCodingAssistant == true &&
                    activeSession!!.pendingChanges.isNotEmpty()) {
                    Button(
                        onClick = {
                            viewModel.toggleChatSettings()
                            showChangeReview = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.view_changes) + " (${activeSession!!.pendingChanges.size})")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Select Reference Sessions ──
                Text(
                    text = "AI Can Read These Conversations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select sessions for AI context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (allSessions.isEmpty()) {
                    Text(
                        text = "No other conversations yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        allSessions.forEach { session ->
                            val isSelected = session.id in uiState.selectedSessionIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSessionSelection(session.id) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSessionSelection(session.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${session.messages.size} messages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Change review sheet (for coding assistant sessions)
    if (showChangeReview) {
        val changes = activeSession?.pendingChanges?.filter {
            it.status == com.chatroom.app.data.model.ChangeStatus.PENDING
        } ?: emptyList()
        ChangeReviewSheet(
            changes = changes,
            onAcceptAll = { viewModel.acceptAllChanges() },
            onRejectAll = { viewModel.rejectAllChanges() },
            onAcceptOne = { changeId -> viewModel.acceptChange(changeId) },
            onRejectOne = { changeId -> viewModel.rejectChange(changeId) },
            onCommit = { message ->
                viewModel.commitChanges(message)
                showChangeReview = false
            },
            onDismiss = { showChangeReview = false }
        )
    }
}

@Composable
private fun ScrollToBottomFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = modifier
                .padding(end = 16.dp, bottom = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll to bottom",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
