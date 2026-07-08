package com.chatroom.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.model.SessionMode
import com.chatroom.app.terminal.TerminalSession
import com.chatroom.app.ui.components.SidebarDestination
import com.chatroom.app.ui.screens.ApiAccountScreen
import com.chatroom.app.ui.screens.ChatScreen
import com.chatroom.app.ui.screens.IdentityScreen
import com.chatroom.app.ui.screens.RepoBrowserScreen
import com.chatroom.app.ui.screens.SessionManagerScreen
import com.chatroom.app.ui.screens.SettingsScreen
import com.chatroom.app.ui.screens.TerminalScreen
import com.chatroom.app.ui.screens.UserProfileScreen
import com.chatroom.app.viewmodel.ApiAccountViewModel
import com.chatroom.app.viewmodel.ChatViewModel
import com.chatroom.app.viewmodel.IdentityViewModel
import com.chatroom.app.viewmodel.SettingsViewModel
import com.chatroom.app.viewmodel.UserProfileViewModel

@Composable
fun AppNavigation(
    currentDestination: SidebarDestination,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    apiAccountViewModel: ApiAccountViewModel,
    identityViewModel: IdentityViewModel,
    userProfileViewModel: UserProfileViewModel,
    terminalSessions: Map<String, TerminalSession>,
    onToggleSidebar: () -> Unit,
    onCloseSidebar: () -> Unit = onToggleSidebar,
    modifier: Modifier = Modifier
) {
    val activeSession by chatViewModel.activeSession.collectAsState()

    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(animationSpec = tween(300)) { direction * 100 } + fadeIn(animationSpec = tween(200)))
                .togetherWith(
                    slideOutHorizontally(animationSpec = tween(300)) { direction * -100 } + fadeOut(animationSpec = tween(200))
                )
        },
        label = "ScreenTransition",
        modifier = modifier
    ) { destination ->
        when (destination) {
            SidebarDestination.Main -> {
                MainContentRouter(
                    activeSession = activeSession,
                    terminalSessions = terminalSessions,
                    chatViewModel = chatViewModel,
                    onToggleSidebar = onToggleSidebar
                )
            }
            SidebarDestination.CodingAssistantSetup -> {
                // Handled directly in MainActivity
            }
            SidebarDestination.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                onToggleSidebar = onToggleSidebar
            )
            SidebarDestination.ApiKeys -> ApiAccountScreen(
                viewModel = apiAccountViewModel,
                onToggleSidebar = onToggleSidebar
            )
            SidebarDestination.Identities -> IdentityScreen(
                viewModel = identityViewModel,
                onToggleSidebar = onToggleSidebar
            )
            SidebarDestination.Sessions -> SessionManagerScreen(
                viewModel = chatViewModel,
                onToggleSidebar = onToggleSidebar,
                onSessionSelected = onCloseSidebar
            )
            SidebarDestination.Profile -> UserProfileScreen(
                viewModel = userProfileViewModel,
                onToggleSidebar = onToggleSidebar
            )
        }
    }
}

@Composable
private fun MainContentRouter(
    activeSession: Session?,
    terminalSessions: Map<String, TerminalSession>,
    chatViewModel: ChatViewModel,
    onToggleSidebar: () -> Unit
) {
    val isCodingAssistant = activeSession?.isCodingAssistant == true
    val mode = activeSession?.mode ?: SessionMode.CHAT

    if (isCodingAssistant && mode == SessionMode.TERMINAL) {
        val sessionId = activeSession!!.id
        val terminalSession = terminalSessions[sessionId]
        if (terminalSession != null) {
            TerminalScreen(
                terminalSession = terminalSession,
                onToggleSidebar = onToggleSidebar,
                onExit = {
                    val s = activeSession
                    if (s != null) {
                        chatViewModel.setSessionMode(s.id, SessionMode.CHAT)
                    }
                }
            )
        } else {
            // Fallback to chat if terminal session not initialized
            ChatScreen(
                viewModel = chatViewModel,
                onToggleSidebar = onToggleSidebar
            )
        }
    } else if (isCodingAssistant && mode == SessionMode.REPO_HOME) {
        RepoBrowserScreen(
            activeSession = activeSession,
            onToggleSidebar = onToggleSidebar
        )
    } else {
        ChatScreen(
            viewModel = chatViewModel,
            onToggleSidebar = onToggleSidebar
        )
    }
}
