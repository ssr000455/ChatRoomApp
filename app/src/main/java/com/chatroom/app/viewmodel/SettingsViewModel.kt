package com.chatroom.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.repository.SettingsRepository
import com.chatroom.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    private val _recreateEvent = MutableSharedFlow<Unit>()
    val recreateEvent: SharedFlow<Unit> = _recreateEvent.asSharedFlow()

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
            // Write to SharedPreferences synchronously for MainActivity.attachBaseContext()
            val prefs = getApplication<Application>()
                .getSharedPreferences("settings_preferences", Context.MODE_PRIVATE)
            prefs.edit().putString("language", lang).commit()
            // Signal MainActivity to recreate with the new locale
            _recreateEvent.emit(Unit)
        }
    }
}
