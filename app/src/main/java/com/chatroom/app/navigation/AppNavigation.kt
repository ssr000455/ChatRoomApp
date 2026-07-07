package com.chatroom.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chatroom.app.ui.components.SidebarDestination
import com.chatroom.app.ui.screens.AccountManagerScreen
import com.chatroom.app.ui.screens.ChatScreen
import com.chatroom.app.ui.screens.SessionManagerScreen
import com.chatroom.app.ui.screens.SettingsScreen
import com.chatroom.app.viewmodel.AccountViewModel
import com.chatroom.app.viewmodel.ChatViewModel
import com.chatroom.app.viewmodel.SettingsViewModel

sealed class Screen {
    data object Chat : Screen()
    data object Settings : Screen()
    data object Accounts : Screen()
    data object Sessions : Screen()
}

@Composable
fun AppNavigation(
    currentDestination: SidebarDestination,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    accountViewModel: AccountViewModel,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            SidebarDestination.Main -> ChatScreen(
                viewModel = chatViewModel,
                onToggleSidebar = onToggleSidebar
            )
            SidebarDestination.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                onToggleSidebar = onToggleSidebar
            )
            SidebarDestination.Accounts -> AccountManagerScreen(
                viewModel = accountViewModel,
                onToggleSidebar = onToggleSidebar
            )
            SidebarDestination.Sessions -> SessionManagerScreen(
                viewModel = chatViewModel,
                onToggleSidebar = onToggleSidebar,
                onSessionSelected = onToggleSidebar
            )
        }
    }
}
