package com.chatroom.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.Session
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "sessions")

class SessionRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val SESSIONS_KEY = stringPreferencesKey("sessions_list")
        private val ACTIVE_SESSION_ID_KEY = stringPreferencesKey("active_session_id")
        private val MAX_SESSIONS = 25
        private const val MAX_TOKENS = 128_000
    }

    // In-memory cache to avoid repeated deserialization
    @Volatile
    private var cachedSessions: List<Session>? = null

    val sessions: Flow<List<Session>> = context.sessionDataStore.data.map { prefs ->
        val json = prefs[SESSIONS_KEY] ?: "[]"
        val type = object : TypeToken<List<Session>>() {}.type
        gson.fromJson<List<Session>>(json, type).map { it.sanitize() }.also { cachedSessions = it }
    }

    val activeSessionId: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[ACTIVE_SESSION_ID_KEY]
    }

    val activeSession: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val sessionId = prefs[ACTIVE_SESSION_ID_KEY] ?: return@map null
        val list = getCachedSessions(prefs[SESSIONS_KEY])
        list.find { it.id == sessionId }?.sanitize()
    }

    @Synchronized
    private fun getCachedSessions(json: String? = null): List<Session> {
        if (cachedSessions != null) return cachedSessions ?: emptyList()
        val raw = json ?: return emptyList()
        val type = object : TypeToken<List<Session>>() {}.type
        return gson.fromJson<List<Session>>(raw, type).map { it.sanitize() }.also { cachedSessions = it }
    }

    suspend fun createSession(session: Session): Result<Unit> = runCatching {
        val current = getCachedSessions().toMutableList()
        if (current.size >= MAX_SESSIONS) {
            throw IllegalStateException("Maximum $MAX_SESSIONS sessions allowed")
        }
        current.add(0, session)
        cachedSessions = current
        context.sessionDataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = gson.toJson(current)
            prefs[ACTIVE_SESSION_ID_KEY] = session.id
        }
    }

    suspend fun updateSession(session: Session) {
        val current = getCachedSessions().toMutableList()
        val index = current.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            current[index] = session.copy(updatedAt = System.currentTimeMillis())
            cachedSessions = current
            context.sessionDataStore.edit { prefs ->
                prefs[SESSIONS_KEY] = gson.toJson(current)
            }
        }
    }

    suspend fun deleteSession(sessionId: String) {
        val current = getCachedSessions().toMutableList()
        current.removeAll { it.id == sessionId }
        cachedSessions = current
        val activeId = context.sessionDataStore.data.first()[ACTIVE_SESSION_ID_KEY]
        context.sessionDataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = gson.toJson(current)
            if (activeId == sessionId) {
                prefs.remove(ACTIVE_SESSION_ID_KEY)
            }
        }
    }

    suspend fun setActiveSession(sessionId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[ACTIVE_SESSION_ID_KEY] = sessionId
        }
    }

    suspend fun addMessageToSession(sessionId: String, message: ChatMessage): Result<Unit> = runCatching {
        val current = getCachedSessions().toMutableList()
        val index = current.indexOfFirst { it.id == sessionId }
        if (index < 0) throw IllegalStateException("Session not found")

        val session = current[index]
        val newMessages = session.messages + message
        val totalTokens = newMessages.sumOf { it.content.length / 2 }

        if (totalTokens > MAX_TOKENS) {
            throw IllegalStateException("Session token limit ($MAX_TOKENS) exceeded")
        }

        current[index] = session.copy(
            messages = newMessages,
            updatedAt = System.currentTimeMillis()
        )
        cachedSessions = current
        context.sessionDataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeMessageAtIndex(sessionId: String, index: Int): Result<Unit> = runCatching {
        val current = getCachedSessions().toMutableList()
        val sIdx = current.indexOfFirst { it.id == sessionId }
        if (sIdx < 0) throw IllegalStateException("Session not found")

        val session = current[sIdx]
        val newMessages = session.messages.toMutableList()
        if (index < 0 || index >= newMessages.size) throw IndexOutOfBoundsException("Message index $index out of bounds")
        newMessages.removeAt(index)

        current[sIdx] = session.copy(
            messages = newMessages,
            updatedAt = System.currentTimeMillis()
        )
        cachedSessions = current
        context.sessionDataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = gson.toJson(current)
        }
    }

    suspend fun canCreateSession(): Boolean = getCachedSessions().size < MAX_SESSIONS

    suspend fun replaceAll(sessions: List<Session>, activeId: String?) {
        val sorted = sessions.sortedByDescending { it.updatedAt }.map { it.sanitize() }
        cachedSessions = sorted
        context.sessionDataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = gson.toJson(sorted)
            if (activeId != null && sorted.any { it.id == activeId }) {
                prefs[ACTIVE_SESSION_ID_KEY] = activeId
            } else {
                prefs.remove(ACTIVE_SESSION_ID_KEY)
            }
        }
    }
}
