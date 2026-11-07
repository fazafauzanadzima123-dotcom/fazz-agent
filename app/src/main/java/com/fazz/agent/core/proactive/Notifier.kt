package com.fazz.agent.core.proactive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fazz.agent.MainActivity
import com.fazz.agent.R

/** Fazz's notification channels and helpers. */
class Notifier(private val context: Context) {
    companion object {
        const val CH_AGENT = "agent_progress"
        const val CH_ALERT = "agent_alert"
        const val CH_BRIEFING = "briefing"
        const val CH_REMINDER = "reminder"
        const val ID_AGENT = 1001
        const val ID_ALERT = 1002
        const val ID_BRIEFING = 1003
        const val ID_APPROVAL = 1004
        const val EXTRA_CONVERSATION = "conversation_id"
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_AGENT, "Fazz sedang bekerja", NotificationManager.IMPORTANCE_LOW).apply { description = "Progres tugas yang sedang dikerjakan Fazz" },
                NotificationChannel(CH_ALERT, "Butuh persetujuan & tugas selesai", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_BRIEFING, "Briefing pagi", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_REMINDER, "Pengingat", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun canPost(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(conversationId: String? = null, requestCode: Int = 0): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        conversationId?.let { i.putExtra(EXTRA_CONVERSATION, it) }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun base(channel: String, title: String, text: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_fazz)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setColor(0xFF2E4A7D.toInt())

    @android.annotation.SuppressLint("MissingPermission")
    fun post(id: Int, builder: NotificationCompat.Builder) {
        if (canPost()) NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun action(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, ApprovalReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Approval request while Fazz works in another app; a backup to the floating panel. */
    fun showApproval(title: String, message: String, confirmLabel: String, denyLabel: String) {
        val b = base(CH_ALERT, title, message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setContentIntent(openAppIntent(requestCode = 40))
            .addAction(0, denyLabel, action(ApprovalReceiver.ACTION_DENY, 41))
            .addAction(0, confirmLabel, action(ApprovalReceiver.ACTION_APPROVE, 42))
            .addAction(0, "STOP", action(ApprovalReceiver.ACTION_STOP, 43))
        post(ID_APPROVAL, b)
    }
}
