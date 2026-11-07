package com.fazz.agent.core.proactive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.Constraints
import com.fazz.agent.FazzApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules one-shot reminder notifications. */
class ReminderScheduler(private val context: Context) {
    fun schedule(text: String, delay: Duration) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("text" to text))
            .addTag("reminder")
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.success()
        val notifier = (applicationContext as FazzApp).container.notifier
        notifier.post(
            (System.currentTimeMillis() % 100000).toInt() + 2000,
            notifier.base(Notifier.CH_REMINDER, "Pengingat dari Fazz", text).setAutoCancel(true).setContentIntent(notifier.openAppIntent()),
        )
        return Result.success()
    }
}

/** Daily morning briefing at a user-chosen hour. */
class BriefingScheduler(private val context: Context) {
    private val name = "morning_briefing"

    fun schedule(hour: Int, minute: Int = 0) {
        val now = ZonedDateTime.now()
        var next = now.with(LocalTime.of(hour, minute)).withSecond(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<BriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel() = WorkManager.getInstance(context).cancelUniqueWork(name)

    /** For "Coba sekarang" in Settings. */
    fun runNow() = WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BriefingWorker>().build())
}

class BriefingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FazzApp).container
        val result = c.session.runBackgroundTask(
            title = "Briefing pagi " + LocalDate.now().toString(),
            prompt = "Buat briefing pagi singkat untuk saya dalam Bahasa Indonesia: tanggal & hari ini, jadwal kalender hari ini, " +
                "notifikasi penting yang belum dibaca (prioritaskan dari orang, abaikan promo), dan 2-3 berita utama Indonesia hari ini " +
                "jika pencarian web tersedia. Format: poin-poin pendek. Jangan melakukan aksi apa pun selain membaca.",
            allowedTools = setOf("get_current_datetime", "read_calendar_events", "read_notifications", "web_search", "get_device_info"),
        )
        val notifier = c.notifier
        notifier.post(
            Notifier.ID_BRIEFING,
            notifier.base(Notifier.CH_BRIEFING, "Briefing pagi dari Fazz", result.text.ifBlank { "Briefing gagal dibuat: ${result.error}" })
                .setAutoCancel(true)
                .setContentIntent(notifier.openAppIntent(result.conversationId, 7)),
        )
        return Result.success()
    }
}
