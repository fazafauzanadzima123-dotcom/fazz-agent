package com.fazz.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.fazz.agent.presentation.navigation.AppNavHost
import com.fazz.agent.presentation.theme.FazzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Status/navigation bar icons automatically switch for light (cream) and dark mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FazzTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AppNavHost() }
            }
        }
    }
}
