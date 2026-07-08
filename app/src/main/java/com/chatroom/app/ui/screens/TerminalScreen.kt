package com.chatroom.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatroom.app.R
import com.chatroom.app.terminal.TerminalSession
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    terminalSession: TerminalSession,
    onToggleSidebar: () -> Unit,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val history by terminalSession.history.collectAsState()
    val isRunning by terminalSession.isRunning.collectAsState()
    val listState = rememberLazyListState()
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    val prompt = "${terminalSession.workingDirectory}$ "

    // Auto-scroll to bottom
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.size)
        }
    }

    val executeCommand = {
        val cmd = inputValue.text.trim()
        if (cmd.isNotEmpty() && !isRunning) {
            if (cmd == "exit" || cmd == "quit") {
                onExit()
            } else {
                scope.launch {
                    terminalSession.executeCommand(cmd)
                }
            }
            inputValue = TextFieldValue("")
        }
    }

    val backgroundColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
    val textColor = androidx.compose.ui.graphics.Color(0xFFD4D4D4)
    val promptColor = androidx.compose.ui.graphics.Color(0xFF4EC9B0)
    val errorColor = androidx.compose.ui.graphics.Color(0xFFF44747)
    val successColor = androidx.compose.ui.graphics.Color(0xFF6A9955)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF2D2D2D))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.terminal_title),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = textColor
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { terminalSession.clearHistory() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.terminal_clear),
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
            // Exit button
            IconButton(
                onClick = onExit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Terminal",
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Terminal output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Welcome message
            item {
                Text(
                    text = "ChatRoom Terminal - Code Repository Access",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = successColor
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = "Type 'help' for available commands. Press Enter to execute.",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Install tools: pkg install git curl wget tree nano vim",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // History
            items(history) { entry ->
                Text(
                    text = "${entry.workingDirectory}$ ${entry.command}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = promptColor
                    )
                )
                if (entry.output.isNotBlank()) {
                    Text(
                        text = entry.output.trimEnd(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = if (entry.exitCode != 0) errorColor else textColor
                        ),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Input line with Enter key handling
            item {
                Row {
                    Text(
                        text = prompt,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = promptColor
                        )
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = textColor
                            ),
                            cursorBrush = SolidColor(textColor),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { executeCommand() }),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (inputValue.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.terminal_input_hint),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = textColor.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF2D2D2D))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = terminalSession.workingDirectory,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.6f)
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = { executeCommand() },
                enabled = inputValue.text.isNotBlank() && !isRunning,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute",
                    tint = if (inputValue.text.isNotBlank() && !isRunning)
                        promptColor else textColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Virtual key row (for mobile without hardware keyboard)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF252526))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val keyColor = textColor.copy(alpha = 0.7f)
            val keyBg = androidx.compose.ui.graphics.Color(0xFF3C3C3C)
            val keyShape = RoundedCornerShape(4.dp)

            listOf("Esc", "Ctrl", "Alt", "Tab").forEach { key ->
                Box(
                    modifier = Modifier
                        .clip(keyShape)
                        .background(keyBg)
                        .clickable {
                            when (key) {
                                "Esc" -> inputValue = TextFieldValue(inputValue.text + "\u001B")
                                "Ctrl" -> {} // Modifier
                                "Alt" -> {} // Modifier
                                "Tab" -> inputValue = TextFieldValue(inputValue.text + "\t")
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = keyColor
                        )
                    )
                }
            }

            Box(modifier = Modifier.width(2.dp))

            listOf("\u2190", "\u2191", "\u2193", "\u2192").forEachIndexed { i, arrow ->
                Box(
                    modifier = Modifier
                        .clip(keyShape)
                        .background(keyBg)
                        .clickable {
                            val idx = inputValue.selection.start
                            val newPos = when (arrow) {
                                "\u2190" -> (idx - 1).coerceAtLeast(0)
                                "\u2191" -> 0
                                "\u2193" -> inputValue.text.length
                                "\u2192" -> (idx + 1).coerceAtMost(inputValue.text.length)
                                else -> idx
                            }
                            inputValue = inputValue.copy(
                                selection = androidx.compose.ui.text.TextRange(newPos)
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = arrow,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = keyColor
                        )
                    )
                }
            }

            Box(modifier = Modifier.width(2.dp))

            listOf("End", "Home").forEach { key ->
                Box(
                    modifier = Modifier
                        .clip(keyShape)
                        .background(keyBg)
                        .clickable {
                            inputValue = inputValue.copy(
                                selection = androidx.compose.ui.text.TextRange(
                                    if (key == "Home") 0 else inputValue.text.length
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = keyColor
                        )
                    )
                }
            }
        }
    }
}
