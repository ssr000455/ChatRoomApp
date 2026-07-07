package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.model.Identity
import com.chatroom.app.data.repository.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IdentityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IdentityRepository(application)

    val identities: StateFlow<List<Identity>> = repository.identities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeIdentity: StateFlow<Identity?> = repository.activeIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggleForm() {
        _showForm.value = !_showForm.value
    }

    fun addIdentity(identity: Identity) {
        viewModelScope.launch {
            repository.addIdentity(identity)
                .onFailure { _error.value = it.message }
                .onSuccess { _showForm.value = false }
        }
    }

    fun deleteIdentity(identityId: String) {
        viewModelScope.launch {
            repository.deleteIdentity(identityId)
        }
    }

    fun setActive(identityId: String) {
        viewModelScope.launch {
            repository.setActive(identityId)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
