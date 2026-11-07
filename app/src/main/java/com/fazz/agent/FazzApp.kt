package com.fazz.agent

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FazzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep the morning briefing schedule in sync with settings (WorkManager survives reboots).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val s = container.settings.current()
            if (s.briefingEnabled) container.briefing.schedule(s.briefingHour)
        }
    }
}
