package com.fazz.agent.core.agent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fazz.agent.FazzApp
import com.fazz.agent.core.proactive.Notifier

/**
 * Keeps Fazz alive while a task runs (it may be operating other apps), with a visible progress
 * notification and a Stop action. Stops itself when the task ends.
 */
class AgentForegroundService : Service() {
    companion object {
        private const val ACTION_STOP_TASK = "com.fazz.agent.STOP_TASK"
        private const val EXTRA_TEXT = "text"

        fun start(context: Context) = runCatching {
            context.startForegroundService(Intent(context, AgentForegroundService::class.java))
        }

        fun update(context: Context, text: String) = runCatching {
            if (running) context.startService(Intent(context, AgentForegroundService::class.java).putExtra(EXTRA_TEXT, text))
        }

        fun stop(context: Context) = runCatching { context.stopService(Intent(context, AgentForegroundService::class.java)) }

        @Volatile private var running = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as FazzApp).container
        if (intent?.action == ACTION_STOP_TASK) {
            container.session.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Sedang bekerja…"
        val stopIntent = android.app.PendingIntent.getService(
            this, 0, Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = container.notifier.base(Notifier.CH_AGENT, "Fazz sedang bekerja", text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(container.notifier.openAppIntent())
            .addAction(0, "Hentikan", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, Notifier.ID_AGENT, n, type) }
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
