package com.chatroom.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatroom.app.R
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ExecutionStep
import com.chatroom.app.data.model.FileChangeSummary
import com.chatroom.app.data.model.TerminalCommand
import com.chatroom.app.ui.theme.AiBubbleDark
import com.chatroom.app.ui.theme.AiBubbleLight
import com.chatroom.app.ui.theme.AiTextDark
import com.chatroom.app.ui.theme.AiTextLight
import com.chatroom.app.ui.theme.UserBubbleDark
import com.chatroom.app.ui.theme.UserBubbleLight
import com.chatroom.app.ui.theme.UserTextDark
import com.chatroom.app.ui.theme.UserTextLight

@Composable
fun ChatBubble(
    message: ChatMessage,
    isLastMessage: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
    onFollowUp: ((String) -> Unit)? = null,
    onRecall: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRewrite: (() -> Unit)? = null,
    onTranslate: ((String, String) -> Unit)? = null,
    searchSources: List<String> = emptyList(),
    onFileClick: ((String) -> Unit)? = null,
    onFileSelect: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val bubbleColor = if (isUser) {
        if (isDark) UserBubbleDark else UserBubbleLight
    } else {
        if (isDark) AiBubbleDark else AiBubbleLight
    }

    val textColor = if (isUser) {
        if (isDark) UserTextDark else UserTextLight
    } else {
        if (isDark) AiTextDark else AiTextLight
    }

    val shape = if (isUser) {
        RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = 18.dp, bottomEnd = 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp, topEnd = 18.dp,
            bottomStart = 18.dp, bottomEnd = 18.dp
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label for AI messages
        if (!isUser) {
            Text(
                text = stringResource(R.string.ai_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        // Search sources (collapsible, shown before AI response)
        if (!isUser && searchSources.isNotEmpty()) {
            SearchSourcesSection(sources = searchSources)
        }

        Surface(
            shape = shape,
            color = bubbleColor,
            shadowElevation = 0.dp
        ) {
            Column {
                // Reasoning content (collapsible)
                if (!isUser && !message.reasoningContent.isNullOrBlank()) {
                    ReasoningSection(reasoning = message.reasoningContent)
                }

                if (isUser) {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                } else {
                    // File changes summary — gray chips above AI output
                    if (!message.fileChanges.isNullOrEmpty()) {
                        FileChangesSummary(
                            changes = message.fileChanges.orEmpty(),
                            onFileClick = onFileClick,
                            onFileSelect = onFileSelect
                        )
                    }

                    // Agent execution steps
                    if (message.isAgentMessage && message.executionSteps.isNotEmpty()) {
                        AgentExecutionSteps(
                            steps = message.executionSteps,
                            isRunning = false
                        )
                    }

                    MarkdownContent(
                        content = message.content,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )

                    // Terminal commands (shown after content)
                    if (!message.terminalCommands.isNullOrEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            message.terminalCommands.orEmpty().forEach { cmd ->
                                TerminalCommandBlock(cmd)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Action buttons
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it / 2 }
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 4.dp, top = 2.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                if (isUser) {
                    // --- User message actions ---
                    // Copy button
                    val copiedText = stringResource(R.string.copied_toast)
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Recall button (only on last user message)
                    if (isLastMessage && onRecall != null) {
                        IconButton(
                            onClick = onRecall,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = stringResource(R.string.recall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    // --- AI message actions ---
                    // Copy button
                    val copiedText = stringResource(R.string.copied_toast)
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Translate dropdown (Chinese, English, Japanese)
                    if (onTranslate != null) {
                        var translateExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { translateExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.translate),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = translateExpanded,
                                onDismissRequest = { translateExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.translate_lang_zh_cn)) },
                                    onClick = {
                                        translateExpanded = false
                                        onTranslate(message.content, "Simplified Chinese")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.translate_lang_zh_tw)) },
                                    onClick = {
                                        translateExpanded = false
                                        onTranslate(message.content, "Traditional Chinese")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.translate_lang_en)) },
                                    onClick = {
                                        translateExpanded = false
                                        onTranslate(message.content, "English")
                                    }
                                )
                            }
                        }
                    }

                    // Follow-up button
                    if (onFollowUp != null) {
                        IconButton(
                            onClick = { onFollowUp(message.content) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QuestionAnswer,
                                contentDescription = stringResource(R.string.follow_up),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Rewrite button (on any AI message, not just the last)
                    if (onRewrite != null) {
                        IconButton(
                            onClick = onRewrite,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.rewrite),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Delete button (on any AI message)
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_message),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningSection(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp)
    ) {
        // Toggle header
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.thinking_process),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchSourcesSection(sources: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .animateContentSize(animationSpec = tween(200))
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.search_sources, sources.size),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(10.dp)
            ) {
                sources.take(5).forEach { url ->
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (sources.size > 5) {
                    Text(
                        text = stringResource(R.string.more_sources, sources.size - 5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator(
    elapsedSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    // Animated dots: cycles through 0-3 dots
    var dotCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = (dotCount + 1) % 4
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        val isDark = isSystemInDarkTheme()
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 18.dp,
                bottomStart = 18.dp, bottomEnd = 18.dp
            ),
            color = if (isDark) AiBubbleDark else AiBubbleLight,
            shadowElevation = 0.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val dots = "\u25CF".repeat(dotCount.coerceAtLeast(1))
                Text(
                    text = dots,
                    color = if (isDark) AiTextDark else AiTextLight,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (elapsedSeconds > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${elapsedSeconds}s",
                        color = if (isDark) AiTextDark else AiTextLight,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileChangesSummary(
    changes: List<FileChangeSummary>,
    onFileClick: ((String) -> Unit)? = null,
    onFileSelect: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        changes.forEach { change ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onFileClick?.invoke(change.filePath) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = change.filePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (change.additions > 0) {
                        Text(
                            text = "+${change.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (change.deletions > 0) {
                        Text(
                            text = "-${change.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(0xFFF44336),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                    if (onFileSelect != null) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.select_file),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onFileSelect(change.filePath) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentExecutionSteps(
    steps: List<ExecutionStep>,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Agent Steps",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        steps.forEach { step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    step.success -> "✓"
                    !step.success -> "✗"
                    else -> "⋯"
                }
                val iconColor = when {
                    step.success -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    !step.success -> androidx.compose.ui.graphics.Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    text = icon,
                    style = MaterialTheme.typography.bodySmall,
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (step.output.isNotBlank()) {
                        Text(
                            text = step.output.take(200),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (step.durationMs > 0) {
                    Text(
                        text = "${step.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        if (isRunning) {
            Text(
                text = "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TerminalCommandBlock(cmd: TerminalCommand) {
    val termBg = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
    val termText = androidx.compose.ui.graphics.Color(0xFFD4D4D4)
    val termPrompt = androidx.compose.ui.graphics.Color(0xFF4EC9B0)
    val termError = androidx.compose.ui.graphics.Color(0xFFF44747)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(termBg)
            .padding(8.dp)
    ) {
        // Command line with prompt
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = termPrompt
            )
            Text(
                text = cmd.command,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = termText
            )
        }

        // Output (if any)
        if (cmd.output.isNotBlank()) {
            Text(
                text = cmd.output.trimEnd(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = if (cmd.exitCode != 0) termError else termText,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }

        // Exit code indicator for non-zero
        if (cmd.exitCode != 0) {
            Text(
                text = stringResource(R.string.terminal_exit_code, cmd.exitCode),
                style = MaterialTheme.typography.labelSmall,
                color = termError,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
