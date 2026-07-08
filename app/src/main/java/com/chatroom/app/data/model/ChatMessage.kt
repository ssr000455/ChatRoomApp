package com.chatroom.app.data.model

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val role: String,  // "user", "assistant", "system"
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)

data class Attachment(
    val name: String,
    val type: String,  // "image", "file", "url"
    val url: String,
    val size: Long = 0
)

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
