package com.chatroom.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatroom.app.data.model.ApiAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.apiDataStore by preferencesDataStore(name = "api_accounts")

class ApiAccountRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val ACCOUNTS_KEY = stringPreferencesKey("api_accounts_list")
        private val ACTIVE_ID_KEY = stringPreferencesKey("active_api_account_id")
    }

    val accounts: Flow<List<ApiAccount>> = context.apiDataStore.data.map { prefs ->
        val json = prefs[ACCOUNTS_KEY] ?: "[]"
        val type = object : TypeToken<List<ApiAccount>>() {}.type
        gson.fromJson(json, type)
    }

    val activeAccount: Flow<ApiAccount?> = context.apiDataStore.data.map { prefs ->
        val activeId = prefs[ACTIVE_ID_KEY] ?: return@map null
        val json = prefs[ACCOUNTS_KEY] ?: "[]"
        val type = object : TypeToken<List<ApiAccount>>() {}.type
        val list: List<ApiAccount> = gson.fromJson(json, type)
        list.find { it.id == activeId }
    }

    suspend fun addAccount(account: ApiAccount) {
        val current = accounts.first().toMutableList()
        current.add(account)
        context.apiDataStore.edit { prefs ->
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun updateAccount(account: ApiAccount) {
        val current = accounts.first().toMutableList()
        val index = current.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            current[index] = account
            context.apiDataStore.edit { prefs ->
                prefs[ACCOUNTS_KEY] = gson.toJson(current)
            }
        }
    }

    suspend fun deleteAccount(accountId: String) {
        val current = accounts.first().toMutableList()
        current.removeAll { it.id == accountId }
        context.apiDataStore.edit { prefs ->
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
            if (prefs[ACTIVE_ID_KEY] == accountId) {
                prefs.remove(ACTIVE_ID_KEY)
            }
        }
    }

    suspend fun setActive(accountId: String) {
        context.apiDataStore.edit { prefs ->
            prefs[ACTIVE_ID_KEY] = accountId
        }
    }
}
