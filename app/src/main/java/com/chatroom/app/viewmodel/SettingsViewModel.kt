package com.chatroom.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.repository.SettingsRepository
import com.chatroom.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val language: StateFlow<String> = repository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.LANGUAGE_ZH_CN)

    val searchEndpoint: StateFlow<String> = repository.searchEndpoint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_SEARCH_ENDPOINT)

    val searchApiKey: StateFlow<String> = repository.searchApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setSearchEndpoint(endpoint: String) {
        viewModelScope.launch { repository.setSearchEndpoint(endpoint) }
    }

    fun setSearchApiKey(apiKey: String) {
        viewModelScope.launch { repository.setSearchApiKey(apiKey) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            repository.setLanguage(lang)
            // Write to SharedPreferences for sync applyLocale() in MainActivity
            val prefs = getApplication<Application>()
                .getSharedPreferences("settings_preferences", 0)
            prefs.edit().putString("language", lang).apply()
            // Restart activity to apply locale
            val intent = getApplication<Application>()
                .packageManager
                .getLaunchIntentForPackage(getApplication<Application>().packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}
