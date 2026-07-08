package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.model.UserProfile
import com.chatroom.app.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserProfileRepository(application)

    val profiles: StateFlow<List<UserProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<UserProfile?> = repository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _editingProfile = MutableStateFlow<UserProfile?>(null)
    val editingProfile: StateFlow<UserProfile?> = _editingProfile.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggleForm() {
        _showForm.value = !_showForm.value
        if (!_showForm.value) _editingProfile.value = null
        _error.value = null
    }

    fun startEdit(profile: UserProfile) {
        _editingProfile.value = profile
        _showForm.value = true
    }

    fun addProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.addProfile(profile)
                .onFailure { _error.value = it.message }
                .onSuccess {
                    _showForm.value = false
                    _editingProfile.value = null
                }
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            _showForm.value = false
            _editingProfile.value = null
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
        }
    }

    fun setActive(profileId: String) {
        viewModelScope.launch {
            repository.setActive(profileId)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
