package com.chatroom.app.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class TerminalSession(
    private val context: Context,
    private val tag: String = "TerminalSession"
) {
    private var _workingDirectory: String = File.separator
    val workingDirectory: String get() = _workingDirectory

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _installProgress = MutableStateFlow("")
    val installProgress: StateFlow<String> = _installProgress.asStateFlow()

    // The actual Termux terminal session (created lazily)
    private var termuxSession: com.termux.terminal.TerminalSession? = null

    // Toolchain manager for optional BusyBox shell
    private val toolchain = ToolchainInstaller(context.filesDir)

    fun start(workingDir: String = File.separator) {
        _workingDirectory = workingDir
        _isRunning.value = false
        _isReady.value = toolchain.isBusyboxInstalled()
        try { File(workingDir).mkdirs() } catch (_: Exception) {}
        Log.d(tag, "Terminal session initialized at $workingDir, ready=${_isReady.value}")
    }

    /**
     * Create or return the existing Termux TerminalSession.
     */
    fun getOrCreateSession(): com.termux.terminal.TerminalSession {
        termuxSession?.let { return it }

        val shellPath = if (toolchain.isBusyboxInstalled()) {
            toolchain.binDir.resolve("sh").absolutePath
        } else {
            "/system/bin/sh"
        }

        val pathEnv = if (toolchain.isBusyboxInstalled()) {
            "${toolchain.binDir.absolutePath}:/system/bin:/system/xbin"
        } else {
            "/system/bin:/system/xbin"
        }

        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=${toolchain.toolsHome.absolutePath}",
            "PATH=$pathEnv",
            "SHELL=$shellPath"
        )

        val client = object : com.termux.terminal.TerminalSessionClient {
            override fun onTitleChanged(session: com.termux.terminal.TerminalSession, title: String) {}
            override fun onSessionInformationChanged(session: com.termux.terminal.TerminalSession, info: String) {}
            override fun onBell(session: com.termux.terminal.TerminalSession) {}
            override fun onColorsChanged(session: com.termux.terminal.TerminalSession) {}
            override fun onTerminalSessionStateChanged(session: com.termux.terminal.TerminalSession) {}
            override fun logError(tag: String, message: String) = Log.e(tag, message)
            override fun logOutput(data: ByteArray, offset: Int, count: Int) {}
            override fun onLineFeed(session: com.termux.terminal.TerminalSession) {}
        }

        val session = com.termux.terminal.TerminalSession(
            shellPath, _workingDirectory, env, 1000, client
        )
        session.initializeEmulator(80, 24)
        termuxSession = session
        Log.d(tag, "Termux session created: shell=$shellPath")
        return session
    }

    /**
     * Install toolchain (BusyBox) in background, called from TerminalScreen
     */
    suspend fun installToolchain(onProgress: (String) -> Unit = {}): Boolean {
        if (toolchain.isBusyboxInstalled()) {
            withContext(Dispatchers.Main) { _isReady.value = true }
            return true
        }
        val ok = toolchain.installBusybox { msg ->
            _installProgress.value = msg
            onProgress(msg)
        }
        if (ok) {
            withContext(Dispatchers.Main) { _isReady.value = true }
        }
        return ok
    }

    fun stop() {
        termuxSession = null
        _isRunning.value = false
        Log.d(tag, "Terminal session stopped")
    }

    fun isRunning(): Boolean = termuxSession?.isRunning ?: false
}
