package com.chatroom.app.terminal

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * GitHub CLI (gh) operations for the AI coding assistant.
 * Wraps common gh commands: auth, repo create, push, list.
 * All methods check for gh availability before executing.
 */
class GitHubOperator {

    companion object {
        private const val TAG = "GitHubOperator"
    }

    /**
     * Check if the gh CLI is installed on the system.
     */
    fun isGhInstalled(): Boolean {
        return try {
            val proc = ProcessBuilder("gh", "--version")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.contains("gh version")
        } catch (e: Exception) {
            Log.w(TAG, "gh CLI not available", e)
            false
        }
    }

    /**
     * Authenticate with GitHub using a Personal Access Token.
     * Uses `gh auth login --with-token` via stdin.
     */
    fun authWithToken(token: String): Boolean {
        return try {
            val pb = ProcessBuilder("gh", "auth", "login", "--with-token")
                .redirectErrorStream(true)
            val proc = pb.start()
            proc.outputStream.bufferedWriter().use { writer ->
                writer.write(token)
                writer.flush()
            }
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "gh auth login succeeded")
                true
            } else {
                Log.w(TAG, "gh auth login failed: $output")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "gh auth login error", e)
            false
        }
    }

    /**
     * Check if the user is currently authenticated with gh.
     */
    fun isAuthenticated(): Boolean {
        return try {
            val proc = ProcessBuilder("gh", "auth", "status")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            exitCode == 0 && output.contains("Logged in")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create a new repository on GitHub.
     * @param name Repository name
     * @param description Optional description
     * @param isPrivate Whether the repo should be private
     * @param dir Directory to initialize as the repo (optional, for `gh repo create --source`)
     * @return The created repo URL, or null on failure
     */
    fun createRepo(
        name: String,
        description: String = "",
        isPrivate: Boolean = true,
        dir: File? = null
    ): String? {
        return try {
            val args = mutableListOf(
                "gh", "repo", "create", name,
                if (isPrivate) "--private" else "--public",
                "--description", description,
                "--push"
            )
            if (dir != null) {
                args.add("--source")
                args.add(dir.absolutePath)
                args.add("--remote")
                args.add("origin")
            }
            val pb = ProcessBuilder(args)
                .redirectErrorStream(true)
            if (dir != null) {
                pb.directory(dir)
            }
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "Created repo: $name -> $output")
                // Parse URL from output (e.g. "https://github.com/owner/repo")
                output.lines().firstOrNull { it.contains("github.com") }?.trim()
                    ?: "https://github.com/$name"
            } else {
                Log.w(TAG, "Failed to create repo $name: $output")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "gh repo create error", e)
            null
        }
    }

    /**
     * List repositories for the authenticated user.
     * Returns list of "owner/repo" strings.
     */
    fun listRepos(limit: Int = 20): List<String> {
        return try {
            val proc = ProcessBuilder(
                "gh", "repo", "list",
                "--limit", limit.toString(),
                "--json", "nameWithOwner",
                "--jq", ".[].nameWithOwner"
            )
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "gh repo list error", e)
            emptyList()
        }
    }

    /**
     * Get the gh CLI version string.
     */
    fun getVersion(): String {
        return try {
            val proc = ProcessBuilder("gh", "--version")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.lines().firstOrNull() ?: "unknown"
        } catch (e: Exception) {
            "not installed"
        }
    }

    /**
     * Run a generic gh command and return the output.
     */
    fun runGhCommand(vararg args: String, dir: File? = null): String? {
        return try {
            val cmd = arrayOf("gh") + args
            val pb = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
            if (dir != null) {
                pb.directory(dir)
            }
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                output
            } else {
                Log.w(TAG, "gh ${args.joinToString(" ")} failed: $output")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "gh command failed: ${args.joinToString(" ")}", e)
            null
        }
    }
}
