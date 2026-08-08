package com.lifetrace.finance.sync

import android.content.Context
import androidx.work.*
import com.lifetrace.finance.AppGraph
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AppGraph.get(applicationContext).syncEngine.runOnce().fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}

object SyncScheduler {
    private const val UNIQUE_NOW = "lifetrace-finance-sync-now"
    private const val UNIQUE_PERIODIC = "lifetrace-finance-sync-periodic"

    fun scheduleNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelNow(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NOW)
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
