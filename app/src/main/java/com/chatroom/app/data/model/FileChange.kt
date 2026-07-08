package com.chatroom.app.data.model

import java.util.UUID

data class FileChange(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val originalContent: String,
    val newContent: String,
    val diff: String = "",
    val status: ChangeStatus = ChangeStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ChangeStatus { PENDING, ACCEPTED, REJECTED }
