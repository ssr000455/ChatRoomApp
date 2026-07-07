package com.chatroom.app.data.model

import java.util.UUID

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarEmoji: String = "\uD83D\uDE0A",  // default smiley
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.openai.com/v1",
    val systemPrompt: String = "You are a helpful assistant.",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
