package com.chatroom.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatroom.app.R
import com.chatroom.app.data.model.ChatMessage
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

        Surface(
            shape = shape,
            color = bubbleColor,
            shadowElevation = 0.dp
        ) {
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
                MarkdownContent(
                    content = message.content,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Action buttons for AI messages
        if (!isUser) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 4.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
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

                    // Regenerate button (only on last AI message)
                    if (isLastMessage && onRegenerate != null) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.regenerate),
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
fun ThinkingIndicator(modifier: Modifier = Modifier) {
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
            Text(
                text = "● ● ●",
                color = if (isDark) AiTextDark else AiTextLight,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}
