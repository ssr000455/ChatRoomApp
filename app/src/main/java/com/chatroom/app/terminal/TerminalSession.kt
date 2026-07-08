package com.chatroom.app.terminal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class CommandEntry(
    val command: String,
    val output: String,
    val workingDirectory: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class TerminalSession(
    private val tag: String = "TerminalSession"
) {
    private var process: Process? = null
    private var writer: java.io.OutputStream? = null

    private var _workingDirectory: String = File.separator
    val workingDirectory: String get() = _workingDirectory

    private val _history = MutableStateFlow<List<CommandEntry>>(emptyList())
    val history: StateFlow<List<CommandEntry>> = _history.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentOutput = MutableStateFlow("")
    val currentOutput: StateFlow<String> = _currentOutput.asStateFlow()

    private val supportedCommands = setOf(
        "git", "gh", "curl", "wget", "tree", "ls", "nano",
        "cat", "head", "tail", "find", "grep", "mkdir",
        "cp", "mv", "rm", "echo", "pwd", "cd", "which",
        "chmod", "touch", "sort", "wc", "diff", "patch",
        "env", "export", "source", "alias", "type",
        "man", "help", "clear", "pwd", "whoami", "id"
    )

    fun start(workingDir: String = File.separator) {
        _workingDirectory = workingDir
        _isRunning.value = true
        Log.d(tag, "Terminal session started at $workingDir")
    }

    suspend fun executeCommand(command: String): Result<CommandEntry> = withContext(Dispatchers.IO) {
        try {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(
                    CommandEntry("", "", _workingDirectory, 0)
                )
            }

            // Handle cd specially since it's a shell built-in
            if (trimmed.startsWith("cd ")) {
                val dir = trimmed.removePrefix("cd ").trim()
                val targetDir = if (dir.startsWith(File.separator)) {
                    File(dir)
                } else {
                    File(_workingDirectory, dir)
                }
                if (targetDir.isDirectory()) {
                    _workingDirectory = targetDir.canonicalPath
                    return@withContext Result.success(
                        CommandEntry(trimmed, "", _workingDirectory, 0)
                    )
                } else {
                    return@withContext Result.success(
                        CommandEntry(trimmed, "cd: $dir: No such directory", _workingDirectory, 1)
                    )
                }
            }

            // Handle clear
            if (trimmed == "clear") {
                _history.value = emptyList()
                return@withContext Result.success(
                    CommandEntry("clear", "", _workingDirectory, 0)
                )
            }

            // Execute command
            val processBuilder = ProcessBuilder()
                .command("sh", "-c", trimmed)
                .directory(File(_workingDirectory))
                .redirectErrorStream(true)

            val proc = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = reader.readText()
            val exitCode = proc.waitFor()

            val entry = CommandEntry(trimmed, output, _workingDirectory, exitCode)
            _history.value = _history.value + entry
            _currentOutput.value = output

            Log.d(tag, "Command: $trimmed, exit: $exitCode")
            Result.success(entry)
        } catch (e: Exception) {
            Log.e(tag, "Command failed: ${e.message}")
            val entry = CommandEntry(command, "Error: ${e.message}", _workingDirectory, -1)
            _history.value = _history.value + entry
            _currentOutput.value = "Error: ${e.message}"
            Result.success(entry)
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _currentOutput.value = ""
    }

    fun stop() {
        process?.destroy()
        process = null
        _isRunning.value = false
        Log.d(tag, "Terminal session stopped")
    }

    fun isCommandSupported(command: String): Boolean {
        val base = command.trim().split(" ").firstOrNull() ?: return false
        return supportedCommands.contains(base)
    }
}
