package com.lifetrace.finance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.domain.AccountBalance
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.launch

class AccountDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: run { finish(); return }
        val graph = AppGraph.get(applicationContext)
        setContent {
            LifeTraceTheme {
                val account by graph.db.financeDao().accountFlow(accountId).collectAsState(initial = null)
                val rows by graph.db.financeDao().transactionsForAccount(accountId).collectAsState(initial = emptyList())
                var correcting by remember { mutableStateOf(false) }
                val balance = AccountBalance.current(account?.openingBalanceCents, accountId, rows)
                val trend = AccountBalance.monthlyTrend(account?.openingBalanceCents, accountId, rows)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(account?.name ?: "账户详情", style = MaterialTheme.typography.headlineSmall) }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("当前余额", style = MaterialTheme.typography.titleMedium)
                                Text(MoneyParser.formatCny(balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("期初余额 ${MoneyParser.formatCny(account?.openingBalanceCents ?: 0L)} · ${rows.size} 笔相关流水", style = MaterialTheme.typography.bodySmall)
                                account?.bankName?.let { Text("银行：$it") }
                                account?.last4?.let { Text("尾号：$it") }
                                account?.creditLimitCents?.let { Text("信用额度：${MoneyParser.formatCny(it)}") }
                            }
                        }
                    }
                    item { AccountTrendCard(trend) }
                    item { Button(onClick = { correcting = true }, modifier = Modifier.fillMaxWidth()) { Text("校正当前余额") }; OutlinedButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
                    item { Text("账户流水", style = MaterialTheme.typography.titleMedium) }
                    items(rows, key = { it.id }) { row ->
                        val delta = AccountBalance.transactionDelta(accountId, listOf(row))
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Column(Modifier.weight(1f)) { Text(row.merchant ?: row.counterparty ?: row.note ?: when (row.transactionType) { "transfer" -> "转账"; "income" -> "收入"; else -> "支出" }); Text(row.localDate, style = MaterialTheme.typography.bodySmall) }
                            Text((if (delta > 0) "+" else "") + MoneyParser.formatCny(delta), color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (rows.isEmpty()) item { Text("暂无账户流水", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (correcting) BalanceCorrectionDialog(balance, onDismiss = { correcting = false }) { desired ->
                    lifecycleScope.launch {
                        graph.bookkeeping.correctAccountBalance(accountId, desired, rows)
                        SyncScheduler.scheduleNow(this@AccountDetailActivity)
                        correcting = false
                    }
                }
            }
        }
    }

    companion object { const val EXTRA_ACCOUNT_ID = "account_id" }
}

@androidx.compose.runtime.Composable
private fun AccountTrendCard(points: List<AccountBalance.MonthlyPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = points.map { it.balanceCents }
    val min = values.minOrNull() ?: 0L
    val max = values.maxOrNull() ?: 0L
    val range = (max - min).coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("近 6 个月余额趋势", style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                repeat(3) { index ->
                    val y = size.height * index / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                if (points.isNotEmpty()) {
                    val chart = Path()
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1f)
                        val y = size.height - ((point.balanceCents - min).toFloat() / range) * size.height
                        if (index == 0) chart.moveTo(x, y) else chart.lineTo(x, y)
                    }
                    drawPath(chart, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1f)
                        val y = size.height - ((point.balanceCents - min).toFloat() / range) * size.height
                        drawCircle(primary, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                points.forEach { Text("${it.month.monthValue}月", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最低 ${MoneyParser.formatCny(min)}", style = MaterialTheme.typography.bodySmall)
                Text("最高 ${MoneyParser.formatCny(max)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BalanceCorrectionDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by remember { mutableStateOf(MoneyParser.formatPlain(current)) }
    val cents = MoneyParser.parseCents(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校正账户余额") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value, { value = it }, label = { Text("当前实际余额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); Text("校正通过调整期初余额实现，不会伪造一笔收支。", style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button(onClick = { onSave(requireNotNull(cents)) }, enabled = cents != null) { Text("保存") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } },
    )
}
