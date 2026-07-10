package com.chatroom.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatroom.app.data.model.UserProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore by preferencesDataStore(name = "user_profiles")

class UserProfileRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val LIST_KEY = stringPreferencesKey("profile_list")
        private val ACTIVE_ID_KEY = stringPreferencesKey("active_profile_id")
        private const val MAX_PROFILES = 5
    }

    val profiles: Flow<List<UserProfile>> = context.userProfileDataStore.data.map { prefs ->
        val json = prefs[LIST_KEY] ?: "[]"
        val type = object : TypeToken<List<UserProfile>>() {}.type
        gson.fromJson<List<UserProfile>>(json, type).map { it.sanitize() }
    }

    val activeProfile: Flow<UserProfile?> = context.userProfileDataStore.data.map { prefs ->
        val activeId = prefs[ACTIVE_ID_KEY] ?: return@map null
        val json = prefs[LIST_KEY] ?: "[]"
        val type = object : TypeToken<List<UserProfile>>() {}.type
        val list: List<UserProfile> = gson.fromJson<List<UserProfile>>(json, type).map { it.sanitize() }
        list.find { it.id == activeId }
    }

    val isLoggedIn: Flow<Boolean> = context.userProfileDataStore.data.map { prefs ->
        prefs[ACTIVE_ID_KEY] != null
    }

    suspend fun addProfile(profile: UserProfile): Result<Unit> = runCatching {
        val current = profiles.first().toMutableList()
        if (current.size >= MAX_PROFILES) {
            throw IllegalStateException("Maximum $MAX_PROFILES profiles allowed")
        }
        current.add(profile)
        context.userProfileDataStore.edit { prefs ->
            prefs[LIST_KEY] = gson.toJson(current)
            // Auto-activate first profile or the newly added one if none active
            if (prefs[ACTIVE_ID_KEY] == null) {
                prefs[ACTIVE_ID_KEY] = profile.id
            }
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        val current = profiles.first().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
            context.userProfileDataStore.edit { prefs ->
                prefs[LIST_KEY] = gson.toJson(current)
            }
        }
    }

    suspend fun deleteProfile(profileId: String) {
        val current = profiles.first().toMutableList()
        current.removeAll { it.id == profileId }
        context.userProfileDataStore.edit { prefs ->
            prefs[LIST_KEY] = gson.toJson(current)
            if (prefs[ACTIVE_ID_KEY] == profileId) {
                // Activate first remaining profile if available
                val nextActive = current.firstOrNull()
                if (nextActive != null) {
                    prefs[ACTIVE_ID_KEY] = nextActive.id
                } else {
                    prefs.remove(ACTIVE_ID_KEY)
                }
            }
        }
    }

    suspend fun setActive(profileId: String) {
        context.userProfileDataStore.edit { prefs ->
            prefs[ACTIVE_ID_KEY] = profileId
        }
    }

    suspend fun canAddMore(): Boolean {
        return profiles.first().size < MAX_PROFILES
    }

    suspend fun replaceAll(profiles: List<UserProfile>, activeId: String?) {
        context.userProfileDataStore.edit { prefs ->
            prefs[LIST_KEY] = gson.toJson(profiles)
            if (activeId != null && profiles.any { it.id == activeId }) {
                prefs[ACTIVE_ID_KEY] = activeId
            } else if (profiles.isNotEmpty()) {
                prefs[ACTIVE_ID_KEY] = profiles.first().id
            } else {
                prefs.remove(ACTIVE_ID_KEY)
            }
        }
    }
}
