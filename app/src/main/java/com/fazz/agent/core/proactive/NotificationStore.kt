package com.fazz.agent.core.proactive

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NotifItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
) {
    fun timeLabel(): String = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(postedAt).atZone(ZoneId.systemDefault()))
}

/**
 * Recent notifications captured by [FazzNotificationListener], kept in memory only (never written to disk).
 * One-time codes are masked before anything is stored.
 */
class NotificationStore(private val context: Context) {
    private val items = ArrayDeque<NotifItem>()
    private val lock = Any()

    fun listenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun add(item: NotifItem) = synchronized(lock) {
        items.removeAll { it.key == item.key }
        items.addFirst(item.copy(title = mask(item.title), text = mask(item.text)))
        while (items.size > 150) items.removeLast()
        val cutoff = System.currentTimeMillis() - 36 * 3600_000L
        items.removeAll { it.postedAt < cutoff }
    }

    fun recent(limit: Int, app: String? = null): List<NotifItem> = synchronized(lock) {
        items.asSequence()
            .filter { app.isNullOrBlank() || it.appLabel.contains(app, ignoreCase = true) || it.packageName.contains(app, ignoreCase = true) }
            .take(limit).toList()
    }

    companion object {
        private val otp = Regex("(?i)(otp|kode|code|verifikasi|verification|pin|sandi|password)[^\\n]{0,40}?\\b\\d{4,8}\\b|\\b\\d{4,8}\\b(?=[^\\n]{0,40}(otp|kode|code|verifikasi|verification))")

        /** Hides one-time codes so they are never sent to the AI. */
        fun mask(s: String): String = otp.replace(s) { m -> m.value.replace(Regex("\\d{4,8}"), "••••") }
    }
}
