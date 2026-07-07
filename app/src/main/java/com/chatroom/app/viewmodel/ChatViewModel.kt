package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.api.ChatApiService
import com.chatroom.app.data.api.WebSearchService
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ChatRequest
import com.chatroom.app.data.model.Identity
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.repository.ApiAccountRepository
import com.chatroom.app.data.repository.IdentityRepository
import com.chatroom.app.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val inputText: String = "",
    val isSending: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val deepThinkingEnabled: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionRepo = SessionRepository(application)
    private val apiAccountRepo = ApiAccountRepository(application)
    private val identityRepo = IdentityRepository(application)
    private val webSearchService = WebSearchService.createDefault()

    val sessions: StateFlow<List<Session>> = sessionRepo.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<Session?> = sessionRepo.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeApiAccount: StateFlow<ApiAccount?> = apiAccountRepo.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeIdentity: StateFlow<Identity?> = identityRepo.activeIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

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

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        val session = activeSession.value ?: run {
            viewModelScope.launch { createNewSession() }
            return
        }
        val apiAccount = activeApiAccount.value ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(inputText = "", isSending = true, error = null)

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
            messages.addAll(historyMessages)
            messages.add(userMessage)

            // Web search: use DuckDuckGo by default, no API key needed
            if (_uiState.value.webSearchEnabled) {
                try {
                    val searchResults = webSearchService.search(
                        WebSearchService.DEFAULT_ENDPOINT, null, text
                    )
                    val searchContext = ChatMessage(
                        role = "system",
                        content = "Web search results for \"$text\":\n$searchResults"
                    )
                    messages.add(messages.size - 1, searchContext)
                } catch (e: Exception) {
                    // If search fails, proceed without results
                    val failMsg = ChatMessage(
                        role = "system",
                        content = "Web search failed: ${e.message}. Proceed without search results."
                    )
                    messages.add(messages.size - 1, failMsg)
                }
            }

            try {
                val api = ChatApiService.create(apiAccount.apiBaseUrl)

                // Use reasoning model when deep thinking is enabled
                val model = if (session.deepThinkingEnabled) "o1-mini" else "gpt-4o"
                val reasoningEffort = if (session.deepThinkingEnabled) "high" else null

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

                if (response.isSuccessful) {
                    val aiMessage = response.body()?.choices?.firstOrNull()?.message
                        ?: ChatMessage(role = "assistant", content = "No response generated.")

                    sessionRepo.addMessageToSession(session.id, userMessage)
                    sessionRepo.addMessageToSession(session.id, aiMessage)

                    // Update session title from first message
                    if (session.messages.isEmpty()) {
                        val title = if (text.length > 30) text.take(30) + "…" else text
                        sessionRepo.updateSession(session.copy(title = title))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        error = "API Error: ${response.code()} - $errorBody"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Network Error: ${e.message ?: "Connection failed"}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun regenerateLastResponse() {
        val session = activeSession.value ?: return
        val apiAccount = activeApiAccount.value ?: return
        val messages = session.messages
        if (messages.size < 2) return

        // Find the last user message
        val lastUserMsg = messages.lastOrNull { it.role == "user" } ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

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
                val model = if (session.deepThinkingEnabled) "o1-mini" else "gpt-4o"
                val reasoningEffort = if (session.deepThinkingEnabled) "high" else null

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

                    // Remove last AI response and add new one
                    val lastAiIdx = session.messages.indexOfLast { it.role == "assistant" }
                    if (lastAiIdx >= 0) {
                        sessionRepo.removeMessageAtIndex(session.id, lastAiIdx)
                    }
                    sessionRepo.addMessageToSession(session.id, aiMessage)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        error = "API Error: ${response.code()} - $errorBody"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Network Error: ${e.message ?: "Connection failed"}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
