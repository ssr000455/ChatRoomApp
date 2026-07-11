package com.chatroom.app.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.LinkedList

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

    // Command history tracking (for AI agent)
    private val commandHistory = LinkedList<CommandRecord>()
    val history: List<CommandRecord> get() = commandHistory.toList()

    private var termuxSession: com.termux.terminal.TerminalSession? = null

    private val toolchain = ToolchainInstaller(context.filesDir)

    // Terminal history persistence
    private var terminalHistory: TerminalHistory? = null

    fun start(workingDir: String = File.separator) {
        _workingDirectory = workingDir
        _isRunning.value = false
        _isReady.value = toolchain.isBusyboxInstalled()
        try { File(workingDir).mkdirs() } catch (_: Exception) {}
        Log.d(tag, "Terminal session initialized at $workingDir, ready=${_isReady.value}")
    }

    /**
     * Initialize terminal history for a session.
     */
    fun initHistory(baseDir: File, sessionId: String) {
        terminalHistory = TerminalHistory(baseDir).also { it.init(sessionId) }
    }

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
            override fun onTitleChanged(session: com.termux.terminal.TerminalSession) {}
            override fun onTextChanged(session: com.termux.terminal.TerminalSession) {}
            override fun onBell(session: com.termux.terminal.TerminalSession) {}
            override fun onColorsChanged(session: com.termux.terminal.TerminalSession) {}
            override fun onCopyTextToClipboard(session: com.termux.terminal.TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: com.termux.terminal.TerminalSession) {}
            override fun onSessionFinished(session: com.termux.terminal.TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "", e) }
        }

        val session = com.termux.terminal.TerminalSession(
            shellPath, _workingDirectory, null, env, 1000, client
        )
        termuxSession = session
        Log.d(tag, "Termux session created: shell=$shellPath")
        return session
    }

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

    /**
     * Write a command to the PTY terminal (so it appears in the interactive terminal).
     * This is used for "AI inject terminal" mode - commands appear as if user typed them.
     */
    fun writeToPty(command: String) {
        val session = termuxSession ?: return
        val cmdBytes = (command + "\n").toByteArray()
        session.write(cmdBytes, 0, cmdBytes.size)
        recordCommand(command, "PTY")
        terminalHistory?.recordCommand(command, _workingDirectory)
        terminalHistory?.append("> $command")
        Log.d(tag, "PTY write: $command")
    }

    /**
     * Write arbitrary bytes to the PTY (for Ctrl+C etc).
     */
    fun writeBytes(data: ByteArray) {
        val session = termuxSession ?: return
        session.write(data, 0, data.size)
    }

    /**
     * Send Ctrl+C to the terminal.
     */
    fun sendCtrlC() {
        writeBytes(byteArrayOf(0x03))
    }

    /**
     * Execute a command programmatically (subprocess, non-PTY) and capture output.
     * Returns the full output as a string.
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
            val pb = ProcessBuilder(arrayListOf(shell, "-c", command))
                .directory(File(_workingDirectory))
                .redirectErrorStream(true)

            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            val exitCode = proc.waitFor()
            val resultStr = output.toString()

            recordCommand(command, exitCode)
            terminalHistory?.recordCommand(command, _workingDirectory)
            terminalHistory?.append(resultStr)

            Log.d(tag, "Executed: $command -> exit $exitCode (${resultStr.length} chars)")
            CommandResult(command = command, output = resultStr, exitCode = exitCode)
        } catch (e: Exception) {
            Log.e(tag, "Command execution failed: $command", e)
            CommandResult(command = command, output = "Error: ${e.message}", exitCode = -1)
        }
    }

    /**
     * Update the working directory for programmatic execution.
     */
    fun updateWorkingDirectory(dir: String) {
        _workingDirectory = dir
        Log.d(tag, "Working directory updated to $dir")
    }

    fun stop() {
        termuxSession?.finishIfRunning()
        termuxSession = null
        _isRunning.value = false
        Log.d(tag, "Terminal session stopped")
    }

    fun isRunning(): Boolean = termuxSession?.isRunning ?: false

    /**
     * Get the terminal history persistence instance.
     */
    fun getTerminalHistory(): TerminalHistory? = terminalHistory

    // ── Private helpers ──

    private fun recordCommand(command: String, exitCode: Int) {
        synchronized(commandHistory) {
            commandHistory.addLast(CommandRecord(command = command, exitCode = exitCode, timestamp = System.currentTimeMillis()))
            if (commandHistory.size > 200) {
                commandHistory.removeFirst()
            }
        }
    }

    private fun recordCommand(command: String, mode: String) {
        synchronized(commandHistory) {
            commandHistory.addLast(CommandRecord(command = command, mode = mode, timestamp = System.currentTimeMillis()))
            if (commandHistory.size > 200) {
                commandHistory.removeFirst()
            }
        }
    }
}

/**
 * Record of a command execution in the terminal.
 */
data class CommandRecord(
    val command: String,
    val output: String = "",
    val exitCode: Int = 0,
    val mode: String = "pty", // "pty" or "programmatic"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of a programmatic command execution.
 */
data class CommandResult(
    val command: String,
    val output: String,
    val exitCode: Int
)
