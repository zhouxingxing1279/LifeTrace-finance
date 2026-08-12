package com.lifetrace.finance.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifetrace.finance.AppGraph
import java.util.concurrent.TimeUnit

class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val graph = AppGraph.get(applicationContext)
        val profile = graph.finance.ensureProfile()
        val generated = graph.bookkeeping.executeDueRecurring(profile.id)
        if (generated > 0) SyncScheduler.scheduleNow(applicationContext)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}

object RecurringScheduler {
    private const val UNIQUE_NOW = "lifetrace-finance-recurring-now"
    private const val UNIQUE_PERIODIC = "lifetrace-finance-recurring-periodic"

    fun scheduleNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RecurringWorker>().build(),
        )
    }

    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build(),
        )
    }
}
