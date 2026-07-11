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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chatroom.app.R
import com.chatroom.app.data.model.AiAccessLevel
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ExecutionStep
import com.chatroom.app.data.model.SessionType
import com.chatroom.app.ui.components.AgentExecutionSteps
import com.chatroom.app.ui.components.ChangeReviewSheet
import com.chatroom.app.ui.components.ChatBubble
import com.chatroom.app.ui.components.DevToolConfig
import com.chatroom.app.ui.components.DevToolPanel
import com.chatroom.app.ui.components.ThinkingIndicator
import com.chatroom.app.viewmodel.ChatViewModel
import com.chatroom.app.viewmodel.CodexViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    codexViewModel: CodexViewModel? = null,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val activeApiAccount by viewModel.activeApiAccount.collectAsState()
    val activeIdentity by viewModel.activeIdentity.collectAsState()
    val codexUiState by codexViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val messages = activeSession?.messages ?: emptyList()
    var showChangeReview by remember { mutableStateOf(false) }

    // Agent mode detection
    val isAgentMode = activeSession?.isCodingAssistant == true
    val isAgentRunning = codexUiState?.isAgentRunning == true

    // File preview dialog state
    var showFilePreview by remember { mutableStateOf(false) }
    var previewFilePath by remember { mutableStateOf("") }

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
                activeApiAccount?.let { account ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = account.name.take(1),
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
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Chat topic header (pinned, auto-generated)
        val sessionForHeader = activeSession
        if (sessionForHeader != null && messages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = sessionForHeader.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val subtitle = buildString {
                        activeIdentity?.let { append(it.name) }
                        activeApiAccount?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it.name)
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
                            onTranslate = if (!uiState.isSending && message.role == "assistant")
                                ({ content, lang -> viewModel.translateMessage(content, lang) }) else null,
                            searchSources = if (isLastAi) uiState.searchSources else emptyList(),
                            onFileClick = { path ->
                                previewFilePath = path
                                showFilePreview = true
                            },
                            onFileSelect = { path -> viewModel.setSelectedFile(path) }
                        )
                    }

                    if (uiState.isSending) {
                        item {
                            ThinkingIndicator(elapsedSeconds = uiState.thinkingElapsed)
                        }
                    }

                    // Agent running indicator
                    val currentAgentState = codexUiState
                    if (isAgentRunning && currentAgentState != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                // Thinking text
                                if (currentAgentState.thinkingText.isNotBlank()) {
                                    Text(
                                        text = currentAgentState.thinkingText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                // Agent events as live execution steps
                                val liveSteps = currentAgentState.agentEvents.mapNotNull { event ->
                                    when (event) {
                                        is com.chatroom.app.data.model.AgentEvent.ReadingFile ->
                                            ExecutionStep(0, "read", "Reading ${event.path}", success = true)
                                        is com.chatroom.app.data.model.AgentEvent.WritingFile ->
                                            ExecutionStep(0, "write", "Writing ${event.path}", success = true)
                                        is com.chatroom.app.data.model.AgentEvent.CommandOutput ->
                                            ExecutionStep(0, "cmd", event.line.take(100), success = true)
                                        else -> null
                                    }
                                }
                                if (liveSteps.isNotEmpty()) {
                                    AgentExecutionSteps(steps = liveSteps, isRunning = true)
                                }

                                // Error display
                                if (currentAgentState.error != null) {
                                    Text(
                                        text = currentAgentState.error ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
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
                        contentDescription = stringResource(R.string.remove_reference),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Selected file reference bar — shows above input when user picks a file from AI changes
        AnimatedVisibility(
            visible = uiState.selectedFilePath != null,
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
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiState.selectedFilePath ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.clearSelectedFile() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.remove_file_reference),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Dev Tool Panel (agent mode)
        if (isAgentMode && codexUiState?.showDevTools == true && codexViewModel != null) {
            DevToolPanel(
                config = codexUiState?.devToolConfig ?: DevToolConfig(),
                onConfigChange = { codexViewModel.updateDevToolConfig(it) },
                onExecute = { codexViewModel.executeDevTool() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // Dev tool result
        if (isAgentMode && codexUiState?.devToolResult != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dev_tool_result_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            codexViewModel?.let { vm ->
                                vm.updateDevToolConfig(vm.uiState.value.devToolConfig)
                            }
                        }) {
                            Text(stringResource(R.string.dev_tool_result_clear), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = codexUiState?.devToolResult ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
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

                    // Dev tools toggle (agent mode only)
                    if (isAgentMode && codexViewModel != null) {
                        val devToolBg by animateColorAsState(
                            targetValue = if (codexUiState?.showDevTools == true)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.surface,
                            animationSpec = tween(200)
                        )
                        IconButton(
                            onClick = { codexViewModel.toggleDevTools() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(devToolBg)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Construction,
                                contentDescription = stringResource(R.string.dev_tools_title),
                                tint = if (codexUiState?.showDevTools == true)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

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
                        if (isGenerating || isAgentRunning) {
                            if (isAgentRunning && codexViewModel != null) {
                                codexViewModel.stopAgent()
                            } else {
                                viewModel.stopSending()
                            }
                        } else {
                            if (isAgentMode && codexViewModel != null) {
                                codexViewModel.startAgentLoop(uiState.inputText)
                                viewModel.updateInput("")
                            } else {
                                viewModel.sendMessage()
                            }
                            focusManager.clearFocus()
                        }
                    },
                    enabled = isGenerating || isAgentRunning || uiState.inputText.isNotBlank(),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(btnBg)
                ) {
                    Icon(
                        imageVector = if (isGenerating || isAgentRunning) Icons.Default.Stop else Icons.Default.Send,
                        contentDescription = stringResource(
                            if (isGenerating || isAgentRunning) R.string.stop else R.string.send
                        ),
                        tint = if (isGenerating || isAgentRunning)
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = stringResource(R.string.chat_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ── System Prompt ──
                Text(
                    text = stringResource(R.string.system_prompt),
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
                    Text(stringResource(R.string.save_prompt))
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
                            text = stringResource(R.string.share_account_info),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.account_info_desc),
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

                // ── 应用语言切换 ──
                val contextForLang = LocalContext.current
                var currentLang by remember { mutableStateOf(
                    contextForLang.getSharedPreferences("settings_preferences", 0)
                        .getString("language", "zh-CN") ?: "zh-CN"
                ) }
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                val langOptions = listOf(
                    "zh-CN" to stringResource(R.string.language_zh_cn),
                    "zh-TW" to stringResource(R.string.language_zh_tw),
                    "en" to stringResource(R.string.language_en)
                )
                langOptions.forEach { (code, label) ->
                    val selected = currentLang == code
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .clickable {
                                currentLang = code
                                contextForLang.getSharedPreferences("settings_preferences", 0)
                                    .edit().putString("language", code).commit()
                                // Signal recreate to apply new locale
                                (contextForLang as? android.app.Activity)?.recreate()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                val caSession = activeSession
                if (caSession?.isCodingAssistant == true) {
                    val session = caSession

                    // AI Access Level
                    Text(
                        text = stringResource(R.string.ai_access_level),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ai_access_level_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val levels = AiAccessLevel.values()
                    levels.forEach { level ->
                        val label = when (level) {
                            AiAccessLevel.READ_ONLY -> stringResource(R.string.access_read_only)
                            AiAccessLevel.READ_WRITE -> stringResource(R.string.access_read_write)
                            AiAccessLevel.FULL_ACCESS -> stringResource(R.string.access_full)
                        }
                        val desc = when (level) {
                            AiAccessLevel.READ_ONLY -> stringResource(R.string.access_read_only_desc)
                            AiAccessLevel.READ_WRITE -> stringResource(R.string.access_read_write_desc)
                            AiAccessLevel.FULL_ACCESS -> stringResource(R.string.access_full_desc)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateAiAccessLevel(level) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = session.aiAccessLevel == level,
                                onClick = { viewModel.updateAiAccessLevel(level) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Connection Status
                    Text(
                        text = stringResource(R.string.connection_status),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (session.repoUrl.isNotBlank()) {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = stringResource(R.string.repo_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = session.repoDisplayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = stringResource(R.string.branch_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = session.repoBranch,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = stringResource(R.string.status_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (session.repoToken.isNotBlank()) stringResource(R.string.connected) else stringResource(R.string.not_authenticated),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (session.repoToken.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.no_repo_connected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Show AI Changes toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateShowAiChanges(!session.showAiChanges) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.show_ai_changes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.show_ai_changes_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = session.showAiChanges,
                            onCheckedChange = { viewModel.updateShowAiChanges(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Review Changes (coding assistant only) ──
                val sessionForChanges = activeSession
                if (sessionForChanges?.isCodingAssistant == true &&
                    !sessionForChanges.pendingChanges.isNullOrEmpty()) {
                    val changeCount = sessionForChanges.pendingChanges.size
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
                        Text(stringResource(R.string.view_changes) + " ($changeCount)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Select Reference Sessions ──
                Text(
                    text = stringResource(R.string.ai_read_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.ai_read_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (allSessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_other_sessions),
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
                                        text = stringResource(R.string.messages_count, session.messages.size),
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

    // Dangerous command confirmation dialog (agent mode)
    codexUiState?.confirmDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { codexViewModel?.rejectCommand() },
            title = { Text(stringResource(R.string.dangerous_command_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dangerous_command_desc))
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = dialog.command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { codexViewModel?.confirmCommand() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.dangerous_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { codexViewModel?.rejectCommand() }) {
                    Text(stringResource(R.string.dangerous_deny))
                }
            }
        )
    }

    // File preview dialog — shows file content in read-only mode when user clicks a file chip
    if (showFilePreview && previewFilePath.isNotBlank()) {
        val session = activeSession
        val fileContent = remember(session, previewFilePath) {
            val repoDir = if (session?.repoDir?.isNotBlank() == true) java.io.File(session.repoDir)
                else if (session?.localPath?.isNotBlank() == true && session?.repoName?.isNotBlank() == true)
                    java.io.File(session.localPath, session.repoName)
                else null
            if (repoDir != null) {
                val file = repoDir.resolve(previewFilePath)
                if (file.exists() && file.isFile && file.length() < 512_000) {
                    try { file.readText() }
                    catch (_: Exception) { "Error reading file" }
                } else {
                    "File not found or too large"
                }
            } else {
                "No repository configured"
            }
        }
        AlertDialog(
            onDismissRequest = { showFilePreview = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = previewFilePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = previewFilePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = fileContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSelectedFile(previewFilePath)
                    showFilePreview = false
                }) {
                    Text(stringResource(R.string.select_for_chat))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilePreview = false }) {
                    Text(stringResource(R.string.close))
                }
            }
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
                contentDescription = stringResource(R.string.scroll_to_bottom),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
