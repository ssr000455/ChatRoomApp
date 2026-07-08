package com.chatroom.app.data.model

import java.util.UUID

enum class SessionType { CHAT, CODING_ASSISTANT }

enum class SessionMode { CHAT, TERMINAL, REPO_HOME }

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val apiAccountId: String = "",
    val identityId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val systemPrompt: String = "You are a helpful assistant.",
    val webSearchEnabled: Boolean = false,
    val deepThinkingEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Coding Assistant fields
    val type: SessionType = SessionType.CHAT,
    val mode: SessionMode = SessionMode.CHAT,
    val repoUrl: String = "",
    val repoOwner: String = "",
    val repoName: String = "",
    val repoBranch: String = "main",
    val localPath: String = "",
    val repoToken: String = "",
    val pendingChanges: List<FileChange> = emptyList()
) {
    val totalTokens: Int
        get() = messages.sumOf { it.content.length / 2 }

    val isCodingAssistant: Boolean
        get() = type == SessionType.CODING_ASSISTANT

    val repoDisplayName: String
        get() = if (repoOwner.isNotBlank() && repoName.isNotBlank()) "$repoOwner/$repoName" else title
}
