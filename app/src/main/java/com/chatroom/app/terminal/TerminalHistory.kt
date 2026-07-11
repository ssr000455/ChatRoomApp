package com.chatroom.app.terminal

import android.util.Log
import java.io.File

/**
 * Persists terminal output to a file for history review.
 * Each session gets its own log file under session_id/terminal.log.
 */
class TerminalHistory(
    private val baseDir: File
) {
    companion object {
        private const val TAG = "TerminalHistory"
        private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB
    }

    private var logFile: File? = null
    private var byteCount: Long = 0

    /**
     * Initialize for a given session.
     */
    fun init(sessionId: String) {
        val dir = File(baseDir, sessionId)
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, "terminal.log")
        byteCount = logFile?.length() ?: 0
        Log.d(TAG, "Terminal history initialized: ${logFile?.absolutePath}")
    }

    /**
     * Append a line to the terminal log.
     */
    fun append(line: String) {
        val file = logFile ?: return
        if (byteCount >= MAX_LOG_SIZE) {
            Log.w(TAG, "Terminal log size exceeded, rotating")
            rotateLog(file)
        }
        try {
            file.appendText(line + "\n")
            byteCount += line.length + 1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write terminal log", e)
        }
    }

    /**
     * Append a command execution record.
     */
    fun recordCommand(command: String, workingDir: String) {
        append("")
        append("─" .repeat(60))
        append("[CMD] $ $command  (in $workingDir)")
        append("─" .repeat(60))
    }

    /**
     * Read back the full terminal log.
     */
    fun readAll(): String {
        val file = logFile ?: return ""
        return try {
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read terminal log", e)
            ""
        }
    }

    /**
     * Read the last N lines of terminal log.
     */
    fun readTail(lines: Int = 50): String {
        val file = logFile ?: return ""
        return try {
            val allLines = file.readLines()
            allLines.takeLast(lines).joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read terminal tail", e)
            ""
        }
    }

    /**
     * Get the current log file.
     */
    fun getLogFile(): File? = logFile

    /**
     * Clear the terminal history.
     */
    fun clear() {
        logFile?.writeText("")
        byteCount = 0
        Log.d(TAG, "Terminal history cleared")
    }

    private fun rotateLog(file: File) {
        val rotated = File(file.absolutePath + ".1")
        try {
            if (rotated.exists()) rotated.delete()
            file.renameTo(rotated)
            byteCount = 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log", e)
        }
    }
}
