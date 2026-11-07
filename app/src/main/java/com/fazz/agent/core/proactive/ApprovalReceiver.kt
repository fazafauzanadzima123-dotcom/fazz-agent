package com.fazz.agent.core.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fazz.agent.FazzApp

/** Handles Izinkan / Tolak / STOP buttons on Fazz's approval notification. */
class ApprovalReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_APPROVE = "com.fazz.agent.APPROVE"
        const val ACTION_DENY = "com.fazz.agent.DENY"
        const val ACTION_STOP = "com.fazz.agent.STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as FazzApp).container
        when (intent.action) {
            ACTION_APPROVE -> c.interaction.resolve(true)
            ACTION_DENY -> c.interaction.resolve(false)
            ACTION_STOP -> { c.interaction.resolve(false); c.session.stop() }
        }
        c.notifier.cancel(Notifier.ID_APPROVAL)
    }
}
