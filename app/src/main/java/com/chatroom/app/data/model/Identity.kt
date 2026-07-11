package com.chatroom.app.data.model

import java.util.UUID

data class Identity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val avatarEmoji: String = "\uD83D\uDE0A",
    val photoUri: String = "",
    val photoData: String = "",
    val personality: String = "",
    val knowledge: String = "",
    val tone: String = "",
    val extraNotes: String = "",
    // Coding-specific fields
    val codingStyle: String = "",
    val preferredStack: String = "",
    val codingExperience: String = "",
    val preferredLanguage: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun sanitize(): Identity = copy(
        name = name ?: "Unknown",
        description = description ?: "",
        avatarEmoji = avatarEmoji ?: "\uD83D\uDE0A",
        photoUri = photoUri ?: "",
        photoData = photoData ?: "",
        personality = personality ?: "",
        knowledge = knowledge ?: "",
        tone = tone ?: "",
        extraNotes = extraNotes ?: "",
        codingStyle = codingStyle ?: "",
        preferredStack = preferredStack ?: "",
        codingExperience = codingExperience ?: "",
        preferredLanguage = preferredLanguage ?: ""
    )

    fun toSystemPrompt(): String {
        val parts = mutableListOf<String>()
        if (name.isNotBlank()) parts.add("You are $name.")
        if (description.isNotBlank()) parts.add(description)
        if (personality.isNotBlank()) parts.add("Personality: $personality")
        if (knowledge.isNotBlank()) parts.add("Knowledge domains: $knowledge")
        if (tone.isNotBlank()) parts.add("Communication style: $tone")
        // Coding-specific traits
        if (codingStyle.isNotBlank()) parts.add("Coding style: $codingStyle")
        if (preferredStack.isNotBlank()) parts.add("Preferred tech stack: $preferredStack")
        if (codingExperience.isNotBlank()) parts.add("Experience level: $codingExperience")
        if (preferredLanguage.isNotBlank()) parts.add("Preferred programming language: $preferredLanguage")
        if (extraNotes.isNotBlank()) parts.add(extraNotes)
        return parts.joinToString("\n")
    }
}
