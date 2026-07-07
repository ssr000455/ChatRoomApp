package com.chatroom.app.data.model

/**
 * Account is deprecated — use ApiAccount + Identity instead.
 * Kept here only for data migration; not used in new code.
 */
@Deprecated("Split into ApiAccount and Identity")
data class Account(
    val id: String = "",
    val name: String = "",
    val avatarEmoji: String = "\uD83D\uDE0A",
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.openai.com/v1",
    val systemPrompt: String = "You are a helpful assistant.",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
