package com.chatroom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.chatroom.app.ui.theme.ThemeMode
import com.chatroom.app.viewmodel.AccountViewModel
import com.chatroom.app.viewmodel.ChatViewModel
import com.chatroom.app.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val chatViewModel: ChatViewModel = viewModel()
            val accountViewModel: AccountViewModel = viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()

            ChatRoomTheme(themeMode = themeMode) {
                ChatRoomAppContent(
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    accountViewModel = accountViewModel
                )
            }
        }
    }
}

@Composable
private fun ChatRoomAppContent(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    accountViewModel: AccountViewModel
) {
    var currentDestination by remember { mutableStateOf(SidebarDestination.Main) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    val sessions by chatViewModel.sessions.collectAsState()
    val activeSession by chatViewModel.activeSession.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        AppNavigation(
            currentDestination = currentDestination,
            chatViewModel = chatViewModel,
            settingsViewModel = settingsViewModel,
            accountViewModel = accountViewModel,
            onToggleSidebar = { isSidebarOpen = !isSidebarOpen }
        )

        // Sidebar overlay
        Sidebar(
            isOpen = isSidebarOpen,
            sessions = sessions,
            activeSessionId = activeSession?.id,
            currentDestination = currentDestination,
            onNavigate = { currentDestination = it },
            onSelectSession = { sessionId ->
                chatViewModel.selectSession(sessionId)
                currentDestination = SidebarDestination.Main
            },
            onNewChat = {
                chatViewModel.createNewSession()
                currentDestination = SidebarDestination.Main
            },
            onClose = { isSidebarOpen = false }
        )
    }
}
