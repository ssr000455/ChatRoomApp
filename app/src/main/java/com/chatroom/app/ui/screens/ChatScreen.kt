package com.chatroom.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // Auto-scroll to bottom
    val messages = activeSession?.messages ?: emptyList()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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
                    items(messages) { message ->
                        ChatBubble(
                            message = message,
                            isLastMessage = message == messages.lastOrNull(),
                            onRegenerate = if (message.role == "assistant" && message == messages.lastOrNull())
                                { viewModel.regenerateLastResponse() } else null
                        )
                    }

                    if (uiState.isSending) {
                        item {
                            ThinkingIndicator()
                        }
                    }
                }
            }
        }

        // Error display
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

        // Input area
        Box(
            modifier = Modifier
                .fillMaxWidth()
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

                // Send button
                val sendBg by animateColorAsState(
                    targetValue = if (uiState.inputText.isNotBlank() && !uiState.isSending)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(200)
                )

                IconButton(
                    onClick = {
                        viewModel.sendMessage()
                        focusManager.clearFocus()
                    },
                    enabled = uiState.inputText.isNotBlank() && !uiState.isSending,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(sendBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = if (uiState.inputText.isNotBlank() && !uiState.isSending)
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
