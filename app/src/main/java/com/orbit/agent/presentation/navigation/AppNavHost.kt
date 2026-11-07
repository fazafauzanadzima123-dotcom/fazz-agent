package com.orbit.agent.presentation.navigation

import android.app.Application
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orbit.agent.OrbitApp
import com.orbit.agent.presentation.chat.ChatScreen
import com.orbit.agent.presentation.chat.ChatViewModel
import com.orbit.agent.presentation.settings.MemoryScreen
import com.orbit.agent.presentation.settings.SettingsScreen
import com.orbit.agent.presentation.settings.SettingsViewModel

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
}

@Composable
fun AppNavHost() {
    val app = LocalContext.current.applicationContext as OrbitApp
    val nav = rememberNavController()
    val chatVm: ChatViewModel = viewModel(factory = viewModelFactory { initializer { ChatViewModel(app.container, app as Application) } })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.container) } })

    NavHost(
        navController = nav,
        startDestination = Routes.CHAT,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable(Routes.CHAT) { ChatScreen(chatVm, onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(settingsVm, onBack = { nav.popBackStack() }, onOpenMemory = { nav.navigate(Routes.MEMORY) })
        }
        composable(Routes.MEMORY) { MemoryScreen(settingsVm, onBack = { nav.popBackStack() }) }
    }
}
