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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.MainActivity
import com.lifetrace.finance.R
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.domain.BudgetAlertLevel
import com.lifetrace.finance.domain.BudgetAlertPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class BudgetAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val graph = AppGraph.get(applicationContext)
        val profile = graph.finance.ensureProfile()
        val prefs = applicationContext.getSharedPreferences("budget_alert_state", Context.MODE_PRIVATE)
        val transactions = graph.finance.transactions(profile.id).first()
        val categories = graph.finance.categories(profile.id).first().associateBy { it.id }
        graph.bookkeeping.ledgers(profile.id).first().forEach { ledger ->
            graph.bookkeeping.budgets(profile.id, ledger.id).first().forEach budgetLoop@ { budget ->
                val (from, to) = graph.bookkeeping.budgetPeriod(budget, LocalDate.now())
                val used = transactions.asSequence()
                    .filter { it.ledgerId == ledger.id && it.deletedAt == null && it.status == "confirmed" && it.transactionType == "expense" && !it.excludeFromBudget }
                    .filter { it.localDate >= from.toString() && it.localDate <= to.toString() }
                    .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
                    .sumOf { it.nativeAmountCents ?: it.amountCents }
                val level = BudgetAlertPolicy.level(used, budget.amountCents)
                if (level == BudgetAlertLevel.NONE) return@budgetLoop
                val dedup = "${budget.id}:$from:${level.name}"
                if (prefs.getBoolean(dedup, false)) return@budgetLoop
                notifyBudget(budget.id.hashCode(), categories[budget.categoryId]?.name ?: "总预算", used, budget.amountCents, level)
                prefs.edit().putBoolean(dedup, true).apply()
            }
        }
    }.fold({ Result.success() }, { Result.retry() })

    private fun notifyBudget(id: Int, name: String, used: Long, limit: Long, level: BudgetAlertLevel) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pendingIntent = PendingIntent.getActivity(applicationContext, id, Intent(applicationContext, MainActivity::class.java).putExtra("destination", "accounts"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (level == BudgetAlertLevel.EXCEEDED) "$name 已超支" else "$name 已使用 80%"
        val text = "已用 ${MoneyParser.formatCny(used)} / ${MoneyParser.formatCny(limit)}"
        NotificationManagerCompat.from(applicationContext).notify(id, NotificationCompat.Builder(applicationContext, "budget_alerts").setSmallIcon(R.drawable.ic_lifetrace).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(pendingIntent).build())
    }
}

object BudgetAlertScheduler {
    private const val NOW = "lifetrace-budget-alert-now"
    private const val PERIODIC = "lifetrace-budget-alert-periodic"
    fun scheduleNow(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BudgetAlertWorker>().build())
    fun ensurePeriodic(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BudgetAlertWorker>(12, TimeUnit.HOURS).build())
}
