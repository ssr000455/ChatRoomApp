package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.api.ChatApiService
import com.chatroom.app.data.api.WebSearchService
import com.chatroom.app.data.api.searchWithSources
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.ChangeStatus
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ChatRequest
import com.chatroom.app.data.model.FileChange
import com.chatroom.app.data.model.Identity
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.model.SessionMode
import com.chatroom.app.data.model.SessionType
import com.chatroom.app.data.repository.ApiAccountRepository
import com.chatroom.app.data.repository.IdentityRepository
import com.chatroom.app.data.repository.SessionRepository
import com.chatroom.app.data.repository.UserProfileRepository
import com.chatroom.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val inputText: String = "",
    val isSending: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val deepThinkingEnabled: Boolean = false,
    val error: String? = null,
    val thinkingElapsed: Int = 0,
    val searchSources: List<String> = emptyList(),
    val showChatSettings: Boolean = false,
    val editableSystemPrompt: String = "",
    val selectedSessionIds: Set<String> = emptySet(),
    val shareAccountInfo: Boolean = false,
    val referencedContent: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionRepo = SessionRepository(application)
    private val apiAccountRepo = ApiAccountRepository(application)
    private val identityRepo = IdentityRepository(application)
    private val userProfileRepo = UserProfileRepository(application)
    private val webSearchService = WebSearchService.createDefault()
    private var timerJob: Job? = null

    val sessions: StateFlow<List<Session>> = sessionRepo.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<Session?> = sessionRepo.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeApiAccount: StateFlow<ApiAccount?> = apiAccountRepo.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeIdentity: StateFlow<Identity?> = identityRepo.activeIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val apiAccounts: StateFlow<List<ApiAccount>> = apiAccountRepo.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Track the current send job so we can cancel it
    private var sendJob: Job? = null

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun toggleWebSearch() {
        _uiState.value = _uiState.value.copy(webSearchEnabled = !_uiState.value.webSearchEnabled)
    }

    fun toggleDeepThinking() {
        _uiState.value = _uiState.value.copy(deepThinkingEnabled = !_uiState.value.deepThinkingEnabled)
    }

    fun createNewSession() {
        viewModelScope.launch {
            val apiAccount = activeApiAccount.value ?: return@launch
            val identity = activeIdentity.value
            val systemPrompt = identity?.toSystemPrompt() ?: "You are a helpful assistant."

            val session = Session(
                apiAccountId = apiAccount.id,
                identityId = identity?.id ?: "",
                systemPrompt = systemPrompt,
                webSearchEnabled = _uiState.value.webSearchEnabled,
                deepThinkingEnabled = _uiState.value.deepThinkingEnabled
            )
            sessionRepo.createSession(session)
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepo.setActiveSession(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepo.deleteSession(sessionId)
        }
    }

    fun createCodingAssistantSession(
        apiAccountId: String,
        systemPrompt: String = "You are an expert coding assistant.",
        repoUrl: String = "",
        repoOwner: String = "",
        repoName: String = ""
    ) {
        viewModelScope.launch {
            val session = Session(
                title = if (repoName.isNotBlank()) repoName else "Coding Assistant",
                type = SessionType.CODING_ASSISTANT,
                mode = SessionMode.CHAT,
                apiAccountId = apiAccountId,
                systemPrompt = systemPrompt,
                repoUrl = repoUrl,
                repoOwner = repoOwner,
                repoName = repoName,
                repoBranch = "main"
            )
            sessionRepo.createSession(session)
        }
    }

    fun setSessionMode(sessionId: String, mode: SessionMode) {
        viewModelScope.launch {
            val session = sessionRepo.activeSession.first() ?: return@launch
            if (session.id != sessionId) {
                sessionRepo.setActiveSession(sessionId)
            }
            val updated = sessionRepo.activeSession.first() ?: return@launch
            sessionRepo.updateSession(updated.copy(mode = mode))
        }
    }

    // ── Change Review Methods ──

    fun acceptAllChanges() {
        viewModelScope.launch {
            val session = sessionRepo.activeSession.first() ?: return@launch
            val updated = session.copy(
                pendingChanges = session.pendingChanges.map {
                    if (it.status == ChangeStatus.PENDING) it.copy(status = ChangeStatus.ACCEPTED)
                    else it
                }
            )
            sessionRepo.updateSession(updated)
        }
    }

    fun rejectAllChanges() {
        viewModelScope.launch {
            val session = sessionRepo.activeSession.first() ?: return@launch
            val updated = session.copy(
                pendingChanges = session.pendingChanges.map {
                    if (it.status == ChangeStatus.PENDING) it.copy(status = ChangeStatus.REJECTED)
                    else it
                }
            )
            sessionRepo.updateSession(updated)
        }
    }

    fun acceptChange(changeId: String) {
        viewModelScope.launch {
            val session = sessionRepo.activeSession.first() ?: return@launch
            val updated = session.copy(
                pendingChanges = session.pendingChanges.map {
                    if (it.id == changeId) it.copy(status = ChangeStatus.ACCEPTED) else it
                }
            )
            sessionRepo.updateSession(updated)
        }
    }

    fun rejectChange(changeId: String) {
        viewModelScope.launch {
            val session = sessionRepo.activeSession.first() ?: return@launch
            val updated = session.copy(
                pendingChanges = session.pendingChanges.map {
                    if (it.id == changeId) it.copy(status = ChangeStatus.REJECTED) else it
                }
            )
            sessionRepo.updateSession(updated)
        }
    }

    fun commitChanges(commitMessage: String) {
        viewModelScope.launch {
            val session = sessionRepo.activeSession.first() ?: return@launch
            // TODO: actual git commit via TerminalSession
            AppLogger.d("ChatViewModel", "Commit: $commitMessage")
            // Clear accepted changes after commit
            val remaining = session.pendingChanges.filter {
                it.status != ChangeStatus.ACCEPTED
            }
            sessionRepo.updateSession(session.copy(pendingChanges = remaining))
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        AppLogger.d("ChatVM", "sendMessage: len=${text.length}")

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                inputText = "", isSending = true, error = null,
                thinkingElapsed = 0, searchSources = emptyList(),
                referencedContent = null
            )

            // Start elapsed time counter
            startTimer()

            // Ensure we have an active session (create one if needed)
            var session = activeSession.value
            if (session == null) {
                AppLogger.d("ChatVM", "No active session, creating new one")
                val apiAccount = activeApiAccount.value
                if (apiAccount == null) {
                    AppLogger.e("ChatVM", "No API account configured")
                    _uiState.value = _uiState.value.copy(isSending = false, error = "No API account configured")
                    return@launch
                }
                val identity = activeIdentity.value
                val systemPrompt = identity?.toSystemPrompt() ?: "You are a helpful assistant."
                session = Session(
                    apiAccountId = apiAccount.id,
                    identityId = identity?.id ?: "",
                    systemPrompt = systemPrompt,
                    webSearchEnabled = _uiState.value.webSearchEnabled,
                    deepThinkingEnabled = _uiState.value.deepThinkingEnabled
                )
                sessionRepo.createSession(session)
            }

            val apiAccount = activeApiAccount.value ?: run {
                _uiState.value = _uiState.value.copy(isSending = false, error = "No API account configured")
                return@launch
            }

            val userMessage = ChatMessage(role = "user", content = text)

            // Build message list with system prompt
            val messages = mutableListOf(
                ChatMessage(role = "system", content = session.systemPrompt)
            )

            // Include recent conversation history (within token limit)
            val maxHistoryTokens = 100_000
            var historyTokens = 0
            val historyMessages = session.messages.reversed().takeWhile { msg ->
                historyTokens += msg.content.length / 2
                historyTokens <= maxHistoryTokens
            }.reversed()
            val hadNoMessages = session.messages.isEmpty()
            messages.addAll(historyMessages)
            messages.add(userMessage)

            // Show user message immediately so chat appears before AI starts
            sessionRepo.addMessageToSession(session.id, userMessage)

            // Collect search sources
            val searchSources = mutableListOf<String>()

            // Web search: use DuckDuckGo by default, no API key needed
            if (_uiState.value.webSearchEnabled) {
                try {
                    val (searchText, sources) = webSearchService.searchWithSources(
                        WebSearchService.DEFAULT_ENDPOINT, null, text
                    )
                    searchSources.addAll(sources)
                    // Update UI with sources immediately
                    _uiState.value = _uiState.value.copy(searchSources = sources.toList())

                    val searchContext = ChatMessage(
                        role = "system",
                        content = "Web search results for \"$text\":\n$searchText"
                    )
                    messages.add(messages.size - 1, searchContext)
                } catch (e: Exception) {
                    val failMsg = ChatMessage(
                        role = "system",
                        content = "Web search failed: ${e.message}. Proceed without search results."
                    )
                    messages.add(messages.size - 1, failMsg)
                }
            }

            try {
                val api = ChatApiService.create(apiAccount.apiBaseUrl)

                // Inject context from selected sessions and account info
                messages.addAll(messages.size - 1, buildContextMessages(session.id))

                // Use reasoning model when deep thinking is enabled
                val model = if (session.deepThinkingEnabled) apiAccount.reasoningModel else apiAccount.model
                val reasoningEffort = if (session.deepThinkingEnabled) "high" else null

                AppLogger.d("ChatVM", "API call: model=$model, url=${apiAccount.apiBaseUrl}")
                val request = ChatRequest(
                    model = model,
                    messages = messages,
                    maxTokens = if (session.deepThinkingEnabled) 8192 else 4096,
                    reasoningEffort = reasoningEffort
                )
                val response = api.chatCompletion(
                    authorization = "Bearer ${apiAccount.apiKey}",
                    request = request
                )
                AppLogger.d("ChatVM", "API response: code=${response.code()}")

                if (response.isSuccessful) {
                    val aiMessage = response.body()?.choices?.firstOrNull()?.message
                        ?: ChatMessage(role = "assistant", content = "No response generated.")

                    // Strip reasoning content if deep thinking is not enabled
                    val finalMessage = if (!session.deepThinkingEnabled) {
                        ChatMessage(role = aiMessage.role ?: "assistant", content = aiMessage.content ?: "", attachments = aiMessage.attachments ?: emptyList(), reasoningContent = null)
                    } else {
                        aiMessage
                    }

                    sessionRepo.addMessageToSession(session.id, finalMessage)

                    // Update session title from first exchange
                    if (hadNoMessages) {
                        val title = if (text.length > 30) text.take(30) + "\u2026" else text
                        // Use session from repo (has messages) to avoid overwriting with empty local copy
                        val repoSession = sessionRepo.activeSession.first() ?: session
                        sessionRepo.updateSession(repoSession.copy(title = title))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    AppLogger.e("ChatVM", "API error: ${response.code()} - $errorBody")
                    _uiState.value = _uiState.value.copy(
                        error = "API Error: ${response.code()} - $errorBody"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ChatVM", "Network error", e)
                _uiState.value = _uiState.value.copy(
                    error = "Network Error: ${e.message ?: "Connection failed"}"
                )
            } finally {
                stopTimer()
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun regenerateLastResponse() {
        AppLogger.d("ChatVM", "regenerateLastResponse")
        val session = activeSession.value ?: return
        val apiAccount = activeApiAccount.value ?: return
        val messages = session.messages
        if (messages.size < 2) return

        // Find the last user message
        val lastUserMsg = messages.lastOrNull { it.role == "user" } ?: return

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSending = true, error = null,
                thinkingElapsed = 0, searchSources = emptyList()
            )
            startTimer()

            // Build message list with system prompt
            val msgList = mutableListOf(
                ChatMessage(role = "system", content = session.systemPrompt)
            )

            // Exclude the last AI response, keep everything before it
            val historyEnd = messages.indexOfLast { it.role == "assistant" }
            if (historyEnd > 0) {
                msgList.addAll(messages.subList(0, historyEnd))
            }
            msgList.add(lastUserMsg)

            try {
                val api = ChatApiService.create(apiAccount.apiBaseUrl)

                // Inject context from selected sessions and account info
                msgList.addAll(msgList.size - 1, buildContextMessages(session.id))

                val model = if (session.deepThinkingEnabled) apiAccount.reasoningModel else apiAccount.model
                val reasoningEffort = if (session.deepThinkingEnabled) "high" else null

                AppLogger.d("ChatVM", "Regen API call: model=$model, url=${apiAccount.apiBaseUrl}")
                val request = ChatRequest(
                    model = model,
                    messages = msgList,
                    maxTokens = if (session.deepThinkingEnabled) 8192 else 4096,
                    reasoningEffort = reasoningEffort
                )
                val response = api.chatCompletion(
                    authorization = "Bearer ${apiAccount.apiKey}",
                    request = request
                )

                if (response.isSuccessful) {
                    val aiMessage = response.body()?.choices?.firstOrNull()?.message
                        ?: ChatMessage(role = "assistant", content = "No response generated.")

                    // Strip reasoning content if deep thinking is not enabled
                    val finalMessage = if (!session.deepThinkingEnabled) {
                        ChatMessage(role = aiMessage.role ?: "assistant", content = aiMessage.content ?: "", attachments = aiMessage.attachments ?: emptyList(), reasoningContent = null)
                    } else {
                        aiMessage
                    }

                    // Remove last AI response and add new one
                    val lastAiIdx = session.messages.indexOfLast { it.role == "assistant" }
                    if (lastAiIdx >= 0) {
                        sessionRepo.removeMessageAtIndex(session.id, lastAiIdx)
                    }
                    sessionRepo.addMessageToSession(session.id, finalMessage)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    AppLogger.e("ChatVM", "API error: ${response.code()} - $errorBody")
                    _uiState.value = _uiState.value.copy(
                        error = "API Error: ${response.code()} - $errorBody"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ChatVM", "Network error", e)
                _uiState.value = _uiState.value.copy(
                    error = "Network Error: ${e.message ?: "Connection failed"}"
                )
            } finally {
                stopTimer()
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun toggleChatSettings() {
        val current = _uiState.value
        if (current.showChatSettings) {
            // Close sheet
            _uiState.value = current.copy(showChatSettings = false)
        } else {
            // Open sheet — populate with current session's system prompt
            val prompt = activeSession.value?.systemPrompt ?: ""
            _uiState.value = current.copy(
                showChatSettings = true,
                editableSystemPrompt = prompt
            )
        }
    }

    fun updateSystemPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(editableSystemPrompt = prompt)
    }

    fun saveSystemPrompt() {
        viewModelScope.launch {
            val session = activeSession.value ?: return@launch
            val newPrompt = _uiState.value.editableSystemPrompt
            sessionRepo.updateSession(session.copy(systemPrompt = newPrompt))
        }
    }

    fun toggleSessionSelection(sessionId: String) {
        val current = _uiState.value.selectedSessionIds
        _uiState.value = _uiState.value.copy(
            selectedSessionIds = if (sessionId in current)
                current - sessionId
            else
                current + sessionId
        )
    }

    fun toggleAccountInfoSharing() {
        _uiState.value = _uiState.value.copy(
            shareAccountInfo = !_uiState.value.shareAccountInfo
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setReferencedContent(content: String) {
        _uiState.value = _uiState.value.copy(referencedContent = content)
    }

    fun clearReferencedContent() {
        _uiState.value = _uiState.value.copy(referencedContent = null)
    }

    fun stopSending() {
        sendJob?.cancel()
        sendJob = null
        stopTimer()
        _uiState.value = _uiState.value.copy(isSending = false)
    }

    /**
     * Recall the last user message: remove it (and its AI response) from the session,
     * and put the text back into the input box.
     */
    fun recallLastMessage() {
        viewModelScope.launch {
            val session = activeSession.value ?: return@launch
            val msgs = session.messages
            if (msgs.isEmpty()) return@launch

            val lastUserIdx = msgs.indexOfLast { it.role == "user" }
            if (lastUserIdx < 0) return@launch

            val recalledText = msgs[lastUserIdx].content

            // Remove messages from lastUserIdx to end (user msg + any following AI responses)
            val removeIndices = (lastUserIdx until msgs.size).toList().reversed()
            for (idx in removeIndices) {
                sessionRepo.removeMessageAtIndex(session.id, idx)
            }

            // Put the text back into the input box
            _uiState.value = _uiState.value.copy(inputText = recalledText)
            clearReferencedContent()
        }
    }

    /**
     * Delete a specific AI message from the session by its ID.
     */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val session = activeSession.value ?: return@launch
            val idx = session.messages.indexOfFirst { it.id == messageId }
            if (idx >= 0) {
                sessionRepo.removeMessageAtIndex(session.id, idx)
            }
        }
    }

    /**
     * Rewrite an AI message: remove it and all messages after it,
     * then regenerate a new AI response from the conversation up to that point.
     */
    fun rewriteMessage(messageId: String) {
        val session = activeSession.value ?: return
        val apiAccount = activeApiAccount.value ?: return
        val msgs = session.messages
        val aiIdx = msgs.indexOfFirst { it.id == messageId }
        if (aiIdx < 0) return

        // Find the user message that preceded this AI message
        val precedingUserIdx = msgs.subList(0, aiIdx).indexOfLast { it.role == "user" }
        if (precedingUserIdx < 0) return
        val userMsg = msgs[precedingUserIdx]

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSending = true, error = null,
                thinkingElapsed = 0, searchSources = emptyList()
            )
            startTimer()

            // Remove this AI message and everything after it
            val removeIndices = (aiIdx until msgs.size).toList().reversed()
            for (idx in removeIndices) {
                sessionRepo.removeMessageAtIndex(session.id, idx)
            }
            // Re-fetch session to get updated messages
            val updatedSession = activeSession.first() ?: return@launch

            // Build message list up to (and including) the user message
            val msgList = mutableListOf(
                ChatMessage(role = "system", content = updatedSession.systemPrompt)
            )
            val upToUserIdx = updatedSession.messages.indexOfFirst { it.id == userMsg.id }
            if (upToUserIdx > 0) {
                msgList.addAll(updatedSession.messages.subList(0, upToUserIdx + 1))
            } else {
                msgList.add(userMsg)
            }

            try {
                val api = ChatApiService.create(apiAccount.apiBaseUrl)
                msgList.addAll(msgList.size - 1, buildContextMessages(updatedSession.id))

                val model = if (updatedSession.deepThinkingEnabled) apiAccount.reasoningModel else apiAccount.model
                val reasoningEffort = if (updatedSession.deepThinkingEnabled) "high" else null

                AppLogger.d("ChatVM", "Rewrite API call: model=$model")
                val request = ChatRequest(
                    model = model,
                    messages = msgList,
                    maxTokens = if (updatedSession.deepThinkingEnabled) 8192 else 4096,
                    reasoningEffort = reasoningEffort
                )
                val response = api.chatCompletion(
                    authorization = "Bearer ${apiAccount.apiKey}",
                    request = request
                )

                if (response.isSuccessful) {
                    val aiMessage = response.body()?.choices?.firstOrNull()?.message
                        ?: ChatMessage(role = "assistant", content = "No response generated.")

                    val finalMessage = if (!updatedSession.deepThinkingEnabled) {
                        ChatMessage(role = aiMessage.role ?: "assistant", content = aiMessage.content ?: "", attachments = aiMessage.attachments ?: emptyList(), reasoningContent = null)
                    } else {
                        aiMessage
                    }
                    sessionRepo.addMessageToSession(updatedSession.id, finalMessage)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    AppLogger.e("ChatVM", "Rewrite API error: ${response.code()} - $errorBody")
                    _uiState.value = _uiState.value.copy(
                        error = "API Error: ${response.code()} - $errorBody"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ChatVM", "Rewrite network error", e)
                _uiState.value = _uiState.value.copy(
                    error = "Network Error: ${e.message ?: "Connection failed"}"
                )
            } finally {
                stopTimer()
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    /**
     * Build context injection messages from user profile, selected sessions and account info.
     * These are injected as system messages before the API call.
     */
    private suspend fun buildContextMessages(excludeSessionId: String): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        val selectedIds = _uiState.value.selectedSessionIds

        // Inject user profile info so the AI knows who it's talking to
        val profile = userProfileRepo.activeProfile.first()
        if (profile != null) {
            result.add(ChatMessage(
                role = "system",
                content = "Current user information:\n${profile.toContextString()}"
            ))
        }

        // Inject referenced/quote content if the user is replying to a specific message
        val referenced = _uiState.value.referencedContent
        if (!referenced.isNullOrBlank()) {
            result.add(ChatMessage(
                role = "system",
                content = "The user is referring to the following message:\n\"$referenced\""
            ))
        }

        // Inject recent messages from selected sessions
        if (selectedIds.isNotEmpty()) {
            val allSessions = sessions.value
            val selectedSessions = allSessions.filter {
                it.id in selectedIds && it.id != excludeSessionId && it.messages.isNotEmpty()
            }
            if (selectedSessions.isNotEmpty()) {
                val contextParts = mutableListOf<String>()
                for (s in selectedSessions) {
                    contextParts.add("--- ${s.title} ---")
                    val recent = s.messages.takeLast(6)
                    for (m in recent) {
                        val preview = m.content.take(200)
                        contextParts.add("${m.role}: $preview")
                    }
                }
                result.add(ChatMessage(
                    role = "system",
                    content = "Reference from other conversations:\n${contextParts.joinToString("\n")}"
                ))
            }
        }

        // Inject account info if enabled
        if (_uiState.value.shareAccountInfo) {
            val account = activeApiAccount.value
            if (account != null) {
                result.add(ChatMessage(
                    role = "system",
                    content = "Current AI configuration:\n- Account: ${account.name}\n- Model: ${account.model}\n- API: ${account.apiBaseUrl}"
                ))
            }
        }

        return result
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var elapsed = 0
            while (isActive) {
                delay(1000)
                elapsed++
                _uiState.value = _uiState.value.copy(thinkingElapsed = elapsed)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
