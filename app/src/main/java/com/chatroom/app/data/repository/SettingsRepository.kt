package com.chatroom.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatroom.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("language")

        const val LANGUAGE_ZH_CN = "zh-CN"
        const val LANGUAGE_ZH_TW = "zh-TW"
        const val LANGUAGE_EN = "en"
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            1 -> ThemeMode.LIGHT
            2 -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val language: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: LANGUAGE_ZH_CN
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = when (mode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            }
        }
    }

    suspend fun setLanguage(lang: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = lang
        }
    }

    suspend fun getCurrentLanguage(): String {
        return context.settingsDataStore.data.first()[LANGUAGE_KEY] ?: LANGUAGE_ZH_CN
    }
}
