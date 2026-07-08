package com.chatroom.app.data.model

import java.util.UUID

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gender: String = "",
    val personality: String = "",
    val hobbies: String = "",
    val background: String = "",
    val avatarUri: String = "",
    val avatarData: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toContextString(): String {
        val parts = mutableListOf<String>()
        parts.add("User name: $name")
        if (gender.isNotBlank()) parts.add("Gender: $gender")
        if (personality.isNotBlank()) parts.add("Personality: $personality")
        if (hobbies.isNotBlank()) parts.add("Hobbies: $hobbies")
        if (background.isNotBlank()) parts.add("Background: $background")
        return parts.joinToString("\n")
    }
}
