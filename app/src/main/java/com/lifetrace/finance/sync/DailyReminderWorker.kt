package com.lifetrace.finance.sync

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifetrace.finance.MainActivity
import com.lifetrace.finance.R
import com.lifetrace.finance.domain.ReminderSettings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = ReminderSettings(applicationContext)
        if (!settings.enabled) return Result.success()
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java).putExtra("destination", "quick").putExtra("transaction_type", "expense")
        val pending = PendingIntent.getActivity(applicationContext, 2100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(applicationContext).notify(
            2100,
            NotificationCompat.Builder(applicationContext, "daily_reminders")
                .setSmallIcon(R.drawable.ic_lifetrace).setContentTitle("今天的账记完了吗？")
                .setContentText("花一分钟补齐今天的收支，数据才不会遗漏。")
                .setContentIntent(pending).setAutoCancel(true).build(),
        )
        return Result.success()
    }
}

object DailyReminderScheduler {
    private const val WORK = "lifetrace-daily-bookkeeping-reminder"

    fun update(context: Context) {
        val settings = ReminderSettings(context)
        val manager = WorkManager.getInstance(context)
        if (!settings.enabled) { manager.cancelUniqueWork(WORK); return }
        val now = ZonedDateTime.now()
        var next = now.withHour(settings.hour).withMinute(settings.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        manager.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next)).build(),
        )
    }
}
