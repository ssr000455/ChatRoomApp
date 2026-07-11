package com.chatroom.app.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,  // "user", "assistant", "system"
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null,
    // Coding assistant display fields
    val terminalCommands: List<TerminalCommand> = emptyList(),
    val fileChanges: List<FileChangeSummary> = emptyList(),
    // Agent execution fields
    val agentActions: List<AgentActionDisplay> = emptyList(),
    val executionSteps: List<ExecutionStep> = emptyList(),
    // Agent loop context
    val agentState: AgentState? = null,
    val isAgentMessage: Boolean = false,
    val agentRound: Int = 0
) {
    /**
     * Sanitize after Gson deserialization: null lists -> empty lists, null Strings -> defaults.
     */
    fun sanitize(): ChatMessage = copy(
        attachments = (attachments ?: emptyList()).map { it.sanitize() },
        terminalCommands = (terminalCommands ?: emptyList()).map { it.sanitize() },
        fileChanges = fileChanges ?: emptyList(),
        agentActions = agentActions ?: emptyList(),
        executionSteps = executionSteps ?: emptyList(),
        agentState = agentState?.sanitize(),
        role = role ?: "assistant",
        content = content ?: ""
    )
}

/**
 * Display model for an AI agent action shown in chat bubbles.
 */
data class AgentActionDisplay(
    val type: String, // "read", "write", "command", "plan", "think", "complete"
    val description: String,
    val path: String = "",
    val status: String = "pending" // "pending", "running", "done", "error"
)

/**
 * Execution step result shown in chat.
 */
data class ExecutionStep(
    val stepNumber: Int,
    val actionType: String,
    val description: String,
    val output: String = "",
    val exitCode: Int? = null,
    val success: Boolean = true,
    val durationMs: Long = 0
)

data class Attachment(
    val name: String,
    val type: String,  // "image", "file", "url"
    val url: String,
    val size: Long = 0
) {
    fun sanitize(): Attachment = copy(
        name = name ?: "",
        type = type ?: "",
        url = url ?: ""
    )
}

data class ChatRequest(
    val model: String = "gpt-4o",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val stream: Boolean = false,
    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = null
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

// Terminal command output embedded in chat
data class TerminalCommand(
    val command: String,
    val output: String,
    val exitCode: Int,
    val workingDirectory: String = ""
) {
    fun sanitize(): TerminalCommand = copy(
        command = command ?: "",
        output = output ?: "",
        workingDirectory = workingDirectory ?: ""
    )
}

// Summary of file changes by AI
data class FileChangeSummary(
    val filePath: String,
    val additions: Int = 0,
    val deletions: Int = 0
)
