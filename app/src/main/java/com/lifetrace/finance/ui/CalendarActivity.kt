package com.lifetrace.finance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.TransactionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(applicationContext)
        setContent {
            LifeTraceTheme {
                val profile by produceState<com.lifetrace.finance.data.LocalProfileEntity?>(null) { value = graph.finance.ensureProfile() }
                val ledgerId = profile?.let { graph.ledgerSelection.selectedLedgerId(it.id) }
                val rows by produceState(initialValue = emptyList<TransactionEntity>(), profile, ledgerId) {
                    val p = profile ?: return@produceState
                    graph.finance.transactions(p.id).collect { all -> value = all.filter { ledgerId == null || it.ledgerId == ledgerId } }
                }
                val accounts by produceState(initialValue = emptyList<AccountEntity>(), profile, ledgerId) {
                    val p = profile ?: return@produceState
                    graph.finance.accounts(p.id).collect { all -> value = all.filter { ledgerId == null || it.ledgerId == ledgerId } }
                }
                CalendarScreen(rows, accounts, ::finish)
            }
        }
    }
}

@Composable
private fun CalendarScreen(rows: List<TransactionEntity>, accounts: List<AccountEntity>, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val monthRows = rows.filter { it.deletedAt == null && it.status == "confirmed" && it.localDate.startsWith(month.toString()) }
    val byDate = monthRows.groupBy { it.localDate }
    val selectedRows = byDate[selected.toString()].orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("返回", Modifier.clickable(onClick = onBack).padding(12.dp), color = MaterialTheme.colorScheme.primary)
                Text("账单日历", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.width(60.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { month = month.minusMonths(1); selected = month.atDay(1) }) { Icon(Icons.Default.ChevronLeft, "上个月") }
                Text("${month.year} 年 ${month.monthValue} 月", Modifier.width(150.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { month = month.plusMonths(1); selected = month.atDay(1) }) { Icon(Icons.Default.ChevronRight, "下个月") }
            }
        }
        item { CalendarGrid(month, selected, byDate, onSelect = { selected = it }) }
        item {
            val income = selectedRows.filter { it.transactionType == "income" || it.transactionType == "refund" }.sumOf { it.amountCents }
            val expense = selectedRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日", style = MaterialTheme.typography.titleMedium)
                    Text("收入 ${MoneyParser.formatCny(income)}   支出 ${MoneyParser.formatCny(expense)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (selectedRows.isEmpty()) item { Text("当天没有账单", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(selectedRows, key = { it.id }) { row ->
            val account = accounts.firstOrNull { it.id == row.accountId }?.name
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(row.merchant ?: row.counterparty ?: row.note ?: when (row.transactionType) { "income" -> "收入"; "transfer" -> "转账"; else -> "支出" }, fontWeight = FontWeight.Medium); account?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                Text((if (row.transactionType == "income") "+" else if (row.transactionType == "expense") "-" else "") + MoneyParser.formatCny(row.amountCents))
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, selected: LocalDate, rows: Map<String, List<TransactionEntity>>, onSelect: (LocalDate) -> Unit) {
    val offset = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = List(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).padding(6.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) } }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).height(54.dp)) else {
                        val dayRows = rows[date.toString()].orEmpty()
                        val hasExpense = dayRows.any { it.transactionType == "expense" }
                        val hasIncome = dayRows.any { it.transactionType == "income" || it.transactionType == "refund" }
                        Column(Modifier.weight(1f).height(54.dp).clickable { onSelect(date) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(34.dp).background(if (date == selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Text("${date.dayOfMonth}", color = if (date == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { if (hasExpense) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.error, CircleShape)); if (hasIncome) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(54.dp)) }
            }
        }
    }
}
