package com.chatroom.app.data.model

import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val accountId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val systemPrompt: String = "You are a helpful assistant.",
    val webSearchEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalTokens: Int
        get() = messages.sumOf { it.content.length / 2 }
}
