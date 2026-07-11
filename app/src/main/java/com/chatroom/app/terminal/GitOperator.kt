package com.chatroom.app.terminal

import android.util.Log
import com.chatroom.app.data.model.GitCommit
import com.chatroom.app.data.model.GitDiff
import com.chatroom.app.data.model.GitFileEntry
import com.chatroom.app.data.model.GitFileStatus
import com.chatroom.app.data.model.GitState
import com.chatroom.app.data.model.GitWorktree
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Git operations wrapper for the AI coding assistant.
 * All methods work on the repository directory set during construction.
 * When repoDir is null, only non-repo operations are available.
 */
class GitOperator(
    private val repoDir: File?
) {
    companion object {
        private const val TAG = "GitOperator"
    }

    /**
     * Capture the full git state snapshot.
     * Returns empty state if no repo directory is configured.
     */
    suspend fun captureState(): GitState {
        if (repoDir == null) return GitState(branch = "none", isDirty = false)
        val branch = getCurrentBranch()
        val statusLines = runGit("status", "--porcelain") ?: ""
        val staged = mutableListOf<GitFileEntry>()
        val unstaged = mutableListOf<GitFileEntry>()
        val untracked = mutableListOf<String>()

        statusLines.lines().forEach { line ->
            when {
                line.startsWith("??") -> untracked.add(line.substring(2).trim())
                line.startsWith(" ") -> {
                    // Unstaged changes
                    val status = parseStatusChar(line[1])
                    unstaged.add(GitFileEntry(path = line.substring(3).trim(), status = status))
                }
                else -> {
                    // Staged changes
                    val status = parseStatusChar(line[0])
                    staged.add(GitFileEntry(path = line.substring(3).trim(), status = status))
                }
            }
        }

        // Get ahead/behind counts
        val (ahead, behind) = getAheadBehind()

        // Get last commit info
        val lastCommitLog = runGit("log", "-1", "--format=%H%n%s") ?: ""
        val logLines = lastCommitLog.lines()
        val lastCommitHash = logLines.getOrElse(0) { "" }
        val lastCommitMessage = logLines.getOrElse(1) { "" }

        return GitState(
            branch = branch,
            isDirty = staged.isNotEmpty() || unstaged.isNotEmpty() || untracked.isNotEmpty(),
            stagedFiles = staged,
            unstagedFiles = unstaged,
            untrackedFiles = untracked,
            behindCount = behind,
            aheadCount = ahead,
            lastCommitMessage = lastCommitMessage,
            lastCommitHash = lastCommitHash
        )
    }

    /**
     * Get the diff for all unstaged changes.
     */
    suspend fun getDiff(): List<GitDiff> {
        val output = runGit("diff", "--stat") ?: return emptyList()
        val diffs = mutableListOf<GitDiff>()

        // Parse --stat output
        output.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split(" | ")
            if (parts.size >= 2) {
                val filePath = parts[0].trim()
                val stat = parts[1].trim()
                val addMatch = Regex("(\\d+) insertion").find(stat)
                val delMatch = Regex("(\\d+) deletion").find(stat)
                val additions = addMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val deletions = delMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                diffs.add(GitDiff(filePath = filePath, additions = additions, deletions = deletions))
            }
        }

        return diffs
    }

    /**
     * Get the full diff text for a specific file.
     */
    suspend fun getFileDiff(filePath: String): String {
        return runGit("diff", filePath) ?: ""
    }

    /**
     * Get the current branch name.
     */
    suspend fun getCurrentBranch(): String {
        return runGit("rev-parse", "--abbrev-ref", "HEAD")?.trim() ?: "unknown"
    }

    /**
     * Get the diff of staged changes.
     */
    suspend fun getStagedDiff(): String {
        return runGit("diff", "--cached") ?: ""
    }

    /**
     * Stage a file.
     */
    suspend fun stageFile(filePath: String): Boolean {
        val result = runGit("add", filePath)
        return result != null
    }

    /**
     * Stage all changes.
     */
    suspend fun stageAll(): Boolean {
        val result = runGit("add", "-A")
        return result != null
    }

    /**
     * Create a commit.
     */
    suspend fun commit(message: String): Boolean {
        val result = runGit("commit", "-m", message)
        return result != null
    }

    /**
     * Create a commit with a multi-line message (reads from stdin).
     */
    suspend fun commitWithMessage(message: String): Boolean {
        val result = runGitWithInput("commit", "-F", "-", message)
        return result != null
    }

    /**
     * Checkout a branch.
     */
    suspend fun checkout(branch: String): Boolean {
        val result = runGit("checkout", branch)
        return result != null
    }

    /**
     * Create and checkout a new branch.
     */
    suspend fun createBranch(branch: String): Boolean {
        val result = runGit("checkout", "-b", branch)
        return result != null
    }

    /**
     * Get log of recent commits.
     */
    suspend fun getLog(count: Int = 10): List<GitCommit> {
        val output = runGit(
            "log", "-${count}",
            "--format=%H%n%an%n%at%n%s%n---"
        ) ?: return emptyList()

        val commits = mutableListOf<GitCommit>()
        val entries = output.split("---\n")
        entries.forEach { entry ->
            val lines = entry.trim().lines()
            if (lines.size >= 4) {
                commits.add(GitCommit(
                    hash = lines[0],
                    author = lines[1],
                    timestamp = lines[2].toLongOrNull() ?: 0L,
                    message = lines[3]
                ))
            }
        }
        return commits
    }

    /**
     * Get all branches (local).
     */
    suspend fun getBranches(): List<String> {
        val output = runGit("branch") ?: return emptyList()
        return output.lines()
            .map { it.trim().removePrefix("* ").trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Get all worktrees.
     */
    suspend fun getWorktrees(): List<GitWorktree> {
        val output = runGit("worktree", "list") ?: return emptyList()
        return output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 2) {
                GitWorktree(
                    path = parts[0],
                    branch = parts[1].removePrefix("[").removeSuffix("]"),
                    isDetached = parts[1] == "(detached)"
                )
            } else null
        }
    }

    /**
     * Check if a file has uncommitted changes.
     */
    suspend fun hasUncommittedChanges(filePath: String): Boolean {
        val status = runGit("status", "--porcelain", filePath) ?: ""
        return status.isNotBlank()
    }

    /**
     * Get the diff stat summary.
     */
    suspend fun getDiffStat(): String {
        return runGit("diff", "--stat") ?: ""
    }

    /**
     * Check if git is available in the repo directory.
     * Returns false if no repo directory is configured.
     */
    fun isGitRepo(): Boolean {
        return repoDir != null && (File(repoDir, ".git").exists() || File(repoDir, ".git").isFile)
    }

    /**
     * Check if the git binary is installed on the system.
     */
    fun isGitInstalled(): Boolean {
        return try {
            val proc = ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.contains("git version")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initialize a new git repository at the given path.
     */
    fun initRepo(path: File): Boolean {
        return try {
            val proc = ProcessBuilder("git", "init")
                .directory(path)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            Log.d(TAG, "git init at $path: $output")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "git init failed", e)
            false
        }
    }

    /**
     * Set the git user config for the repository.
     */
    fun setUserConfig(path: File, name: String, email: String): Boolean {
        return try {
            val nameResult = ProcessBuilder("git", "config", "user.name", name)
                .directory(path)
                .redirectErrorStream(true)
                .start()
            nameResult.waitFor()
            val emailResult = ProcessBuilder("git", "config", "user.email", email)
                .directory(path)
                .redirectErrorStream(true)
                .start()
            emailResult.waitFor()
            true
        } catch (e: Exception) {
            Log.e(TAG, "git config failed", e)
            false
        }
    }

    /**
     * Check if a command looks dangerous.
     */
    fun isDangerousCommand(command: String): Boolean {
        val dangerousPatterns = listOf(
            Regex("""\brm\s+-rf\s+/"""),
            Regex("""\brm\s+--recursive"""),
            Regex("""\bgit\s+push\s+--force"""),
            Regex("""\bgit\s+push\s+-f\b"""),
            Regex("""\bdd\s+if=""".toRegex(RegexOption.IGNORE_CASE)),
            Regex("""\b:wq!\b"""),
            Regex("""\bmkfs\b"""),
            Regex("""\bfdisk\b"""),
            Regex("""\bchmod\s+-R\s+777\s+/"""),
            Regex("""\bshred\b"""),
            Regex("""\bformat\b"""),
            Regex("""\b>/\s+dev"""),
            Regex("""\bsudo\s+rm\b""")
        )
        return dangerousPatterns.any { it.containsMatchIn(command) }
    }

    // ── Private helpers ──

    private suspend fun runGit(vararg args: String): String? {
        if (repoDir == null) return null
        return try {
            val cmd = arrayOf("git") + args
            val pb = ProcessBuilder(*cmd)
                .directory(repoDir)
                .redirectErrorStream(true)

            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = reader.readText()
            val exitCode = proc.waitFor()

            if (exitCode != 0) {
                Log.w(TAG, "git ${args.joinToString(" ")} exited $exitCode: ${output.take(200)}")
            }
            output
        } catch (e: Exception) {
            Log.e(TAG, "git command failed: ${args.joinToString(" ")}", e)
            null
        }
    }

    private suspend fun runGitWithInput(vararg args: String, input: String): String? {
        if (repoDir == null) return null
        return try {
            val cmd = arrayOf("git") + args
            val pb = ProcessBuilder(*cmd)
                .directory(repoDir)
                .redirectErrorStream(true)

            val proc = pb.start()
            proc.outputStream.bufferedWriter().use { writer ->
                writer.write(input)
                writer.flush()
            }
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = reader.readText()
            val exitCode = proc.waitFor()

            if (exitCode != 0) {
                Log.w(TAG, "git ${args.joinToString(" ")} exited $exitCode")
            }
            output
        } catch (e: Exception) {
            Log.e(TAG, "git command failed: ${args.joinToString(" ")}", e)
            null
        }
    }

    private fun parseStatusChar(c: Char): GitFileStatus = when (c) {
        'M' -> GitFileStatus.MODIFIED
        'A' -> GitFileStatus.ADDED
        'D' -> GitFileStatus.DELETED
        'R' -> GitFileStatus.RENAMED
        'C' -> GitFileStatus.COPIED
        else -> GitFileStatus.MODIFIED
    }

    private suspend fun getAheadBehind(): Pair<Int, Int> {
        // Try to get the upstream branch
        val upstream = runGit("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}")?.trim()
            ?: return Pair(0, 0)
        if (upstream.isBlank()) return Pair(0, 0)

        val output = runGit("rev-list", "--left-right", "--count", "HEAD...$upstream")?.trim()
            ?: return Pair(0, 0)

        val parts = output.split("\\s+".toRegex())
        val ahead = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
        val behind = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        return Pair(ahead, behind)
    }
}
