package com.chatroom.app.terminal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

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
    private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null
    private var shellReader: BufferedReader? = null

    private val CMD_MARKER = "___CMD_FINISHED___"

    private var _workingDirectory: String = File.separator
    val workingDirectory: String get() = _workingDirectory

    private val _history = MutableStateFlow<List<CommandEntry>>(emptyList())
    val history: StateFlow<List<CommandEntry>> = _history.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentOutput = MutableStateFlow("")
    val currentOutput: StateFlow<String> = _currentOutput.asStateFlow()

    private var hasPty = false

    fun start(workingDir: String = File.separator) {
        try {
            // Use script(1) to create a real PTY for the shell.
            // script -q: quiet, -c: run command, /dev/null: discard typescript file
            // This gives the shell a genuine PTY, enabling interactive programs.
            val pb = ProcessBuilder("script", "-qc", "sh", "/dev/null")
                .directory(File(workingDir))
                .redirectErrorStream(true)
            shellProcess = pb.start()
            shellWriter = BufferedWriter(OutputStreamWriter(shellProcess!!.outputStream))
            shellReader = BufferedReader(InputStreamReader(shellProcess!!.inputStream))

            // Drain initial output (login banner, shell prompt etc.)
            drainInitialOutput()

            _workingDirectory = workingDir
            _isRunning.value = true
            hasPty = true
            Log.d(tag, "PTY shell started at $workingDir")
        } catch (e: Exception) {
            Log.w(tag, "script not available, falling back to plain shell: ${e.message}")
            startPlainShell(workingDir)
        }
    }

    private fun startPlainShell(workingDir: String) {
        try {
            val pb = ProcessBuilder("sh")
                .directory(File(workingDir))
                .redirectErrorStream(true)
            shellProcess = pb.start()
            shellWriter = BufferedWriter(OutputStreamWriter(shellProcess!!.outputStream))
            shellReader = BufferedReader(InputStreamReader(shellProcess!!.inputStream))
            _workingDirectory = workingDir
            _isRunning.value = true
            hasPty = false
            Log.d(tag, "Plain shell started at $workingDir (no PTY)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start shell: ${e.message}")
        }
    }

    private fun drainInitialOutput() {
        try {
            shellProcess?.let { proc ->
                val reader = shellReader ?: return
                // Wait a tiny bit for shell to start and output anything
                Thread.sleep(100)
                // Drain available output
                val buf = CharArray(4096)
                while (reader.ready()) {
                    reader.read(buf)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun executeCommand(command: String): Result<CommandEntry> = withContext(Dispatchers.IO) {
        try {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(
                    CommandEntry("", "", _workingDirectory, 0)
                )
            }

            val writer = shellWriter ?: return@withContext Result.success(
                CommandEntry(trimmed, "Shell not started", _workingDirectory, -1)
            )
            val reader = shellReader ?: return@withContext Result.success(
                CommandEntry(trimmed, "Shell not started", _workingDirectory, -1)
            )

            if (trimmed == "clear") {
                _history.value = emptyList()
                _currentOutput.value = ""
                return@withContext Result.success(
                    CommandEntry("clear", "", _workingDirectory, 0)
                )
            }

            // Restart shell if process died
            if (shellProcess?.isAlive != true) {
                restart()
                return@withContext Result.success(
                    CommandEntry(trimmed, "Shell restarted", _workingDirectory, -1)
                )
            }

            // Drain any pending output before writing command
            drainBeforeWrite(reader)

            // Write command to shell's stdin
            writer.write(trimmed)
            writer.newLine()
            // Write marker to detect command completion and capture exit code
            writer.write("echo ${CMD_MARKER}$?")
            writer.newLine()
            writer.flush()

            // Read output in real-time until we see the marker
            val outputLines = mutableListOf<String>()
            var exitCode = -1
            var markerFound = false
            val buffer = StringBuilder()

            while (true) {
                val line = reader.readLine() ?: run {
                    // Process died
                    if (shellProcess?.isAlive != true) restart()
                    break
                }

                if (line.startsWith(CMD_MARKER)) {
                    exitCode = line.removePrefix(CMD_MARKER).trim().toIntOrNull() ?: -1
                    markerFound = true
                    break
                }

                // With PTY, the shell echoes the command back.
                // Skip the echoed command line in the output.
                // The first line of output with PTY is always the command itself echoed back.
                if (hasPty && outputLines.isEmpty() && line.trim() == trimmed) {
                    continue
                }

                outputLines.add(line)
                buffer.append(line).append('\n')
            }

            // Update current output in real-time
            val outputText = outputLines.joinToString("\n")
            _currentOutput.value = outputText

            // If we never found the marker, the command probably failed
            if (!markerFound && outputText.isNotEmpty()) {
                // Try to still use the output
            }

            // Update working directory
            try {
                updateWorkingDirectory()
            } catch (_: Exception) {}

            val entry = CommandEntry(trimmed, outputText, _workingDirectory, exitCode)
            _history.value = _history.value + entry

            Log.d(tag, "Command: $trimmed, exit: $exitCode, lines: ${outputLines.size}")
            Result.success(entry)
        } catch (e: Exception) {
            Log.e(tag, "Shell error: ${e.message}")
            if (shellProcess?.isAlive != true) restart()
            val entry = CommandEntry(command, "Error: ${e.message}", _workingDirectory, -1)
            _history.value = _history.value + entry
            _currentOutput.value = "Error: ${e.message}"
            Result.success(entry)
        }
    }

    private fun drainBeforeWrite(reader: BufferedReader) {
        try {
            if (reader.ready()) {
                val buf = CharArray(4096)
                // Limit drain to avoid infinite loop on stuck output
                var drained = 0
                while (reader.ready() && drained < 100) {
                    reader.read(buf)
                    drained++
                }
            }
        } catch (_: Exception) {}
    }

    private fun updateWorkingDirectory() {
        val writer = shellWriter ?: return
        val reader = shellReader ?: return

        // Drain any pending output first
        drainBeforeWrite(reader)

        writer.write("pwd 2>/dev/null")
        writer.newLine()
        writer.write("echo ${CMD_MARKER}pwd")
        writer.newLine()
        writer.flush()

        val pwdLines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.startsWith(CMD_MARKER)) break
            // Skip echoed "pwd" when PTY echoes it back
            if (hasPty && pwdLines.isEmpty() && line.trim() == "pwd") continue
            pwdLines.add(line)
        }
        if (pwdLines.isNotEmpty()) {
            _workingDirectory = pwdLines.last().trim()
        }
    }

    private fun restart() {
        stop()
        start(_workingDirectory)
    }

    fun clearHistory() {
        _history.value = emptyList()
        _currentOutput.value = ""
    }

    fun stop() {
        try {
            shellWriter?.apply {
                write("exit")
                newLine()
                flush()
            }
        } catch (_: Exception) {}
        shellProcess?.destroy()
        shellProcess = null
        shellWriter = null
        shellReader = null
        _isRunning.value = false
        hasPty = false
        Log.d(tag, "Terminal session stopped")
    }
}
