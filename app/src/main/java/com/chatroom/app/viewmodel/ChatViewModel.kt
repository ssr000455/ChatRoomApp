package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.api.ChatApiService
import com.chatroom.app.data.api.WebSearchService
import com.chatroom.app.data.api.searchWithSources
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ChatRequest
import com.chatroom.app.data.model.Identity
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.repository.ApiAccountRepository
import com.chatroom.app.data.repository.IdentityRepository
import com.chatroom.app.data.repository.SessionRepository
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
    val thinkingElapsed: Int = 0,      // seconds elapsed while waiting for AI
    val searchSources: List<String> = emptyList()  // URLs from web search
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionRepo = SessionRepository(application)
    private val apiAccountRepo = ApiAccountRepository(application)
    private val identityRepo = IdentityRepository(application)
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

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        AppLogger.d("ChatVM", "sendMessage: len=${text.length}")

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                inputText = "", isSending = true, error = null,
                thinkingElapsed = 0, searchSources = emptyList()
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

                    sessionRepo.addMessageToSession(session.id, aiMessage)

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

                    // Remove last AI response and add new one
                    val lastAiIdx = session.messages.indexOfLast { it.role == "assistant" }
                    if (lastAiIdx >= 0) {
                        sessionRepo.removeMessageAtIndex(session.id, lastAiIdx)
                    }
                    sessionRepo.addMessageToSession(session.id, aiMessage)
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun stopSending() {
        sendJob?.cancel()
        sendJob = null
        stopTimer()
        _uiState.value = _uiState.value.copy(isSending = false)
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
