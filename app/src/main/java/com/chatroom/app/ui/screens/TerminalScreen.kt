package com.chatroom.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatroom.app.R
import com.chatroom.app.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TerminalScreen(
    terminalSession: TerminalSession,
    onToggleSidebar: () -> Unit,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isReady by terminalSession.isReady.collectAsState()
    val installProgress by terminalSession.installProgress.collectAsState()
    var terminalError by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val terminalLines = remember { mutableStateListOf<String>() }
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Append initial welcome message
    LaunchedEffect(Unit) {
        if (terminalLines.isEmpty()) {
            terminalLines.add("Welcome to ChatRoom Terminal")
            terminalLines.add("Type a command and press Send to execute")
            terminalLines.add("---")
        }
    }

    // Auto-install toolchain if not ready
    LaunchedEffect(Unit) {
        if (!isReady) {
            terminalSession.installToolchain()
        }
    }

    // Keep list scrolled to bottom
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    fun executeCommand(cmd: String) {
        if (cmd.isBlank()) return
        terminalLines.add("$ ${cmd}")
        inputText = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val shell = if (java.io.File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
                    val proc = ProcessBuilder(shell, "-c", cmd)
                        .redirectErrorStream(true)
                        .start()
                    val output = proc.inputStream.bufferedReader().readText()
                    val exitCode = proc.waitFor()
                    Pair(output, exitCode)
                } catch (e: Exception) {
                    Pair("Error: ${e.message}", -1)
                }
            }
            if (result.first.isNotBlank()) {
                result.first.trim().lines().forEach { line ->
                    terminalLines.add(line)
                }
            }
            terminalLines.add("")
        }
    }

    val backgroundColor = Color(0xFF1E1E1E)
    val textColor = Color(0xFFD4D4D4)
    val promptColor = Color(0xFF4EC9B0)
    val inputBgColor = Color(0xFF2D2D2D)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.content_desc_menu),
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
            if (isReady) {
                // History toggle button
                IconButton(
                    onClick = { showHistory = !showHistory },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.command_history),
                        tint = if (showHistory) promptColor else textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Clear terminal button
                IconButton(
                    onClick = { terminalLines.clear() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.terminal_clear),
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(
                onClick = onExit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.terminal_close),
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!isReady || terminalError != null) {
            // Toolchain install progress or error state
            if (terminalError != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = terminalError ?: "",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFFF44747)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = {
                        terminalError = null
                        scope.launch { terminalSession.installToolchain() }
                    }) {
                        Text(stringResource(R.string.retry), color = promptColor)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.terminal_installing_toolchain),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = textColor
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        color = promptColor,
                        trackColor = Color(0xFF2D2D2D)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = installProgress.ifEmpty { "..." },
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        } else {
            // Terminal output area
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    items(terminalLines.toList()) { line ->
                        val lineColor = when {
                            line.startsWith("$ ") -> promptColor
                            line.startsWith("Error:") -> Color(0xFFF44747)
                            else -> textColor
                        }
                        Text(
                            text = line,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = lineColor
                            ),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }

                // Command input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(inputBgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Enter command...",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.4f)
                                )
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = promptColor.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color(0xFF3D3D3D),
                            cursorColor = promptColor,
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { executeCommand(inputText) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) promptColor else textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Command history overlay
            if (showHistory) {
                val history = terminalSession.history
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE61E1E1E))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Command History",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = promptColor,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (history.isEmpty()) {
                                item {
                                    Text(
                                        text = "No commands yet",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = textColor.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else {
                                items(history.reversed()) { record ->
                                    val cmdColor = if (record.exitCode == 0) textColor
                                        else Color(0xFFF44747)
                                    Text(
                                        text = "> ${record.command}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = cmdColor
                                        ),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { showHistory = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.close), color = promptColor)
                        }
                    }
                }
            }
        }
    }
}
