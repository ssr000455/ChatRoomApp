package com.chatroom.app.terminal

import android.util.Log
import com.chatroom.app.data.api.ChatApiService
import com.chatroom.app.data.model.AgentEvent
import com.chatroom.app.data.model.AgentState
import com.chatroom.app.data.model.AiAction
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ChatRequest
import com.chatroom.app.data.model.ExecutionStep
import com.chatroom.app.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Core AI Agent loop engine.
 * Manages multi-turn conversation with the AI, executing actions and maintaining context.
 * When repoDir is null, the agent runs in blank-workspace mode for creating projects from scratch.
 */
class CodexAgent(
    private val repoDir: File?,
    private val apiAccount: ApiAccount,
    private val terminalHistory: TerminalHistory
) {
    companion object {
        private const val TAG = "CodexAgent"
        private const val MAX_ROUNDS = 25
        private const val MAX_CONTEXT_TOKENS = 80000
    }

    private val gitOperator = GitOperator(repoDir)
    private val contextBuilder = CodexContext(repoDir)
    private val actionExecutor = ActionExecutor(repoDir, gitOperator)

    // Pending dangerous command confirmations
    private val pendingConfirmations = mutableMapOf<String, String>() // confirmId -> command

    /**
     * Process user input and run the agent loop.
     * Returns a Flow of AgentEvents for real-time UI updates.
     */
    suspend fun processUserInput(
        session: Session,
        userText: String
    ): Flow<AgentEvent> = flow {
        Log.d(TAG, "Starting agent loop for: ${userText.take(100)}")

        // Initial state
        emit(AgentEvent.Thinking("Understanding your request: ${userText.take(100)}"))

        // Build the agent loop message list
        val messages = buildMessageList(session, userText)
        var currentState = session.agentState.copy(
            taskDescription = userText,
            isActive = true,
            round = 0
        )

        // Agent loop
        var round = 0
        var keepLooping = true
        val allResults = mutableListOf<String>()

        while (keepLooping && round < MAX_ROUNDS) {
            round++
            currentState = currentState.copy(round = round)
            emit(AgentEvent.Thinking("Round $round: Consulting AI..."))

            // Build context for this round
            val systemContext = contextBuilder.buildSystemContext(
                session = session.copy(agentState = currentState),
                lastActionResults = allResults.takeLast(5)
            )

            // Make API call
            val aiResponse = callAI(systemContext, messages)

            if (aiResponse == null) {
                emit(AgentEvent.Error("AI response failed"))
                break
            }

            // Parse actions from response
            val actions = actionExecutor.parseActions(aiResponse)

            if (actions.isEmpty()) {
                // No actions found - this might be the final text response
                emit(AgentEvent.Thinking(aiResponse.take(500)))
                keepLooping = false
                break
            }

            // Execute each action
            var hasCompleteAction = false
            var hasNonPlanAction = false
            val roundResults = mutableListOf<String>()

            for (action in actions) {
                when (action) {
                    is AiAction.Complete -> {
                        hasCompleteAction = true
                        emit(AgentEvent.TaskComplete(action.summary))
                        currentState = currentState.copy(
                            isActive = false,
                            summary = action.summary
                        )
                    }

                    is AiAction.Plan, is AiAction.Think -> {
                        val text = if (action is AiAction.Plan) action.text else (action as AiAction.Think).text
                        emit(AgentEvent.Thinking(text))
                        roundResults.add("[AI] ${text.take(200)}")
                    }

                    is AiAction.ReadFile -> {
                        // Emit reading event, read file, include result in next context
                        val result = withContext(Dispatchers.IO) {
                            actionExecutor.readFile(action.path)
                        }
                        if (result.success) {
                            val preview = result.output.take(2000)
                            roundResults.add("[READ] ${action.path} (${result.output.length} chars)")
                            emit(AgentEvent.CommandOutput(
                                command = "read:${action.path}",
                                line = "=== ${action.path} ===\n$preview\n... (${result.output.length} total chars)"
                            ))
                        } else {
                            roundResults.add("[READ FAIL] ${action.path}: ${result.error}")
                            emit(AgentEvent.CommandOutput(
                                command = "read:${action.path}",
                                line = "Error: ${result.error}",
                                isStderr = true
                            ))
                        }
                        hasNonPlanAction = true
                    }

                    is AiAction.WriteFile -> {
                        // Emit writing event, write file
                        emit(AgentEvent.WritingFile(action.path))
                        val result = withContext(Dispatchers.IO) {
                            actionExecutor.writeFile(action.path, action.content)
                        }
                        if (result.success) {
                            val additions = action.content.lines().size
                            emit(AgentEvent.FileChanged(action.path, additions, 0))
                            roundResults.add("[WRITE] ${action.path} (${action.content.length} chars)")
                        } else {
                            roundResults.add("[WRITE FAIL] ${action.path}: ${result.error}")
                        }
                        hasNonPlanAction = true
                    }

                    is AiAction.RunCommand -> {
                        val command = action.command
                        // Check for dangerous commands
                        if (gitOperator.isDangerousCommand(command)) {
                            val confirmId = "confirm_${System.currentTimeMillis()}"
                            pendingConfirmations[confirmId] = command
                            emit(AgentEvent.DangerousCommand(command, confirmId))
                            // Don't execute, wait for user confirmation
                            roundResults.add("[DANGEROUS] Command blocked: $command")
                        } else {
                            emit(AgentEvent.CommandOutput(command = command, line = "Running: $command"))
                            val result = withContext(Dispatchers.IO) {
                                actionExecutor.runCommand(command)
                            }
                            if (result.isSuccess) {
                                val output = result.getOrThrow()
                                terminalHistory.recordCommand(command, repoDir.absolutePath)
                                output.lines().forEach { line ->
                                    if (line.isNotBlank()) {
                                        emit(AgentEvent.CommandOutput(command = command, line = line))
                                    }
                                }
                                emit(AgentEvent.CommandComplete(command = command, exitCode = 0))
                                val preview = output.take(2000)
                                roundResults.add("[CMD] $$ command\n$preview")
                            } else {
                                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                emit(AgentEvent.CommandOutput(command = command, line = "Error: $error", isStderr = true))
                                roundResults.add("[CMD FAIL] $$command: $error")
                            }
                        }
                        hasNonPlanAction = true
                    }
                }
            }

            allResults.addAll(roundResults)

            if (hasCompleteAction) {
                keepLooping = false
            }

            // Update current state
            currentState = currentState.copy(
                completedSteps = currentState.completedSteps + roundResults.filter { it.startsWith("[WRITE]") || it.startsWith("[CMD]") }
            )

            // If only plan/think actions, stop after one round
            if (!hasNonPlanAction && !hasCompleteAction) {
                keepLooping = false
            }
        }

        if (round >= MAX_ROUNDS) {
            emit(AgentEvent.Thinking("Reached maximum rounds ($MAX_ROUNDS)"))
        }

        emit(AgentEvent.TaskComplete(currentState.summary.ifBlank { "Task completed after $round rounds." }))
        Log.d(TAG, "Agent loop finished after $round rounds")
    }

    /**
     * Confirm and execute a previously blocked dangerous command.
     */
    suspend fun confirmDangerousCommand(confirmId: String): Boolean {
        val command = pendingConfirmations.remove(confirmId) ?: return false
        Log.d(TAG, "Executing confirmed dangerous command: $command")
        return withContext(Dispatchers.IO) {
            try {
                actionExecutor.runCommand(command)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute confirmed command", e)
                false
            }
        }
    }

    /**
     * Reject a dangerous command.
     */
    fun rejectDangerousCommand(confirmId: String) {
        pendingConfirmations.remove(confirmId)
        Log.d(TAG, "Rejected dangerous command: $confirmId")
    }

    /**
     * Get the action executor for direct access.
     */
    fun getActionExecutor(): ActionExecutor = actionExecutor

    /**
     * Get the git operator for direct access.
     */
    fun getGitOperator(): GitOperator = gitOperator

    // ── Private ──

    private fun buildMessageList(session: Session, userText: String): List<ChatMessage> {
        val messages = mutableListOf(
            ChatMessage(role = "system", content = session.systemPrompt)
        )

        // Include coding assistant message history (non-system messages)
        val historyMessages = session.messages.filter { it.role != "system" }
        var tokenCount = 0
        for (msg in historyMessages.reversed()) {
            val tokens = msg.content.length / 2
            if (tokenCount + tokens > MAX_CONTEXT_TOKENS) break
            tokenCount += tokens
            messages.add(1, msg) // Insert after system prompt, maintaining order
        }

        messages.add(ChatMessage(role = "user", content = userText))
        return messages
    }

    private suspend fun callAI(systemContext: String, messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            try {
                val api = ChatApiService.create(apiAccount.apiBaseUrl)
                val contextMsg = ChatMessage(role = "system", content = systemContext)

                val fullMessages = mutableListOf(contextMsg)
                fullMessages.addAll(messages)

                val request = ChatRequest(
                    model = apiAccount.model,
                    messages = fullMessages,
                    maxTokens = 4096,
                    stream = false
                )

                Log.d(TAG, "API call to ${apiAccount.apiBaseUrl} with model ${apiAccount.model}")
                val response = api.chatCompletion(
                    authorization = "Bearer ${apiAccount.apiKey}",
                    request = request
                )

                if (response.isSuccessful) {
                    val aiMessage = response.body()?.choices?.firstOrNull()?.message
                    aiMessage?.content?.also {
                        Log.d(TAG, "AI response received: ${it.take(200)}")
                    }
                    aiMessage?.content
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "API error: ${response.code()} - $errorBody")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "API call failed", e)
                null
            }
        }
    }
}
