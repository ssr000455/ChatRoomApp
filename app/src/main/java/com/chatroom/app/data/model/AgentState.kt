package com.chatroom.app.data.model

/**
 * Models the state of a CodexAgent session.
 */
data class AgentState(
    val taskDescription: String = "",
    val completedSteps: List<String> = emptyList(),
    val currentStep: String = "",
    val nextSteps: List<String> = emptyList(),
    val round: Int = 0,
    val isActive: Boolean = false,
    val summary: String = ""
) {
    fun sanitize(): AgentState = copy(
        taskDescription = taskDescription ?: "",
        completedSteps = completedSteps ?: emptyList(),
        currentStep = currentStep ?: "",
        nextSteps = nextSteps ?: emptyList(),
        summary = summary ?: ""
    )
}

/**
 * Represents an action the AI wants to perform.
 */
sealed class AiAction {
    data class ReadFile(val path: String) : AiAction()
    data class WriteFile(val path: String, val content: String) : AiAction()
    data class RunCommand(val command: String) : AiAction()
    data class Plan(val text: String) : AiAction()
    data class Think(val text: String) : AiAction()
    data class Complete(val summary: String) : AiAction()
}

/**
 * Result of executing a single AI action.
 */
data class ActionResult(
    val action: AiAction,
    val success: Boolean,
    val output: String = "",
    val error: String = ""
)

/**
 * Events emitted by the CodexAgent during its loop.
 */
sealed class AgentEvent {
    data class Thinking(val text: String) : AgentEvent()
    data class ReadingFile(val path: String) : AgentEvent()
    data class WritingFile(val path: String) : AgentEvent()
    data class CommandOutput(
        val command: String,
        val line: String,
        val isStderr: Boolean = false
    ) : AgentEvent()
    data class CommandComplete(
        val command: String,
        val exitCode: Int
    ) : AgentEvent()
    data class FileChanged(
        val path: String,
        val additions: Int,
        val deletions: Int
    ) : AgentEvent()
    data class TaskComplete(val summary: String) : AgentEvent()
    data class DangerousCommand(
        val command: String,
        val confirmId: String
    ) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}

/**
 * Snapshot of execution context at a point in time.
 */
data class ContextSnapshot(
    val gitState: GitState = GitState(),
    val roundResults: List<String> = emptyList(),
    val sessionHistory: List<String> = emptyList(),
    val currentTask: String = "",
    val completedSteps: List<String> = emptyList(),
    val directoryTree: String = ""
)
