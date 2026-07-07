package com.chatroom.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed class Segment {
    data class Text(val content: String) : Segment()
    data class Bold(val content: String) : Segment()
    data class Italic(val content: String) : Segment()
    data class Strikethrough(val content: String) : Segment()
    data class Code(val content: String, val language: String = "") : Segment()
    data class InlineCode(val content: String) : Segment()
    data class Url(val url: String, val text: String) : Segment()
    data class InlineMath(val content: String) : Segment()
    data class BlockMath(val content: String) : Segment()
    data object HorizontalRule : Segment()
    data object Newline : Segment()
}

@Composable
fun RichMessageContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val segments = parseContent(content)

    Column(modifier = modifier.fillMaxWidth()) {
        var currentIndex = 0
        while (currentIndex < segments.size) {
            val segment = segments[currentIndex]

            when (segment) {
                is Segment.Code -> {
                    CodeBlock(
                        code = segment.content,
                        language = segment.language,
                        context = context,
                        isDark = isDark
                    )
                    currentIndex++
                }

                is Segment.BlockMath -> {
                    LatexBlock(content = segment.content)
                    currentIndex++
                }

                is Segment.HorizontalRule -> {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                    currentIndex++
                }

                else -> {
                    // Collect contiguous inline segments
                    val inlineSegments = mutableListOf<Segment>()
                    while (currentIndex < segments.size &&
                        segments[currentIndex] !is Segment.Code &&
                        segments[currentIndex] !is Segment.BlockMath &&
                        segments[currentIndex] !is Segment.HorizontalRule
                    ) {
                        inlineSegments.add(segments[currentIndex])
                        currentIndex++
                    }
                    InlineContent(segments = inlineSegments, context = context, isDark = isDark)
                }
            }
        }
    }
}

@Composable
private fun InlineContent(
    segments: List<Segment>,
    context: Context,
    isDark: Boolean
) {
    val annotated = buildAnnotatedString {
        for (seg in segments) {
            when (seg) {
                is Segment.Text -> append(seg.content)
                is Segment.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(seg.content) }
                is Segment.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(seg.content) }
                is Segment.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(seg.content) }
                is Segment.InlineCode -> {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = if (isDark) MaterialTheme.colorScheme.surfaceVariant
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        color = if (isDark) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary
                    )) { append(seg.content) }
                }
                is Segment.Url -> {
                    pushStringAnnotation("URL", seg.url)
                    withStyle(SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )) { append(seg.text) }
                    pop()
                }
                is Segment.InlineMath -> {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        color = if (isDark) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.tertiary,
                        background = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )) { append(" $${seg.content}$ ") }
                }
                is Segment.Newline -> append("\n")
                else -> {}
            }
        }
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                context.startActivity(intent)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CodeBlock(
    code: String,
    language: String,
    context: Context,
    isDark: Boolean
) {
    val bg = if (isDark) MaterialTheme.colorScheme.surfaceVariant
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
    ) {
        // Header bar with language label and copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Parser ──

private fun parseContent(content: String): List<Segment> {
    val segments = mutableListOf<Segment>()
    var i = 0
    val text = content

    while (i < text.length) {
        // Horizontal rule --- / ___ / *** (must be whole line, at least 3 chars)
        if (i == 0 || text[i - 1] == '\n') {
            val lineEnd = text.indexOf('\n', i).let { if (it == -1) text.length else it }
            val line = text.substring(i, lineEnd).trim()
            if (line.length >= 3 && (line.all { it == '-' } || line.all { it == '_' } || line.all { it == '*' })) {
                segments.add(Segment.HorizontalRule)
                segments.add(Segment.Newline)
                i = lineEnd + 1
                continue
            }
        }

        // Code block ```lang\ncode```
        val codeBlockMatch = text.indexOf("```", i)
        if (codeBlockMatch == i) {
            val endIdx = text.indexOf("```", i + 3)
            if (endIdx != -1) {
                val inner = text.substring(i + 3, endIdx)
                val newlineIdx = inner.indexOf('\n')
                val lang = if (newlineIdx != -1) inner.substring(0, newlineIdx).trim() else ""
                val code = if (newlineIdx != -1) inner.substring(newlineIdx + 1) else inner
                segments.add(Segment.Code(code.trimEnd(), lang))
                i = endIdx + 3
                continue
            }
        }

        // Block math $$
        val blockMathMatch = text.indexOf("$$", i)
        if (blockMathMatch == i) {
            val endIdx = text.indexOf("$$", i + 2)
            if (endIdx != -1) {
                segments.add(Segment.BlockMath(text.substring(i + 2, endIdx).trim()))
                i = endIdx + 2
                continue
            }
        }

        // Newline
        if (text[i] == '\n') {
            segments.add(Segment.Newline)
            i++
            continue
        }

        // Collect a line for inline parsing
        val lineEnd = text.indexOf('\n', i).let { if (it == -1) text.length else it }
        val line = text.substring(i, lineEnd)
        parseInline(line, segments)
        i = lineEnd
    }

    return segments
}

private fun parseInline(line: String, segments: MutableList<Segment>) {
    var i = 0
    var currentText = StringBuilder()

    fun flushText() {
        if (currentText.isNotEmpty()) {
            segments.add(Segment.Text(currentText.toString()))
            currentText = StringBuilder()
        }
    }

    while (i < line.length) {
        when {
            // Inline math $...$
            line[i] == '$' && i + 1 < line.length && line[i + 1] != '$' -> {
                val end = line.indexOf('$', i + 1)
                if (end != -1) {
                    flushText()
                    segments.add(Segment.InlineMath(line.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            // Inline code `...`
            line[i] == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end != -1) {
                    flushText()
                    segments.add(Segment.InlineCode(line.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            // Bold **...**
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end != -1) {
                    flushText()
                    segments.add(Segment.Bold(line.substring(i + 2, end)))
                    i = end + 2
                    continue
                }
            }

            // Strikethrough ~~...~~
            line.startsWith("~~", i) -> {
                val end = line.indexOf("~~", i + 2)
                if (end != -1) {
                    flushText()
                    segments.add(Segment.Strikethrough(line.substring(i + 2, end)))
                    i = end + 2
                    continue
                }
            }

            // Italic *...*
            line[i] == '*' && i + 1 < line.length && line[i + 1] != '*' -> {
                val end = line.indexOf('*', i + 1)
                if (end != -1) {
                    flushText()
                    segments.add(Segment.Italic(line.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            // URLs - detect http:// or https://
            line.startsWith("https://", i) || line.startsWith("http://", i) -> {
                var end = i
                while (end < line.length && line[end] != ' ' && line[end] != '\n' && line[end] != ')') end++
                if (end > i) {
                    val url = line.substring(i, end)
                    flushText()
                    segments.add(Segment.Url(url = url, text = url))
                    i = end
                    continue
                }
            }

            // Markdown links [text](url)
            line[i] == '[' -> {
                val closeB = line.indexOf(']', i)
                if (closeB != -1 && closeB + 1 < line.length && line[closeB + 1] == '(') {
                    val closeP = line.indexOf(')', closeB + 2)
                    if (closeP != -1) {
                        val linkText = line.substring(i + 1, closeB)
                        val url = line.substring(closeB + 2, closeP)
                        flushText()
                        segments.add(Segment.Url(url = url, text = linkText))
                        i = closeP + 1
                        continue
                    }
                }
            }
        }

        currentText.append(line[i])
        i++
    }

    flushText()
}
