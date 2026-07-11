package com.chatroom.app.data.model

/**
 * Current git state for a repository.
 */
data class GitState(
    val branch: String = "",
    val isDirty: Boolean = false,
    val stagedFiles: List<GitFileEntry> = emptyList(),
    val unstagedFiles: List<GitFileEntry> = emptyList(),
    val untrackedFiles: List<String> = emptyList(),
    val behindCount: Int = 0,
    val aheadCount: Int = 0,
    val lastCommitMessage: String = "",
    val lastCommitHash: String = ""
)

/**
 * Represents a single file entry in git status.
 */
data class GitFileEntry(
    val path: String,
    val status: GitFileStatus = GitFileStatus.MODIFIED,
    val additions: Int = 0,
    val deletions: Int = 0
)

enum class GitFileStatus {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED, UNMODIFIED
}

/**
 * Represents a commit.
 */
data class GitCommit(
    val hash: String,
    val author: String,
    val message: String,
    val timestamp: Long = 0L,
    val files: List<GitFileEntry> = emptyList()
)

/**
 * Represents a git worktree.
 */
data class GitWorktree(
    val path: String,
    val branch: String,
    val isBare: Boolean = false,
    val isDetached: Boolean = false
)

/**
 * Diff result for a single file.
 */
data class GitDiff(
    val filePath: String,
    val diffText: String = "",
    val additions: Int = 0,
    val deletions: Int = 0
)
