package com.chatroom.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatroom.app.R
import com.chatroom.app.data.model.SessionMode
import com.chatroom.app.data.repository.BackupManager
import com.chatroom.app.navigation.AppNavigation
import com.chatroom.app.terminal.TerminalSession
import com.chatroom.app.ui.components.Sidebar
import com.chatroom.app.ui.components.SidebarDestination
import com.chatroom.app.ui.screens.RepoLoginScreen
import com.chatroom.app.ui.theme.ChatRoomTheme
import com.chatroom.app.viewmodel.ApiAccountViewModel
import com.chatroom.app.viewmodel.ChatViewModel
import com.chatroom.app.viewmodel.IdentityViewModel
import com.chatroom.app.viewmodel.SettingsViewModel
import com.chatroom.app.viewmodel.UserProfileViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("settings_preferences", Context.MODE_PRIVATE)
            .getString("language", "zh-CN") ?: "zh-CN"
        val locale = when (lang) {
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            else -> Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val chatViewModel: ChatViewModel = viewModel()
            val apiAccountViewModel: ApiAccountViewModel = viewModel()
            val identityViewModel: IdentityViewModel = viewModel()
            val userProfileViewModel: UserProfileViewModel = viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isLoggedIn by userProfileViewModel.isLoggedIn.collectAsState()

            // Observe recreate event for language switching
            LaunchedEffect(Unit) {
                settingsViewModel.recreateEvent.collect {
                    recreate()
                }
            }

            ChatRoomTheme(themeMode = themeMode) {
                ChatRoomAppContent(
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    apiAccountViewModel = apiAccountViewModel,
                    identityViewModel = identityViewModel,
                    userProfileViewModel = userProfileViewModel,
                    isLoggedIn = isLoggedIn
                )
            }
        }
    }
}

@Composable
private fun ChatRoomAppContent(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    apiAccountViewModel: ApiAccountViewModel,
    identityViewModel: IdentityViewModel,
    userProfileViewModel: UserProfileViewModel,
    isLoggedIn: Boolean
) {
    var currentDestination by remember { mutableStateOf(SidebarDestination.Main) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    val sessions by chatViewModel.sessions.collectAsState()
    val activeSession by chatViewModel.activeSession.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Terminal sessions per coding assistant session
    val terminalSessions = remember { mutableStateMapOf<String, TerminalSession>() }

    // Repo login overlay state
    var showRepoLogin by remember { mutableStateOf(false) }
    var repoLoginUrl by remember { mutableStateOf("") }

    // Auto-create terminal sessions for coding assistant sessions
    LaunchedEffect(sessions) {
        sessions.filter { it.isCodingAssistant }.forEach { session ->
            if (!terminalSessions.containsKey(session.id)) {
                val ts = TerminalSession("Terminal-${session.id}")
                ts.start(session.localPath.ifBlank { "/" })
                terminalSessions[session.id] = ts
            }
        }
    }

    // Auto-restore from backup file on first launch
    LaunchedEffect(Unit) {
        val backupManager = BackupManager(context)
        val restored = backupManager.autoRestoreIfNeeded()
        if (restored) {
            Toast.makeText(context, context.getString(R.string.restored_from_backup), Toast.LENGTH_LONG).show()
        }
    }

    // Redirect to Profile screen on first launch if not logged in
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            currentDestination = SidebarDestination.Profile
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // BackHandler: repo login -> wizard -> main -> system back
        BackHandler(enabled = showRepoLogin) { showRepoLogin = false }
        BackHandler(enabled = !showRepoLogin && !isSidebarOpen && currentDestination != SidebarDestination.Main) {
            currentDestination = SidebarDestination.Main
        }
        BackHandler(enabled = isSidebarOpen) { isSidebarOpen = false }

        // Main content (or wizard)
        if (currentDestination != SidebarDestination.CodingAssistantSetup) {
            AppNavigation(
                currentDestination = currentDestination,
                chatViewModel = chatViewModel,
                settingsViewModel = settingsViewModel,
                apiAccountViewModel = apiAccountViewModel,
                identityViewModel = identityViewModel,
                userProfileViewModel = userProfileViewModel,
                terminalSessions = terminalSessions,
                onToggleSidebar = { isSidebarOpen = !isSidebarOpen },
                onSetupNavigate = { url ->
                    repoLoginUrl = url
                    showRepoLogin = true
                },
                onCloseSidebar = { isSidebarOpen = false }
            )
        } else {
            // Show wizard directly (outside AnimatedContent to avoid conflicts)
            CodingAssistantScreen(
                chatViewModel = chatViewModel,
                onOpenRepoLogin = { url ->
                    repoLoginUrl = url
                    showRepoLogin = true
                },
                onBack = { currentDestination = SidebarDestination.Main }
            )
        }

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
            onSelectSessionMode = { sessionId, mode ->
                chatViewModel.setSessionMode(sessionId, mode)
                currentDestination = SidebarDestination.Main
            },
            onDeleteSession = { sessionId ->
                chatViewModel.deleteSession(sessionId)
                terminalSessions.remove(sessionId)
            },
            onNewChat = {
                chatViewModel.createNewSession()
                currentDestination = SidebarDestination.Main
            },
            onAddCodingAssistant = {
                currentDestination = SidebarDestination.CodingAssistantSetup
            },
            onClose = { isSidebarOpen = false }
        )

        // Repo login overlay (full screen above everything)
        if (showRepoLogin) {
            RepoLoginScreen(
                repoUrl = repoLoginUrl,
                onLoginSuccess = { token ->
                    Toast.makeText(context, "Repository connected successfully", Toast.LENGTH_SHORT).show()
                    showRepoLogin = false
                },
                onBack = { showRepoLogin = false }
            )
        }
    }
}

@Composable
private fun CodingAssistantScreen(
    chatViewModel: ChatViewModel,
    onOpenRepoLogin: (String) -> Unit,
    onBack: () -> Unit
) {
    val sessions by chatViewModel.sessions.collectAsState()
    val apiAccounts by chatViewModel.apiAccounts.collectAsState()
    val codingAssistantCount = sessions.count { it.isCodingAssistant }

    com.chatroom.app.ui.screens.CodingAssistantWizardScreen(
        apiAccounts = apiAccounts,
        currentCodingAssistantCount = codingAssistantCount,
        onCreate = { apiAccountId, systemPrompt, repoUrl, repoOwner, repoName ->
            chatViewModel.createCodingAssistantSession(
                apiAccountId, systemPrompt, repoUrl, repoOwner, repoName
            )
            onBack()
        },
        onOpenRepoLogin = onOpenRepoLogin,
        onBack = onBack
    )
}
