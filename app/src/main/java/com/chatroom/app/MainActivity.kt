package com.chatroom.app

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatroom.app.navigation.AppNavigation
import com.chatroom.app.ui.components.Sidebar
import com.chatroom.app.ui.components.SidebarDestination
import com.chatroom.app.ui.theme.ChatRoomTheme
import com.chatroom.app.viewmodel.ApiAccountViewModel
import com.chatroom.app.viewmodel.ChatViewModel
import com.chatroom.app.viewmodel.IdentityViewModel
import com.chatroom.app.viewmodel.SettingsViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply saved locale
        applyLocale(this)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val chatViewModel: ChatViewModel = viewModel()
            val apiAccountViewModel: ApiAccountViewModel = viewModel()
            val identityViewModel: IdentityViewModel = viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()

            ChatRoomTheme(themeMode = themeMode) {
                ChatRoomAppContent(
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    apiAccountViewModel = apiAccountViewModel,
                    identityViewModel = identityViewModel
                )
            }
        }
    }
}

private fun applyLocale(activity: Activity) {
    val prefs = activity.getSharedPreferences("settings_preferences", MODE_PRIVATE)
    val lang = prefs.getString("language", "zh-CN") ?: "zh-CN"
    val locale = when (lang) {
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        else -> Locale.ENGLISH
    }
    Locale.setDefault(locale)
    val config = Configuration(activity.resources.configuration)
    config.setLocale(locale)
    activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
}

@Composable
private fun ChatRoomAppContent(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    apiAccountViewModel: ApiAccountViewModel,
    identityViewModel: IdentityViewModel
) {
    var currentDestination by remember { mutableStateOf(SidebarDestination.Main) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    val sessions by chatViewModel.sessions.collectAsState()
    val activeSession by chatViewModel.activeSession.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        AppNavigation(
            currentDestination = currentDestination,
            chatViewModel = chatViewModel,
            settingsViewModel = settingsViewModel,
            apiAccountViewModel = apiAccountViewModel,
            identityViewModel = identityViewModel,
            onToggleSidebar = { isSidebarOpen = !isSidebarOpen }
        )

        // Sidebar overlay
        Sidebar(
            isOpen = isSidebarOpen,
            sessions = sessions,
            activeSessionId = activeSession?.id,
            currentDestination = currentDestination,
            isGenerating = uiState.isSending,
            onNavigate = { currentDestination = it },
            onSelectSession = { sessionId ->
                chatViewModel.selectSession(sessionId)
                currentDestination = SidebarDestination.Main
            },
            onDeleteSession = { sessionId ->
                chatViewModel.deleteSession(sessionId)
            },
            onNewChat = {
                chatViewModel.createNewSession()
                currentDestination = SidebarDestination.Main
            },
            onClose = { isSidebarOpen = false }
        )
    }
}
