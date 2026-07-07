package com.chatroom.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatroom.app.data.model.Account
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "accounts")

class AccountRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val ACCOUNTS_KEY = stringPreferencesKey("accounts_list")
        private val ACTIVE_ACCOUNT_ID_KEY = stringPreferencesKey("active_account_id")
        private val MAX_ACCOUNTS = 10
    }

    val accounts: Flow<List<Account>> = context.dataStore.data.map { prefs ->
        val json = prefs[ACCOUNTS_KEY] ?: "[]"
        val type = object : TypeToken<List<Account>>() {}.type
        gson.fromJson(json, type)
    }

    val activeAccount: Flow<Account?> = context.dataStore.data.map { prefs ->
        val activeId = prefs[ACTIVE_ACCOUNT_ID_KEY] ?: return@map null
        val json = prefs[ACCOUNTS_KEY] ?: "[]"
        val type = object : TypeToken<List<Account>>() {}.type
        val list: List<Account> = gson.fromJson(json, type)
        list.find { it.id == activeId }
    }

    suspend fun addAccount(account: Account): Result<Unit> = runCatching {
        val current = accounts.first().toMutableList()
        if (current.size >= MAX_ACCOUNTS) {
            throw IllegalStateException("Maximum $MAX_ACCOUNTS accounts allowed")
        }
        current.add(account)
        context.dataStore.edit { prefs ->
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun updateAccount(account: Account) {
        val current = accounts.first().toMutableList()
        val index = current.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            current[index] = account
            context.dataStore.edit { prefs ->
                prefs[ACCOUNTS_KEY] = gson.toJson(current)
            }
        }
    }

    suspend fun deleteAccount(accountId: String) {
        val current = accounts.first().toMutableList()
        current.removeAll { it.id == accountId }
        context.dataStore.edit { prefs ->
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
            val activeId = prefs[ACTIVE_ACCOUNT_ID_KEY]
            if (activeId == accountId) {
                prefs.remove(ACTIVE_ACCOUNT_ID_KEY)
            }
        }
    }

    suspend fun setActiveAccount(accountId: String) {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_ACCOUNT_ID_KEY] = accountId
        }
    }

    suspend fun canAddMoreAccounts(): Boolean {
        return accounts.first().size < MAX_ACCOUNTS
    }
}
