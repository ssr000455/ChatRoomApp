package com.chatroom.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatroom.app.data.model.Identity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.identityDataStore by preferencesDataStore(name = "identities")

class IdentityRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val LIST_KEY = stringPreferencesKey("identity_list")
        private val ACTIVE_ID_KEY = stringPreferencesKey("active_identity_id")
        private const val MAX_IDENTITIES = 10
    }

    val identities: Flow<List<Identity>> = context.identityDataStore.data.map { prefs ->
        val json = prefs[LIST_KEY] ?: "[]"
        val type = object : TypeToken<List<Identity>>() {}.type
        gson.fromJson<List<Identity>>(json, type).map { it.sanitize() }
    }

    val activeIdentity: Flow<Identity?> = context.identityDataStore.data.map { prefs ->
        val activeId = prefs[ACTIVE_ID_KEY] ?: return@map null
        val json = prefs[LIST_KEY] ?: "[]"
        val type = object : TypeToken<List<Identity>>() {}.type
        val list: List<Identity> = gson.fromJson(json, type).map { it.sanitize() }
        list.find { it.id == activeId }
    }

    suspend fun addIdentity(identity: Identity): Result<Unit> = runCatching {
        val current = identities.first().toMutableList()
        if (current.size >= MAX_IDENTITIES) {
            throw IllegalStateException("Maximum $MAX_IDENTITIES identities allowed")
        }
        current.add(identity)
        context.identityDataStore.edit { prefs ->
            prefs[LIST_KEY] = gson.toJson(current)
        }
    }

    suspend fun updateIdentity(identity: Identity) {
        val current = identities.first().toMutableList()
        val index = current.indexOfFirst { it.id == identity.id }
        if (index >= 0) {
            current[index] = identity
            context.identityDataStore.edit { prefs ->
                prefs[LIST_KEY] = gson.toJson(current)
            }
        }
    }

    suspend fun deleteIdentity(identityId: String) {
        val current = identities.first().toMutableList()
        current.removeAll { it.id == identityId }
        context.identityDataStore.edit { prefs ->
            prefs[LIST_KEY] = gson.toJson(current)
            if (prefs[ACTIVE_ID_KEY] == identityId) {
                prefs.remove(ACTIVE_ID_KEY)
            }
        }
    }

    suspend fun setActive(identityId: String) {
        context.identityDataStore.edit { prefs ->
            prefs[ACTIVE_ID_KEY] = identityId
        }
    }

    suspend fun canAddMore(): Boolean {
        return identities.first().size < MAX_IDENTITIES
    }

    suspend fun replaceAll(identities: List<Identity>, activeId: String?) {
        context.identityDataStore.edit { prefs ->
            prefs[LIST_KEY] = gson.toJson(identities)
            if (activeId != null && identities.any { it.id == activeId }) {
                prefs[ACTIVE_ID_KEY] = activeId
            } else {
                prefs.remove(ACTIVE_ID_KEY)
            }
        }
    }
}
