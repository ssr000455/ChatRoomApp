package com.chatroom.app.terminal

import android.util.Log
import com.chatroom.app.data.model.AgentState
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.Session
import java.io.File

/**
 * Builds the context string injected into the AI system message for each agent loop round.
 * Collects git status, directory tree, file contents, execution history, and session state.
 * When repoDir is null, the agent operates in blank-workspace mode (no git repository).
 */
class CodexContext(
    private val repoDir: File?
) {
    companion object {
        private const val TAG = "CodexContext"
        private const val MAX_DIR_TREE_DEPTH = 4
        private const val MAX_DIR_ENTRIES = 50
    }

    private val gitOperator = GitOperator(repoDir)
    private val workspaceDir: File = repoDir ?: File(System.getProperty("java.io.tmpdir"), "codex-workspace")

    /**
     * Build the complete system context string for the next AI round.
     */
    suspend fun buildSystemContext(
        session: Session,
        roundResult: String = "",
        lastActionResults: List<String> = emptyList()
    ): String {
        val sb = StringBuilder()

        if (repoDir != null) {
            sb.appendLine("You are an AI coding assistant operating inside a git repository.")
            sb.appendLine()
            sb.appendLine("=== REPOSITORY ===")
            sb.appendLine("Repository: ${session.repoOwner}/${session.repoName}")
            sb.appendLine()

            // Git status
            appendGitStatus(sb)

            // Directory tree
            sb.appendLine("--- Directory Tree ---")
            sb.appendLine(buildDirectoryTree(repoDir))
            sb.appendLine()
        } else {
            sb.appendLine("You are an AI coding assistant in a blank workspace (no repository yet).")
            sb.appendLine()
            sb.appendLine("=== WORKSPACE ===")
            sb.appendLine("Workspace directory: $workspaceDir")
            sb.appendLine("No git repository has been connected yet.")
            sb.appendLine()
            sb.appendLine("You can CREATE NEW PROJECTS from scratch:")
            sb.appendLine("  - Create files with `write:path/to/file`")
            sb.appendLine("  - Run commands with `run:command` (e.g. `run:gradle init`, `run:npm init`, `run:git init`)")
            sb.appendLine("  - Initialize git in the workspace with `run:git init`")
            sb.appendLine("  - Later, the workspace can be connected to a remote repository")
            sb.appendLine()
        }

        // Current task
        sb.appendLine("=== CURRENT TASK ===")
        sb.appendLine("Task: ${session.agentState.taskDescription}")
        if (session.agentState.completedSteps.isNotEmpty()) {
            sb.appendLine("Completed steps:")
            session.agentState.completedSteps.forEach { step ->
                sb.appendLine("  - $step")
            }
        }
        if (session.agentState.currentStep.isNotBlank()) {
            sb.appendLine("Current step: ${session.agentState.currentStep}")
        }
        if (session.agentState.nextSteps.isNotEmpty()) {
            sb.appendLine("Next steps:")
            session.agentState.nextSteps.forEach { step ->
                sb.appendLine("  - $step")
            }
        }
        sb.appendLine()

        // Round result
        if (roundResult.isNotBlank()) {
            sb.appendLine("=== LAST ROUND RESULT ===")
            sb.appendLine(roundResult)
            sb.appendLine()
        }

        // Action results
        if (lastActionResults.isNotEmpty()) {
            sb.appendLine("=== ACTION RESULTS ===")
            lastActionResults.forEach { result ->
                sb.appendLine(result)
            }
            sb.appendLine()
        }

        // Session history summary
        appendSessionHistory(sb, session)

        // Agent protocol
        sb.appendLine("=== AGENT PROTOCOL ===")
        sb.appendLine("You respond with actions the system will execute for you.")
        sb.appendLine()
        sb.appendLine("Available actions:")
        sb.appendLine("  READ: `read:path/to/file` - I will read the file and show you its contents")
        sb.appendLine("  WRITE: `write:path/to/file` followed by ``` with the full new file content - I will write it")
        sb.appendLine("  COMMAND: `run:command` - I will execute the shell command and return output")
        sb.appendLine("  THINK/PLAN: `think:your thoughts` or `plan:your plan` - shown to user, no execution")
        sb.appendLine("  COMPLETE: `complete:summary of what was done` - ends the agent loop")
        sb.appendLine()
        sb.appendLine("Protocol:")
        sb.appendLine("1. First, plan by reading relevant files to understand the codebase")
        sb.appendLine("2. Then make changes by writing files")
        sb.appendLine("3. Verify by running commands like builds or tests")
        sb.appendLine("4. Fix any issues found")
        sb.appendLine("5. Complete when done")
        sb.appendLine()
        sb.appendLine("IMPORTANT: When writing a file, provide the COMPLETE new file content, not just a diff.")

        if (repoDir == null) {
            sb.appendLine()
            sb.appendLine("WORKFLOW for creating a new project:")
            sb.appendLine("  1. Plan the project structure first (directories, key files)")
            sb.appendLine("  2. Create files with `write:path/to/file` (e.g. write:README.md, write:src/main.kt)")
            sb.appendLine("  3. Initialize project tools with commands: `run:gradle init`, `run:npm init -y`, etc.")
            sb.appendLine("  4. Optionally initialize git: `run:git init`")
            sb.appendLine("  5. Optionally set up remote: `run:gh repo create <name> --private --push`")
            sb.appendLine("  6. Verify with build/test commands")
            sb.appendLine("  7. Complete when done")
        } else {
            sb.appendLine()
            sb.appendLine("GitHub CLI is available. You can use `run:gh ...` commands to:")
            sb.appendLine("  - Create remote repos: `run:gh repo create <name> --private --push`")
            sb.appendLine("  - Push changes: `run:git push -u origin <branch>`")
            sb.appendLine("  - Create PRs: `run:gh pr create --title \"...\" --body \"...\"`")
            sb.appendLine("  - List repos: `run:gh repo list`")
            sb.appendLine("  - View repo: `run:gh repo view`")
        }

        sb.appendLine()
        sb.appendLine("You are on round ${session.agentState.round + 1}.")

        return sb.toString()
    }

    /**
     * Build context from specific file contents.
     */
    suspend fun readFileContent(filePath: String): String {
        val base = repoDir ?: workspaceDir
        val file = base.resolve(filePath)
        if (!file.exists() || !file.isFile) {
            return "ERROR: File not found: $filePath"
        }
        return try {
            file.readText()
        } catch (e: Exception) {
            "ERROR: Cannot read file $filePath: ${e.message}"
        }
    }

    /**
     * Get the directory tree as a formatted string.
     */
    fun buildDirectoryTree(dir: File, prefix: String = "", depth: Int = 0): String {
        if (depth > MAX_DIR_TREE_DEPTH) return "${prefix}...\n"
        val sb = StringBuilder()
        val files = dir.listFiles()?.take(MAX_DIR_ENTRIES) ?: return sb.toString()

        files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEachIndexed { index, file ->
            val isLast = index == files.size - 1 || index == MAX_DIR_ENTRIES - 1
            val connector = if (isLast) "└── " else "├── "
            sb.append("$prefix$connector${file.name}")
            if (file.isDirectory) {
                sb.appendLine("/")
                val extension = if (isLast) "    " else "│   "
                sb.append(buildDirectoryTree(file, "$prefix$extension", depth + 1))
            } else {
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    /**
     * Build a summary of recent conversation history.
     */
    private fun appendSessionHistory(sb: StringBuilder, session: Session) {
        sb.appendLine("=== SESSION HISTORY SUMMARY ===")
        val agentMessages = session.messages.filter { it.isAgentMessage }
        val recentMessages = agentMessages.takeLast(5)

        if (recentMessages.isEmpty()) {
            sb.appendLine("No agent messages yet.")
        } else {
            recentMessages.forEach { msg ->
                val summary = msg.content.take(150).replace("\n", " ")
                sb.appendLine("Round ${msg.agentRound}: $summary")
                if (msg.executionSteps.isNotEmpty()) {
                    msg.executionSteps.forEach { step ->
                        val status = if (step.success) "OK" else "FAIL"
                        sb.appendLine("  [$status] ${step.actionType}: ${step.description} (${step.durationMs}ms)")
                    }
                }
            }
        }
        sb.appendLine()
    }

    private suspend fun appendGitStatus(sb: StringBuilder) {
        try {
            val gitState = gitOperator.captureState()
            sb.appendLine("--- Git Status ---")
            sb.appendLine("Branch: ${gitState.branch}")

            if (gitState.aheadCount > 0 || gitState.behindCount > 0) {
                sb.appendLine("  ahead ${gitState.aheadCount}, behind ${gitState.behindCount}")
            }

            if (gitState.lastCommitHash.isNotBlank()) {
                sb.appendLine("Last commit: ${gitState.lastCommitHash.take(8)} - ${gitState.lastCommitMessage.take(60)}")
            }

            if (gitState.stagedFiles.isNotEmpty()) {
                sb.appendLine("Staged changes:")
                gitState.stagedFiles.forEach { file ->
                    sb.appendLine("  ${file.status.name.lowercase()}: ${file.path}")
                }
            }

            if (gitState.unstagedFiles.isNotEmpty()) {
                sb.appendLine("Unstaged changes:")
                gitState.unstagedFiles.forEach { file ->
                    sb.appendLine("  modified: ${file.path}")
                }
            }

            if (gitState.untrackedFiles.isNotEmpty()) {
                sb.appendLine("Untracked files:")
                gitState.untrackedFiles.forEach { file ->
                    sb.appendLine("  $file")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Git status unavailable: ${e.message}")
        }
    }
}
