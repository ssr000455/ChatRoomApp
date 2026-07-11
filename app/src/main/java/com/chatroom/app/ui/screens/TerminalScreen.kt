package com.chatroom.app.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.viewinterop.AndroidView
import com.chatroom.app.R
import com.chatroom.app.terminal.TerminalSession

@Composable
fun TerminalScreen(
    terminalSession: TerminalSession,
    onToggleSidebar: () -> Unit,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isReady by terminalSession.isReady.collectAsState()
    val installProgress by terminalSession.installProgress.collectAsState()
    var terminalView by remember { mutableStateOf<com.termux.view.TerminalView?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    // Auto-install toolchain if not ready
    LaunchedEffect(Unit) {
        if (!isReady) {
            terminalSession.installToolchain()
        }
    }

    // Request focus on TerminalView when ready so the keyboard shows
    LaunchedEffect(isReady) {
        if (isReady && terminalView != null) {
            kotlinx.coroutines.delay(300)
            terminalView?.let { tv ->
                tv.requestFocus()
                val imm = tv.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    val backgroundColor = Color(0xFF1E1E1E)
    val textColor = Color(0xFFD4D4D4)
    val promptColor = Color(0xFF4EC9B0)

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
                IconButton(
                    onClick = {
                        // Send Ctrl+L (form feed) to clear terminal screen
                        terminalSession.getOrCreateSession().write("\u000C".toByteArray(), 0, 1)
                    },
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

        if (!isReady) {
            // Toolchain install progress
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
        } else {
            // Termux TerminalView - full terminal emulation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        com.termux.view.TerminalView(ctx, null).apply {
                            setTextSize(12)
                            setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                                override fun onSingleTapUp(e: android.view.MotionEvent?) {}
                                override fun onScale(scale: Float): Float = scale
                                override fun onLongPress(e: android.view.MotionEvent?): Boolean = false
                                override fun isTerminalViewSelected(): Boolean = true
                                override fun shouldEnforceCharBasedInput(): Boolean = false
                                override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                override fun readControlKey(): Boolean = false
                                override fun readAltKey(): Boolean = false
                                override fun readShiftKey(): Boolean = false
                                override fun readFnKey(): Boolean = false
                                override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: com.termux.terminal.TerminalSession?): Boolean = false
                                override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = false
                                override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: com.termux.terminal.TerminalSession?): Boolean = false
                                override fun onEmulatorSet() {}
                                override fun copyModeChanged(isCopyMode: Boolean) {}
                                override fun logInfo(tag: String?, message: String?) { android.util.Log.i(tag ?: "TerminalView", message ?: "") }
                                override fun logError(tag: String?, message: String?) { android.util.Log.e(tag ?: "TerminalView", message ?: "") }
                                override fun logWarn(tag: String?, message: String?) { android.util.Log.w(tag ?: "TerminalView", message ?: "") }
                                override fun logDebug(tag: String?, message: String?) { android.util.Log.d(tag ?: "TerminalView", message ?: "") }
                                override fun logVerbose(tag: String?, message: String?) { android.util.Log.v(tag ?: "TerminalView", message ?: "") }
                                override fun logStackTraceWithMessage(tag: String?, message: String?, e: java.lang.Exception?) { android.util.Log.e(tag ?: "TerminalView", message ?: "", e) }
                                override fun logStackTrace(tag: String?, e: java.lang.Exception?) { android.util.Log.e(tag ?: "TerminalView", "", e) }
                            })
                            val session = terminalSession.getOrCreateSession()
                            attachSession(session)
                            isFocusable = true
                            isFocusableInTouchMode = true
                            terminalView = this
                            // Post to ensure view is laid out before focus request
                            post {
                                requestFocus()
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    },
                    onRelease = {
                        terminalView = null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Command history overlay
                if (showHistory) {
                    val history = terminalSession.history
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xE61E1E1E))
                            .clickable { /* consume clicks */ }
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
}
