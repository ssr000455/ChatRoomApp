package com.chatroom.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.repository.ApiAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ApiAccountViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ApiAccountRepository(application)

    val accounts: StateFlow<List<ApiAccount>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<ApiAccount?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    fun toggleForm() {
        _showForm.value = !_showForm.value
    }

    fun addAccount(account: ApiAccount) {
        viewModelScope.launch {
            repository.addAccount(account)
            _showForm.value = false
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
        }
    }

    fun setActive(accountId: String) {
        viewModelScope.launch {
            repository.setActive(accountId)
        }
    }
}
