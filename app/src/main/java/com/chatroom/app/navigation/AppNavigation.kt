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
import com.chatroom.app.ui.screens.ApiAccountScreen
import com.chatroom.app.ui.screens.ChatScreen
import com.chatroom.app.ui.screens.IdentityScreen
import com.chatroom.app.ui.screens.SessionManagerScreen
import com.chatroom.app.ui.screens.SettingsScreen
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
    onToggleSidebar: () -> Unit,
    onCloseSidebar: () -> Unit = onToggleSidebar,
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
