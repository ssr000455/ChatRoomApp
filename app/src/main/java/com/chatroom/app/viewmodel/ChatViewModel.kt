package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.api.ChatApiService
import com.chatroom.app.data.model.Account
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ChatRequest
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.repository.AccountRepository
import com.chatroom.app.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val inputText: String = "",
    val isSending: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionRepo = SessionRepository(application)
    private val accountRepo = AccountRepository(application)

    val sessions: StateFlow<List<Session>> = sessionRepo.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<Session?> = sessionRepo.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeAccount: StateFlow<Account?> = accountRepo.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun toggleWebSearch() {
        _uiState.value = _uiState.value.copy(webSearchEnabled = !_uiState.value.webSearchEnabled)
    }

    fun createNewSession() {
        viewModelScope.launch {
            val account = activeAccount.value ?: return@launch
            val session = Session(
                accountId = account.id,
                systemPrompt = account.systemPrompt
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
        val account = activeAccount.value ?: return

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

            try {
                val api = ChatApiService.create(account.apiBaseUrl)
                val request = ChatRequest(
                    messages = messages,
                    maxTokens = 4096
                )
                val response = api.chatCompletion(
                    authorization = "Bearer ${account.apiKey}",
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
