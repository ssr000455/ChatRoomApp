package com.chatroom.app.data.model

import java.util.UUID

data class ApiAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o",
    val reasoningModel: String = "o1-mini",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
