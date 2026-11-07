package com.fazz.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.fazz.agent.core.common.AppVisibility
import com.fazz.agent.core.proactive.Notifier
import com.fazz.agent.presentation.navigation.AppNavHost
import com.fazz.agent.presentation.theme.FazzTheme

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Needed for task progress, approvals while Fazz works in other apps, reminders and briefings.
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FazzTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AppNavHost() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() { super.onStart(); AppVisibility.set(true) }
    override fun onStop() { AppVisibility.set(false); super.onStop() }

    /** Opening a notification (briefing, finished task) jumps to that conversation. */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(Notifier.EXTRA_CONVERSATION) ?: return
        (application as FazzApp).container.session.open(id)
        intent.removeExtra(Notifier.EXTRA_CONVERSATION)
    }
}
