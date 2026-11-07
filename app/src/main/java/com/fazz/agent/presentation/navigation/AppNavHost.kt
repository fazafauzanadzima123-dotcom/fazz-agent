package com.fazz.agent.presentation.navigation

import android.app.Application
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fazz.agent.FazzApp
import com.fazz.agent.presentation.chat.ChatScreen
import com.fazz.agent.presentation.chat.ChatViewModel
import com.fazz.agent.presentation.customize.CustomizeScreen
import com.fazz.agent.presentation.customize.LocationScreen
import com.fazz.agent.presentation.customize.PasswordScreen
import com.fazz.agent.presentation.customize.PersonalityScreen
import com.fazz.agent.presentation.customize.PluginsScreen
import com.fazz.agent.presentation.settings.MemoryScreen
import com.fazz.agent.presentation.setup.SetupScreen
import com.fazz.agent.presentation.settings.SettingsScreen
import com.fazz.agent.presentation.settings.SettingsViewModel

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
    const val SETUP = "setup"
    const val CUSTOMIZE = "customize"
    const val PERSONALITY = "personality"
    const val PLUGINS = "plugins"
    const val LOCATION = "location"
    const val PASSWORD = "password"
}

@Composable
fun AppNavHost() {
    val app = LocalContext.current.applicationContext as FazzApp
    val nav = rememberNavController()
    val chatVm: ChatViewModel = viewModel(factory = viewModelFactory { initializer { ChatViewModel(app.container, app as Application) } })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.container) } })

    // First launch: open the setup screen once so Fazz can be allowed to act on its own from the start.
    val appSettings by settingsVm.settings.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(appSettings?.setupSeen) {
        if (appSettings?.setupSeen == false) nav.navigate(Routes.SETUP)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.CHAT,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable(Routes.CHAT) {
            ChatScreen(chatVm, onOpenSettings = { nav.navigate(Routes.SETTINGS) }, onOpenMemory = { nav.navigate(Routes.MEMORY) }, onOpenCustomize = { nav.navigate(Routes.CUSTOMIZE) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(settingsVm, onBack = { nav.popBackStack() }, onOpenMemory = { nav.navigate(Routes.MEMORY) }, onOpenSetup = { nav.navigate(Routes.SETUP) })
        }
        composable(Routes.CUSTOMIZE) {
            CustomizeScreen(
                settingsVm, onBack = { nav.popBackStack() },
                onPersonality = { nav.navigate(Routes.PERSONALITY) }, onMemory = { nav.navigate(Routes.MEMORY) },
                onPlugins = { nav.navigate(Routes.PLUGINS) }, onLocation = { nav.navigate(Routes.LOCATION) }, onPassword = { nav.navigate(Routes.PASSWORD) },
            )
        }
        composable(Routes.PERSONALITY) { PersonalityScreen(settingsVm, onBack = { nav.popBackStack() }) }
        composable(Routes.PLUGINS) { PluginsScreen(settingsVm, onBack = { nav.popBackStack() }) }
        composable(Routes.LOCATION) { LocationScreen(settingsVm, onBack = { nav.popBackStack() }) }
        composable(Routes.PASSWORD) { PasswordScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETUP) {
            SetupScreen(
                disclosureAccepted = appSettings?.screenDisclosureAccepted == true,
                onAcceptDisclosure = settingsVm::acceptScreenDisclosure,
                onDone = { settingsVm.markSetupSeen(); nav.popBackStack() },
            )
        }
        composable(Routes.MEMORY) { MemoryScreen(settingsVm, onBack = { nav.popBackStack() }) }
    }
}
