package com.chatroom.app.terminal

import android.util.Log
import com.chatroom.app.data.model.ActionResult
import com.chatroom.app.data.model.AgentEvent
import com.chatroom.app.data.model.AiAction
import com.chatroom.app.data.model.GitDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Executes AI agent actions: reading files, writing files, running commands.
 * Emits AgentEvents for real-time UI updates during execution.
 * When repoDir is null, a temporary workspace directory is used.
 */
class ActionExecutor(
    private val repoDir: File?,
    private val gitOperator: GitOperator
) {
    companion object {
        private const val TAG = "ActionExecutor"
        private const val MAX_OUTPUT_LENGTH = 50_000
    }

    private val workspaceDir: File = repoDir
        ?: File(System.getProperty("java.io.tmpdir"), "codex-workspace-${System.currentTimeMillis()}").also {
            it.mkdirs()
        }

    /**
     * Execute a single AI action and return the result.
     * Emits progress events via the returned flow.
     */
    fun execute(action: AiAction): Flow<AgentEvent> = flow {
        when (action) {
            is AiAction.ReadFile -> {
                emit(AgentEvent.ReadingFile(action.path))
                val result = readFile(action.path)
                if (result.success) {
                    emit(AgentEvent.FileChanged(action.path, 0, 0))
                }
                // Emit the file content as a command output-like event
                emit(AgentEvent.CommandOutput(
                    command = "read:${action.path}",
                    line = result.output
                ))
            }

            is AiAction.WriteFile -> {
                emit(AgentEvent.WritingFile(action.path))
                val (oldContent, additions, deletions) = captureWriteMetrics(action.path, action.content)
                val result = writeFile(action.path, action.content)
                if (result.success) {
                    emit(AgentEvent.FileChanged(action.path, additions, deletions))
                }
                emit(AgentEvent.CommandOutput(
                    command = "write:${action.path}",
                    line = result.output
                ))
            }

            is AiAction.RunCommand -> {
                emit(AgentEvent.CommandOutput(
                    command = action.command,
                    line = "Running: ${action.command}",
                    isStderr = false
                ))
                // Execute command with real-time output streaming
                val outputBuilder = StringBuilder()
                runCommandWithOutput(action.command) { line, isStderr ->
                    emit(AgentEvent.CommandOutput(
                        command = action.command,
                        line = line,
                        isStderr = isStderr
                    ))
                    outputBuilder.appendLine(line)
                }
                emit(AgentEvent.CommandComplete(
                    command = action.command,
                    exitCode = 0
                ))
            }

            is AiAction.Plan -> {
                emit(AgentEvent.Thinking("Plan: ${action.text}"))
            }

            is AiAction.Think -> {
                emit(AgentEvent.Thinking(action.text))
            }

            is AiAction.Complete -> {
                emit(AgentEvent.TaskComplete(action.summary))
            }
        }
    }

    /**
     * Parse AI response text to extract actions.
     * Supports formats:
     *   read:/path/to/file
     *   write:/path/to/file  followed by ```content```
     *   run:command
     *   think:text
     *   plan:text
     *   complete:text
     */
    fun parseActions(aiResponse: String): List<AiAction> {
        val actions = mutableListOf<AiAction>()
        val lines = aiResponse.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                // Read action
                line.matches(Regex("^read\\s*[:：]\\s*.+", RegexOption.IGNORE_CASE)) -> {
                    val path = line.substringAfterAny(listOf(":", "：")).trim()
                    if (path.isNotBlank()) {
                        actions.add(AiAction.ReadFile(path))
                    }
                }

                // Write action - followed by ```content```
                line.matches(Regex("^write\\s*[:：]\\s*.+", RegexOption.IGNORE_CASE)) -> {
                    val path = line.substringAfterAny(listOf(":", "：")).trim()
                    if (path.isNotBlank()) {
                        // Look for fenced content
                        val contentStart = i + 1
                        var contentEnd = -1
                        var fenceChar = "```"
                        if (contentStart < lines.size && lines[contentStart].trim().startsWith("```")) {
                            fenceChar = lines[contentStart].trim()
                            // Find closing fence
                            for (j in (contentStart + 1) until lines.size) {
                                if (lines[j].trim() == "```" || lines[j].trim() == "````") {
                                    contentEnd = j
                                    break
                                }
                            }
                            if (contentEnd > contentStart) {
                                val content = lines.subList(contentStart + 1, contentEnd).joinToString("\n")
                                actions.add(AiAction.WriteFile(path, content))
                                i = contentEnd
                            }
                        }
                    }
                }

                // Run/exec command action
                line.matches(Regex("^run\\s*[:：]\\s*.+", RegexOption.IGNORE_CASE)) -> {
                    val command = line.substringAfterAny(listOf(":", "：")).trim()
                    if (command.isNotBlank()) {
                        actions.add(AiAction.RunCommand(command))
                    }
                }

                // Think action
                line.matches(Regex("^think\\s*[:：]\\s*.+", RegexOption.IGNORE_CASE)) -> {
                    val text = line.substringAfterAny(listOf(":", "：")).trim()
                    if (text.isNotBlank()) {
                        actions.add(AiAction.Think(text))
                    }
                }

                // Plan action
                line.matches(Regex("^plan\\s*[:：]\\s*.+", RegexOption.IGNORE_CASE)) -> {
                    val text = line.substringAfterAny(listOf(":", "：")).trim()
                    if (text.isNotBlank()) {
                        actions.add(AiAction.Plan(text))
                    }
                }

                // Complete action
                line.matches(Regex("^complete\\s*[:：]\\s*.+", RegexOption.IGNORE_CASE)) -> {
                    val summary = line.substringAfterAny(listOf(":", "：")).trim()
                    actions.add(AiAction.Complete(summary))
                }

                // Also detect emoji-prefixed formats
                line.matches(Regex("^📖\\s*阅读\\s*[:：]\\s*.+")) -> {
                    val path = line.substringAfterAny(listOf(":", "：")).trim()
                    if (path.isNotBlank()) actions.add(AiAction.ReadFile(path))
                }
                line.matches(Regex("^📝\\s*(创建|修改)\\s*[:：]\\s*.+")) -> {
                    val path = line.substringAfterAny(listOf(":", "：")).trim()
                    if (path.isNotBlank()) {
                        // Check for content in subsequent lines
                        val contentStart = i + 1
                        if (contentStart < lines.size && lines[contentStart].trim().startsWith("```")) {
                            val fenceLine = lines[contentStart].trim()
                            for (j in (contentStart + 1) until lines.size) {
                                if (lines[j].trim() == fenceLine || lines[j].trim() == "```" || lines[j].trim() == "````") {
                                    val content = lines.subList(contentStart + 1, j).joinToString("\n")
                                    actions.add(AiAction.WriteFile(path, content))
                                    i = j
                                    break
                                }
                            }
                        }
                    }
                }
                line.matches(Regex("^💻\\s*执行\\s*[:：]\\s*.+")) -> {
                    val command = line.substringAfterAny(listOf(":", "：")).trim()
                    if (command.isNotBlank()) actions.add(AiAction.RunCommand(command))
                }
                line.matches(Regex("^🧠\\s*(计划|分析|思考)\\s*[:：]\\s*.+")) -> {
                    val text = line.substringAfterAny(listOf(":", "：")).trim()
                    if (text.isNotBlank()) actions.add(AiAction.Think(text))
                }
                line.matches(Regex("^✅\\s*完成\\s*[:：]\\s*.+")) -> {
                    val summary = line.substringAfterAny(listOf(":", "：")).trim()
                    actions.add(AiAction.Complete(summary))
                }
            }
            i++
        }

        return actions
    }

    /**
     * Extract the main text content (non-action parts) from an AI response.
     */
    fun extractTextContent(aiResponse: String): String {
        val sb = StringBuilder()
        val lines = aiResponse.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip lines that are action directives
            val isActionLine = line.matches(Regex("^(read|write|run|think|plan|complete)\\s*[:：].+", RegexOption.IGNORE_CASE)) ||
                    line.matches(Regex("^[📖📝💻🧠✅].+[:：]\\s*.+"))

            if (!isActionLine) {
                sb.appendLine(lines[i])
            }
            i++
        }

        return sb.toString().trim()
    }

    /**
     * Write a file at the given path (relative to repoDir).
     */
    suspend fun writeFile(relativePath: String, content: String): ActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val base = repoDir ?: workspaceDir
                val file = base.resolve(relativePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                Log.d(TAG, "Written file: $relativePath (${content.length} chars)")
                ActionResult(
                    action = AiAction.WriteFile(relativePath, content),
                    success = true,
                    output = "Written $relativePath (${content.length} chars)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write file: $relativePath", e)
                ActionResult(
                    action = AiAction.WriteFile(relativePath, content),
                    success = false,
                    error = "Failed to write $relativePath: ${e.message}"
                )
            }
        }
    }

    /**
     * Read a file at the given path (relative to repoDir).
     */
    suspend fun readFile(relativePath: String): ActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val base = repoDir ?: workspaceDir
                val file = base.resolve(relativePath)
                if (!file.exists()) {
                    return@withContext ActionResult(
                        action = AiAction.ReadFile(relativePath),
                        success = false,
                        error = "File not found: $relativePath"
                    )
                }
                val content = file.readText()
                Log.d(TAG, "Read file: $relativePath (${content.length} chars)")
                ActionResult(
                    action = AiAction.ReadFile(relativePath),
                    success = true,
                    output = content
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read file: $relativePath", e)
                ActionResult(
                    action = AiAction.ReadFile(relativePath),
                    success = false,
                    error = "Failed to read $relativePath: ${e.message}"
                )
            }
        }
    }

    /**
     * Run a shell command in the repo directory and stream output.
     */
    suspend fun runCommand(command: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val base = repoDir ?: workspaceDir
                val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
                val pb = ProcessBuilder(shell, "-c", command)
                    .directory(base)
                    .redirectErrorStream(true)

                val proc = pb.start()
                val output = proc.inputStream.bufferedReader().readText()
                val exitCode = proc.waitFor()

                if (exitCode != 0) {
                    Log.w(TAG, "Command '$command' exited $exitCode")
                }
                Result.success(output)
            } catch (e: Exception) {
                Log.e(TAG, "Command failed: $command", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get the repo directory for confirmation dialogs.
     */
    fun getRepoDir(): File = repoDir ?: workspaceDir

    /**
     * Check if git operator is available.
     */
    fun getGitOperator(): GitOperator = gitOperator

    /**
     * Get the added/deleted line counts for a write operation.
     */
    private suspend fun captureWriteMetrics(relativePath: String, newContent: String): Triple<String, Int, Int> {
        val base = repoDir ?: workspaceDir
        val file = base.resolve(relativePath)
        val oldContent = if (file.exists()) file.readText() else ""
        val oldLines = oldContent.lines().size
        val newLines = newContent.lines().size
        val additions = maxOf(0, newLines - oldLines)
        val deletions = maxOf(0, oldLines - newLines)
        return Triple(oldContent, additions, deletions)
    }

    /**
     * Execute a command with streaming output via callback.
     */
    private suspend fun runCommandWithOutput(
        command: String,
        onOutput: (line: String, isStderr: Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val base = repoDir ?: workspaceDir
                val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
                val pb = ProcessBuilder(shell, "-c", command)
                    .directory(base)
                    .redirectErrorStream(true)

                val proc = pb.start()

                // Read stdout (merged with stderr)
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onOutput(line ?: "", false)
                }

                proc.waitFor()
            } catch (e: Exception) {
                onOutput("Error: ${e.message}", true)
            }
        }
    }
}

private fun String.substringAfterAny(delimiters: List<String>): String {
    delimiters.forEach { delimiter ->
        val index = this.indexOf(delimiter)
        if (index >= 0) {
            return this.substring(index + delimiter.length)
        }
    }
    return this
}
