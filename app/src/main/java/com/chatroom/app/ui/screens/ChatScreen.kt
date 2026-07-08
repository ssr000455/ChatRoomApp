package com.chatroom.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chatroom.app.R
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.ui.components.ChatBubble
import com.chatroom.app.ui.components.ThinkingIndicator
import com.chatroom.app.viewmodel.ChatViewModel

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

    // Auto-scroll to bottom only if user is near the bottom
    val messages = activeSession?.messages ?: emptyList()
    // Track the last AI message by ID for reliable regenerate callback
    val lastAiMessageId = remember(messages) {
        messages.lastOrNull { it.role == "assistant" }?.id
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val nearBottom = lastVisible != null && lastVisible.index >= messages.size - 3
            if (nearBottom) {
                listState.scrollToItem(messages.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        ChatBubble(
                            message = message,
                            isLastMessage = message == messages.lastOrNull(),
                            onRegenerate = if (isLastAi && !uiState.isSending)
                                ({ viewModel.regenerateLastResponse() }) else null,
                            onFollowUp = if (!uiState.isSending) ({ content ->
                                viewModel.updateInput("\"$content\" ")
                            }) else null,
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
        }

        // Error display
        AnimatedVisibility(
            visible = uiState.error != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Input area — imePadding() moves it up when keyboard is shown
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
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
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            viewModel.sendMessage()
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true
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
}
