package com.chatroom.app.data.model

import java.util.UUID

enum class SessionType { CHAT, CODING_ASSISTANT }

enum class SessionMode { CHAT, TERMINAL, REPO_HOME }

enum class AiAccessLevel {
    READ_ONLY,
    READ_WRITE,
    FULL_ACCESS
}

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
    val pendingChanges: List<FileChange> = emptyList(),
    // Access control & display
    val aiAccessLevel: AiAccessLevel = AiAccessLevel.READ_WRITE,
    val showAiChanges: Boolean = true
) {
    val totalTokens: Int
        get() = messages.sumOf { it.content.length / 2 }

    val isCodingAssistant: Boolean
        get() = type == SessionType.CODING_ASSISTANT

    val repoDisplayName: String
        get() = if (repoOwner.isNotBlank() && repoName.isNotBlank()) "$repoOwner/$repoName" else title

    /**
     * Sanitize after Gson deserialization: null lists -> empty lists, null Strings -> defaults.
     * Gson bypasses constructor defaults when deserializing old data.
     */
    fun sanitize(): Session = copy(
        messages = (messages ?: emptyList()).map { it.sanitize() },
        pendingChanges = (pendingChanges ?: emptyList()).map { it.sanitize() },
        title = title ?: "New Chat",
        apiAccountId = apiAccountId ?: "",
        identityId = identityId ?: "",
        systemPrompt = systemPrompt ?: "You are a helpful assistant.",
        repoUrl = repoUrl ?: "",
        repoOwner = repoOwner ?: "",
        repoName = repoName ?: "",
        repoBranch = repoBranch ?: "main",
        localPath = localPath ?: "",
        repoToken = repoToken ?: ""
    )
}
