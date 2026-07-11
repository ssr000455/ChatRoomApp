package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.model.AgentEvent
import com.chatroom.app.data.model.AgentState
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ExecutionStep
import com.chatroom.app.data.model.GitState
import com.chatroom.app.data.model.Identity
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.repository.ApiAccountRepository
import com.chatroom.app.data.repository.IdentityRepository
import com.chatroom.app.data.repository.SessionRepository
import com.chatroom.app.terminal.CodexAgent
import com.chatroom.app.terminal.DevToolExecutor
import com.chatroom.app.terminal.GitOperator
import com.chatroom.app.ui.components.DevToolConfig
import com.chatroom.app.ui.components.DevToolType
import com.chatroom.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class CodexUiState(
    val isAgentRunning: Boolean = false,
    val currentRound: Int = 0,
    val agentEvents: List<AgentEvent> = emptyList(),
    val thinkingText: String = "",
    val gitState: GitState = GitState(),
    val error: String? = null,
    val confirmDialog: ConfirmDialogState? = null,
    // Dev tool state
    val devToolConfig: DevToolConfig = DevToolConfig(),
    val devToolResult: String? = null,
    val isDevToolRunning: Boolean = false,
    val showDevTools: Boolean = false
)

data class ConfirmDialogState(
    val confirmId: String,
    val command: String,
    val message: String = "This command appears dangerous. Allow execution?"
)

/**
 * ViewModel for the Codex AI coding assistant.
 * Manages the agent loop, git state, and UI state for agent operations.
 * Separate from ChatViewModel as specified in the architecture.
 */
class CodexViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionRepo = SessionRepository(application)
    private val apiAccountRepo = ApiAccountRepository(application)
    private val identityRepo = IdentityRepository(application)

    private val _uiState = MutableStateFlow(CodexUiState())
    val uiState: StateFlow<CodexUiState> = _uiState.asStateFlow()

    val sessions: StateFlow<List<Session>> = sessionRepo.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<Session?> = sessionRepo.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeApiAccount: StateFlow<ApiAccount?> = apiAccountRepo.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // The current agent instance
    private var currentAgent: CodexAgent? = null
    private var agentJob: Job? = null

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepo.setActiveSession(sessionId)
        }
    }

    /**
     * Start the agent loop with the user's input.
     * Creates the CodexAgent, sets up the session, and runs the loop.
     */
    fun startAgentLoop(userText: String) {
        if (_uiState.value.isAgentRunning) return

        agentJob?.cancel()
        agentJob = viewModelScope.launch {
            val session = activeSession.first() ?: run {
                _uiState.value = _uiState.value.copy(error = "No active session")
                return@launch
            }
            val apiAccount = activeApiAccount.first() ?: run {
                _uiState.value = _uiState.value.copy(error = "No API account configured")
                return@launch
            }

            if (!session.isCodingAssistant) {
                _uiState.value = _uiState.value.copy(error = "Session is not a coding assistant")
                return@launch
            }

            // Determine repo directory (null = blank workspace mode for creating new projects)
            val repoDir: File? = if (session.repoDir.isNotBlank()) {
                File(session.repoDir)
            } else if (session.localPath.isNotBlank() && session.repoName.isNotBlank()) {
                File(session.localPath, session.repoName)
            } else if (session.repoName.isNotBlank()) {
                getApplication<Application>().filesDir.resolve("workspace").resolve(session.repoName)
            } else {
                null // Blank workspace — project will be created from scratch
            }

            // Initialize agent
            val agent = CodexAgent(
                repoDir = repoDir,
                apiAccount = apiAccount,
                terminalHistory = com.chatroom.app.terminal.TerminalHistory(
                    getApplication<Application>().filesDir.resolve("terminal_history")
                ).also { it.init(session.id) }
            )
            currentAgent = agent

            // Reset UI state
            _uiState.value = CodexUiState(
                isAgentRunning = true,
                currentRound = 0,
                gitState = GitState()
            )

            // Add user message to session
            val userMessage = ChatMessage(
                role = "user",
                content = userText,
                isAgentMessage = false
            )
            sessionRepo.addMessageToSession(session.id, userMessage)

            // Create initial agent message
            val agentMessage = ChatMessage(
                role = "assistant",
                content = "",
                isAgentMessage = true,
                agentRound = 0
            )
            sessionRepo.addMessageToSession(session.id, agentMessage)

            try {
                // Collect current events for the agent message
                val events = mutableListOf<AgentEvent>()
                var currentRound = 0
                var stepNumber = 0
                val executionSteps = mutableListOf<ExecutionStep>()

                // Run the agent loop and collect events
                agent.processUserInput(session, userText).collect { event ->
                    events.add(event)
                    _uiState.value = _uiState.value.copy(
                        agentEvents = events.toList()
                    )

                    when (event) {
                        is AgentEvent.Thinking -> {
                            _uiState.value = _uiState.value.copy(thinkingText = event.text)
                        }
                        is AgentEvent.ReadingFile -> {
                            stepNumber++
                            executionSteps.add(ExecutionStep(
                                stepNumber = stepNumber,
                                actionType = "read",
                                description = "Reading ${event.path}"
                            ))
                        }
                        is AgentEvent.WritingFile -> {
                            stepNumber++
                            executionSteps.add(ExecutionStep(
                                stepNumber = stepNumber,
                                actionType = "write",
                                description = "Writing ${event.path}"
                            ))
                        }
                        is AgentEvent.CommandOutput -> {
                            // Update the last execution step with output
                            if (executionSteps.isNotEmpty()) {
                                val lastStep = executionSteps.last()
                                val newOutput = if (lastStep.output.isBlank()) event.line
                                    else lastStep.output + "\n" + event.line
                                executionSteps[executionSteps.size - 1] = lastStep.copy(
                                    output = newOutput.take(5000)
                                )
                            }
                        }
                        is AgentEvent.CommandComplete -> {
                            // Mark the last command step as complete
                            if (executionSteps.isNotEmpty()) {
                                val lastStep = executionSteps.last()
                                executionSteps[executionSteps.size - 1] = lastStep.copy(
                                    success = event.exitCode == 0
                                )
                            }
                        }
                        is AgentEvent.FileChanged -> {
                            stepNumber++
                            executionSteps.add(ExecutionStep(
                                stepNumber = stepNumber,
                                actionType = "change",
                                description = "${event.path} (+${event.additions} -${event.deletions})"
                            ))
                        }
                        is AgentEvent.TaskComplete -> {
                            _uiState.value = _uiState.value.copy(
                                thinkingText = "Complete: ${event.summary}"
                            )
                        }
                        is AgentEvent.DangerousCommand -> {
                            _uiState.value = _uiState.value.copy(
                                confirmDialog = ConfirmDialogState(
                                    confirmId = event.confirmId,
                                    command = event.command
                                )
                            )
                        }
                        is AgentEvent.Error -> {
                            _uiState.value = _uiState.value.copy(error = event.message)
                        }
                    }
                }

                // Update the agent message with collected steps
                val finalContent = buildAgentSummary(events, executionSteps)
                val updatedAgentMsg = agentMessage.copy(
                    content = finalContent,
                    executionSteps = executionSteps.toList(),
                    agentRound = currentRound,
                    agentState = session.agentState.copy(
                        isActive = false,
                        summary = finalContent
                    )
                )

                // Remove the placeholder and add the final message
                sessionRepo.removeMessageAtIndex(session.id, session.messages.size - 1)
                sessionRepo.addMessageToSession(session.id, updatedAgentMsg)

                // Update session agent state
                val updatedSession = activeSession.first() ?: return@collect
                sessionRepo.updateSession(updatedSession.copy(
                    agentState = session.agentState.copy(
                        isActive = false,
                        summary = finalContent
                    )
                ))

            } catch (e: Exception) {
                AppLogger.e("CodexVM", "Agent loop failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Agent loop error: ${e.message}",
                    isAgentRunning = false
                )
            } finally {
                _uiState.value = _uiState.value.copy(isAgentRunning = false)
                currentAgent = null
            }
        }
    }

    /**
     * Confirm a dangerous command.
     */
    fun confirmCommand() {
        val dialog = _uiState.value.confirmDialog ?: return
        viewModelScope.launch {
            currentAgent?.confirmDangerousCommand(dialog.confirmId)
            _uiState.value = _uiState.value.copy(confirmDialog = null)
        }
    }

    /**
     * Reject a dangerous command.
     */
    fun rejectCommand() {
        val dialog = _uiState.value.confirmDialog ?: return
        currentAgent?.rejectDangerousCommand(dialog.confirmId)
        _uiState.value = _uiState.value.copy(confirmDialog = null)
    }

    /**
     * Stop the current agent loop.
     */
    fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        currentAgent = null
        _uiState.value = _uiState.value.copy(isAgentRunning = false)
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ── Dev Tool Methods ──

    /**
     * Update the dev tool configuration.
     */
    fun updateDevToolConfig(config: DevToolConfig) {
        _uiState.value = _uiState.value.copy(devToolConfig = config)
    }

    /**
     * Toggle the dev tool panel.
     */
    fun toggleDevTools() {
        _uiState.value = _uiState.value.copy(
            showDevTools = !_uiState.value.showDevTools,
            devToolResult = null
        )
    }

    /**
     * Execute the currently configured dev tool.
     */
    fun executeDevTool() {
        val config = _uiState.value.devToolConfig
        if (_uiState.value.isDevToolRunning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDevToolRunning = true, devToolResult = null)

            val session = activeSession.first() ?: run {
                _uiState.value = _uiState.value.copy(
                    isDevToolRunning = false,
                    error = "No active session"
                )
                return@launch
            }
            val apiAccount = activeApiAccount.first() ?: run {
                _uiState.value = _uiState.value.copy(
                    isDevToolRunning = false,
                    error = "No API account configured"
                )
                return@launch
            }

            val repoDir = resolveRepoDir(session) ?: run {
                _uiState.value = _uiState.value.copy(
                    isDevToolRunning = false,
                    error = "No repository configured"
                )
                return@launch
            }

            val executor = DevToolExecutor(repoDir, apiAccount)

            try {
                val result = when (config.selectedTool) {
                    com.chatroom.app.ui.components.DevToolType.CODE_SEARCH -> {
                        executor.searchCode(config.searchQuery, config.searchGlob.ifBlank { null })
                    }
                    com.chatroom.app.ui.components.DevToolType.CODE_EXPLAIN -> {
                        executor.explainCode(
                            code = config.codeInput,
                            fileName = config.fileName,
                            language = config.fileName.substringAfterLast('.', "")
                        )
                    }
                    com.chatroom.app.ui.components.DevToolType.PERF_ANALYSIS -> {
                        executor.analyzePerformance(config.fileName, config.codeInput)
                    }
                    com.chatroom.app.ui.components.DevToolType.SECURITY_AUDIT -> {
                        executor.auditSecurity(config.fileName, config.codeInput)
                    }
                    com.chatroom.app.ui.components.DevToolType.API_DEBUG -> {
                        executor.debugApi(
                            requestMethod = config.apiMethod,
                            requestUrl = config.apiUrl,
                            requestBody = config.apiRequestBody,
                            responseStatus = config.apiResponseStatus.toIntOrNull() ?: 0,
                            responseBody = config.apiResponseBody,
                            errorMessage = config.apiError
                        )
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isDevToolRunning = false,
                    devToolResult = result.details,
                    error = if (!result.success) result.error else null
                )

                // Also log the result in the session
                if (result.success) {
                    val toolMsg = ChatMessage(
                        role = "assistant",
                        content = "## ${config.selectedTool.label} Result\n\n${result.details}",
                        isAgentMessage = true,
                        agentRound = 0
                    )
                    sessionRepo.addMessageToSession(session.id, toolMsg)
                }
            } catch (e: Exception) {
                AppLogger.e("CodexVM", "Dev tool failed", e)
                _uiState.value = _uiState.value.copy(
                    isDevToolRunning = false,
                    error = "Tool execution failed: ${e.message}"
                )
            }
        }
    }

    private suspend fun resolveRepoDir(session: Session): File? {
        return if (session.repoDir.isNotBlank()) {
            File(session.repoDir)
        } else if (session.localPath.isNotBlank() && session.repoName.isNotBlank()) {
            File(session.localPath, session.repoName)
        } else if (session.repoName.isNotBlank()) {
            getApplication<Application>().filesDir.resolve("workspace").resolve(session.repoName)
        } else {
            null
        }
    }

    private fun buildAgentSummary(events: List<AgentEvent>, steps: List<ExecutionStep>): String {
        val sb = StringBuilder()
        val reads = events.count { it is AgentEvent.ReadingFile }
        val writes = events.count { it is AgentEvent.WritingFile }
        val commands = events.count { it is AgentEvent.CommandComplete }
        val changes = events.filterIsInstance<AgentEvent.FileChanged>()

        sb.appendLine("## Agent Execution Complete")
        sb.appendLine()
        sb.appendLine("**Summary**: Read $reads files, wrote/modified $writes files, executed $commands commands.")
        sb.appendLine()

        if (changes.isNotEmpty()) {
            sb.appendLine("### Files Changed")
            changes.forEach { change ->
                sb.appendLine("- ${change.path} (+${change.additions} -${change.deletions})")
            }
            sb.appendLine()
        }

        if (steps.isNotEmpty()) {
            sb.appendLine("### Execution Steps")
            steps.forEach { step ->
                val icon = if (step.success) "✓" else "✗"
                sb.appendLine("$icon **${step.actionType}**: ${step.description}")
                if (step.exitCode != null && step.exitCode != 0) {
                    sb.appendLine("  Exit code: ${step.exitCode}")
                }
            }
        }

        return sb.toString().trim()
    }
}
