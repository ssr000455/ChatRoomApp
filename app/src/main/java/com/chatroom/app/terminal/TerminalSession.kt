package com.chatroom.app.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class CommandEntry(
    val command: String,
    val output: String,
    val workingDirectory: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class TerminalSession(
    private val context: Context,
    private val tag: String = "TerminalSession"
) {
    private var _workingDirectory: String = File.separator
    val workingDirectory: String get() = _workingDirectory

    private val _history = MutableStateFlow<List<CommandEntry>>(emptyList())
    val history: StateFlow<List<CommandEntry>> = _history.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentOutput = MutableStateFlow("")
    val currentOutput: StateFlow<String> = _currentOutput.asStateFlow()

    // Toolchain manager and shell environment
    private val toolchain = ToolchainInstaller(context.filesDir)
    private var shellPath: String = "sh"
    private var extraPath: String = ""

    fun start(workingDir: String = File.separator) {
        _workingDirectory = workingDir
        _isRunning.value = false

        // Initialize toolchain: use BusyBox if available, fall back to system sh
        val status = toolchain.checkStatus()
        if (status.hasBusybox) {
            shellPath = status.shellPath
            extraPath = status.binDir
            Log.d(tag, "Using BusyBox shell: $shellPath")
        } else {
            shellPath = "sh"
            extraPath = ""
            Log.d(tag, "Using system shell: sh")
        }

        // Ensure workspace dir exists
        try { File(workingDir).mkdirs() } catch (_: Exception) {}
        Log.d(tag, "Terminal session started at $workingDir")
    }

    /**
     * Initialize toolchain (download BusyBox etc.). Call this from a coroutine.
     */
    suspend fun initToolchain(onProgress: (String) -> Unit = {}): Boolean {
        val ok = toolchain.installBusybox(onProgress)
        if (ok) {
            shellPath = toolchain.binDir.resolve("sh").absolutePath
            extraPath = toolchain.binDir.absolutePath
        }
        return ok
    }

    fun isBusyboxInstalled(): Boolean = toolchain.isBusyboxInstalled()

    private fun buildProcess(command: String): ProcessBuilder {
        val pb = ProcessBuilder(shellPath, "-c", command)
            .directory(File(_workingDirectory))
            .redirectErrorStream(true)
        // Set PATH: toolchain bin first (if available), then system paths
        val env = pb.environment()
        env["PATH"] = if (extraPath.isNotEmpty()) {
            "$extraPath:/system/bin:/system/xbin"
        } else {
            "/system/bin:/system/xbin"
        }
        env["HOME"] = toolchain.toolsHome.absolutePath
        return pb
    }

    suspend fun executeCommand(command: String): Result<CommandEntry> = withContext(Dispatchers.IO) {
        _isRunning.value = true
        try {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(
                    CommandEntry("", "", _workingDirectory, 0)
                )
            }

            // Handle clear
            if (trimmed == "clear") {
                _history.value = emptyList()
                _currentOutput.value = ""
                return@withContext Result.success(
                    CommandEntry("clear", "", _workingDirectory, 0)
                )
            }

            // Handle cd internally
            if (trimmed == "cd") {
                _workingDirectory = File.separator
                return@withContext Result.success(
                    CommandEntry("cd", "", _workingDirectory, 0)
                )
            }
            if (trimmed.startsWith("cd ")) {
                val dir = trimmed.removePrefix("cd ").trim()
                val newDir = if (dir.startsWith(File.separator)) {
                    File(dir)
                } else {
                    File(_workingDirectory, dir)
                }
                val normalized = newDir.normalize()
                if (normalized.isDirectory) {
                    _workingDirectory = normalized.absolutePath
                    return@withContext Result.success(
                        CommandEntry(trimmed, "", _workingDirectory, 0)
                    )
                } else {
                    val entry = CommandEntry(trimmed, "cd: $dir: No such directory", _workingDirectory, 1)
                    _history.value = _history.value + entry
                    _currentOutput.value = entry.output
                    return@withContext Result.success(entry)
                }
            }

            // Run command with timeout and proper cleanup
            val process = buildProcess(trimmed).start()
            var processOutput = ""
            var processExitCode = -1
            try {
                // Read output in chunks to avoid pipe buffer deadlock
                val reader = process.inputStream.bufferedReader()
                val outputBuilder = StringBuilder()
                val commandTimeout = 30000L
                val startTime = System.currentTimeMillis()
                var timedOut = false
                while (true) {
                    if (reader.ready()) {
                        val line = reader.readLine()
                        if (line == null) break
                        outputBuilder.appendLine(line)
                    } else {
                        // Check if process has exited
                        try {
                            processExitCode = process.exitValue()
                            break
                        } catch (_: IllegalThreadStateException) {
                            // Process still running
                        }
                        // Check timeout
                        if (System.currentTimeMillis() - startTime > commandTimeout) {
                            timedOut = true
                            process.destroyForcibly()
                            outputBuilder.appendLine("\n[Command timed out after ${commandTimeout / 1000}s]")
                            break
                        }
                        // Small delay to avoid busy-waiting
                        Thread.sleep(50)
                    }
                }
                // Read any remaining output
                try {
                    val remaining = reader.readText()
                    if (remaining.isNotEmpty()) outputBuilder.append(remaining)
                } catch (_: Exception) {}
                processOutput = outputBuilder.toString().trimEnd()
                if (!timedOut) {
                    try {
                        processExitCode = process.waitFor()
                    } catch (_: Exception) {}
                }
            } finally {
                // Always destroy the process to avoid zombie processes
                try { process.destroyForcibly() } catch (_: Exception) {}
            }

            _currentOutput.value = processOutput

            val entry = CommandEntry(trimmed, processOutput, _workingDirectory, processExitCode)
            _history.value = _history.value + entry

            Log.d(tag, "Cmd: $trimmed, exit: $processExitCode, out len: ${processOutput.length}")
            Result.success(entry)
        } catch (e: Exception) {
            Log.e(tag, "Command error: ${e.message}")
            val entry = CommandEntry(command, "Error: ${e.message}", _workingDirectory, -1)
            _history.value = _history.value + entry
            _currentOutput.value = "Error: ${e.message}"
            Result.success(entry)
        } finally {
            _isRunning.value = false
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _currentOutput.value = ""
    }

    fun stop() {
        _isRunning.value = false
        Log.d(tag, "Terminal session stopped")
    }
}
