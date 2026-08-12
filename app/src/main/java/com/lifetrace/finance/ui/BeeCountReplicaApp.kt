package com.lifetrace.finance.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.CategoryEntity
import com.lifetrace.finance.data.TransactionEntity
import com.lifetrace.finance.importer.BillImportActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BcYellow = Color(0xFFFFCB2E)
private val BcYellowDark = Color(0xFFF5B900)
private val BcInk = Color(0xFF202124)
private val BcMuted = Color(0xFF8A8A8A)
private val BcDivider = Color(0xFFF0F0F0)
private val BcPage = Color(0xFFF7F7F7)
private val BcGreen = Color(0xFF3C9A72)

private enum class BcTab(val label: String, val icon: ImageVector) {
    Bills("明细", Icons.Default.ReceiptLong),
    Analytics("图表", Icons.Default.PieChart),
    Accounts("账本", Icons.Default.AccountBalanceWallet),
    Mine("我的", Icons.Default.Person),
}

@Composable
fun BeeCountReplicaApp(
    vm: FinanceViewModel,
    initialDestination: String,
    sharedText: String?,
    initialTransactionType: String? = null,
) {
    var tab by remember(initialDestination) {
        mutableStateOf(
            when (initialDestination) {
                "reports" -> BcTab.Analytics
                "accounts" -> BcTab.Accounts
                "settings" -> BcTab.Mine
                else -> BcTab.Bills
            },
        )
    }
    var showEntry by remember(initialDestination) { mutableStateOf(initialDestination == "quick") }
    val message by vm.message.collectAsState()

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (!showEntry) BcBottomBar(tab = tab, onTab = { tab = it }, onAdd = { showEntry = true })
        },
    ) { insets ->
        Box(Modifier.padding(insets).fillMaxSize()) {
            if (showEntry) {
                BcEntryScreen(vm, sharedText, initialTransactionType, onClose = { showEntry = false })
            } else {
                when (tab) {
                    BcTab.Bills -> BcBillsScreen(vm)
                    BcTab.Analytics -> BcAnalyticsScreen(vm)
                    BcTab.Accounts -> BcAccountsScreen(vm)
                    BcTab.Mine -> BcMineScreen(vm)
                }
            }
            message?.let { msg ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 18.dp, end = 18.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (msg.error) MaterialTheme.colorScheme.errorContainer else Color(0xEB202124),
                    shadowElevation = 5.dp,
                ) {
                    Text(
                        msg.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        color = if (msg.error) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BcBottomBar(tab: BcTab, onTab: (BcTab) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White)) {
        HorizontalDivider(color = BcDivider, modifier = Modifier.align(Alignment.TopCenter))
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BcNavItem(BcTab.Bills, tab == BcTab.Bills, Modifier.weight(1f)) { onTab(BcTab.Bills) }
            BcNavItem(BcTab.Analytics, tab == BcTab.Analytics, Modifier.weight(1f)) { onTab(BcTab.Analytics) }
            Spacer(Modifier.weight(1f))
            BcNavItem(BcTab.Accounts, tab == BcTab.Accounts, Modifier.weight(1f)) { onTab(BcTab.Accounts) }
            BcNavItem(BcTab.Mine, tab == BcTab.Mine, Modifier.weight(1f)) { onTab(BcTab.Mine) }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(58.dp).clickable(onClick = onAdd),
            shape = CircleShape,
            color = BcYellow,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "记一笔", tint = BcInk, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun BcNavItem(item: BcTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick).padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = if (selected) BcYellowDark else Color(0xFF8A8A8A), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(item.label, fontSize = 11.sp, color = if (selected) BcInk else BcMuted)
    }
}

@Composable
private fun BcBillsScreen(vm: FinanceViewModel) {
    val rows by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val now = LocalDate.now()
    val monthKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val monthRows = rows.filter { it.deletedAt == null && it.localDate.startsWith(monthKey) }
    val income = monthRows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    val expense = monthRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val filtered = rows.filter { row ->
        if (row.deletedAt != null) false else {
            val p = presentTransaction(row, accounts)
            query.isBlank() || listOfNotNull(p.title, p.accountLine, row.note, row.item, row.localDate)
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
    }
    val grouped = filtered.groupBy { it.localDate }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(BcYellow).statusBarsPadding()
                    .padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
            ) {
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("蜜蜂账本", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BcInk, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Default.Search, contentDescription = "搜索", tint = BcInk) }
                    IconButton(onClick = { vm.syncNow() }) { Icon(Icons.Default.Sync, contentDescription = "同步", tint = BcInk) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.width(76.dp)) {
                        Text("${now.year}年", fontSize = 11.sp, color = BcInk.copy(alpha = .65f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${now.monthValue}月", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = BcInk)
                            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp), tint = BcInk)
                        }
                    }
                    BcSummaryCell("收入", income, Modifier.weight(1f))
                    BcSummaryCell("支出", expense, Modifier.weight(1f))
                    BcSummaryCell("结余", income - expense, Modifier.weight(1f))
                }
            }
        }
        if (searchOpen) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("transaction_search"),
                    placeholder = { Text("搜索商户、账户、备注") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空") } },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BcYellowDark,
                        unfocusedBorderColor = BcDivider,
                        focusedContainerColor = BcPage,
                        unfocusedContainerColor = BcPage,
                    ),
                )
            }
        } else {
            item { Box(Modifier.size(1.dp).testTag("transaction_search")) }
        }
        if (grouped.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, null, tint = Color(0xFFC5C5C5), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("暂无账单", color = BcMuted)
                }
            }
        } else {
            grouped.forEach { (date, dayRows) ->
                item(key = "d-$date") { BcDayHeader(date, dayRows) }
                items(dayRows, key = { it.id }) { tx ->
                    BcTransactionRow(tx, accounts, categories)
                    HorizontalDivider(color = BcDivider, modifier = Modifier.padding(start = 62.dp))
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun BcSummaryCell(label: String, cents: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(label, fontSize = 11.sp, color = BcInk.copy(alpha = .62f))
        Text(MoneyParser.formatPlain(cents), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = BcInk, maxLines = 1)
    }
}

@Composable
private fun BcDayHeader(date: String, rows: List<TransactionEntity>) {
    val exp = rows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val inc = rows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    Row(
        Modifier.fillMaxWidth().height(32.dp).background(Color(0xFFF7F7F7)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, fontSize = 11.sp, color = BcMuted, modifier = Modifier.weight(1f))
        Text("支出 ${MoneyParser.formatPlain(exp)}  收入 ${MoneyParser.formatPlain(inc)}", fontSize = 11.sp, color = BcMuted)
    }
}

@Composable
private fun BcTransactionRow(tx: TransactionEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>) {
    val category = categories.firstOrNull { it.id == tx.categoryId }?.name ?: if (tx.transactionType == "transfer") "转账" else "其他"
    val p = presentTransaction(tx, accounts, category)
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(BcCategoryTint(category), CircleShape), contentAlignment = Alignment.Center) {
            Icon(BcCategoryIcon(category, tx.transactionType), null, tint = Color(0xFF5E5868), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = BcInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(listOfNotNull(category, p.accountLine).joinToString(" · "), fontSize = 11.sp, color = BcMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when (tx.transactionType) {
                "expense" -> "-${MoneyParser.formatPlain(tx.amountCents)}"
                "income" -> "+${MoneyParser.formatPlain(tx.amountCents)}"
                else -> MoneyParser.formatPlain(tx.amountCents)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (tx.transactionType == "income") BcGreen else BcInk,
        )
    }
}

@Composable
private fun BcEntryScreen(vm: FinanceViewModel, sharedText: String?, initialTransactionType: String?, onClose: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var type by remember(initialTransactionType) {
        mutableStateOf(when (initialTransactionType) { "income" -> TransactionType.INCOME; "transfer" -> TransactionType.TRANSFER; else -> TransactionType.EXPENSE })
    }
    var amount by remember(sharedText) { mutableStateOf(BcExtractAmount(sharedText.orEmpty())) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember(type, categories) { mutableStateOf(categories.firstOrNull { it.categoryType == type.wire }?.id) }
    var note by remember(sharedText) { mutableStateOf(sharedText?.take(100).orEmpty()) }
    val visibleCategories = categories.filter { it.categoryType == type.wire }.take(15)

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("取消", color = BcInk, fontSize = 14.sp) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                BcTypeTab("支出", type == TransactionType.EXPENSE) { type = TransactionType.EXPENSE }
                BcTypeTab("收入", type == TransactionType.INCOME) { type = TransactionType.INCOME }
                BcTypeTab("转账", type == TransactionType.TRANSFER) { type = TransactionType.TRANSFER }
            }
            Spacer(Modifier.width(56.dp))
        }
        HorizontalDivider(color = BcDivider)
        Text(
            if (amount.isBlank()) "¥0.00" else "¥$amount",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 22.dp, bottom = 8.dp).testTag("quick_amount"),
            textAlign = TextAlign.End,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = BcInk,
        )

        if (type == TransactionType.TRANSFER) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BcAccountPill("转出", accounts, accountId, Modifier.weight(1f)) { accountId = it }
                BcAccountPill("转入", accounts.filter { it.id != accountId }, toAccountId, Modifier.weight(1f)) { toAccountId = it }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                visibleCategories.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(74.dp)) {
                        row.forEach { cat ->
                            BcCategoryCell(cat, selected = categoryId == cat.id, Modifier.weight(1f)) { categoryId = cat.id }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BcAccountPill("账户", accounts, accountId, Modifier.weight(1f)) { accountId = it }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F4F4), modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = BcMuted)
                    Spacer(Modifier.width(6.dp))
                    Text("今天", fontSize = 12.sp, color = BcInk)
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            placeholder = { Text("备注") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFFF5F5F5),
                unfocusedContainerColor = Color(0xFFF5F5F5),
            ),
        )
        Spacer(Modifier.weight(1f))
        BcKeypad(
            onKey = { key ->
                amount = when (key) {
                    "⌫" -> amount.dropLast(1)
                    "." -> if (amount.contains('.')) amount else if (amount.isBlank()) "0." else "$amount."
                    else -> if (amount.contains('.') && amount.substringAfter('.').length >= 2) amount else (amount + key).take(12)
                }
            },
            onDone = {
                vm.save(type, amount, accountId, toAccountId, categoryId, null, note.ifBlank { null })
                amount = ""; note = ""; onClose()
            },
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && accounts.isNotEmpty(),
        )
    }
}

@Composable
private fun BcTypeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 15.sp, color = BcInk, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(22.dp).height(3.dp).background(if (selected) BcYellowDark else Color.Transparent, CircleShape))
    }
}

@Composable
private fun BcCategoryCell(cat: CategoryEntity, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(42.dp).background(if (selected) BcYellow else BcCategoryTint(cat.name), CircleShape), contentAlignment = Alignment.Center) {
            Icon(BcCategoryIcon(cat.name, cat.categoryType), null, tint = BcInk, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(cat.name, fontSize = 10.sp, color = BcInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BcAccountPill(label: String, accounts: List<AccountEntity>, selectedId: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(Modifier.fillMaxWidth().clickable { open = true }, shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F4F4)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(16.dp), tint = BcMuted)
                Spacer(Modifier.width(6.dp))
                Text(accounts.firstOrNull { it.id == selectedId }?.name ?: label, fontSize = 12.sp, color = BcInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account.id); open = false }) }
        }
    }
}

@Composable
private fun BcKeypad(onKey: (String) -> Unit, onDone: () -> Unit, enabled: Boolean) {
    val keys = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf(".", "0", "⌫"))
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key ->
                        Surface(
                            Modifier.weight(1f).height(52.dp).clickable { onKey(key) },
                            color = Color.White,
                            shape = RoundedCornerShape(6.dp),
                            shadowElevation = 1.dp,
                        ) { Box(contentAlignment = Alignment.Center) { Text(key, fontSize = 22.sp, color = BcInk, fontWeight = FontWeight.Medium) } }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("今天", "+", "−").forEach { label ->
                Surface(Modifier.fillMaxWidth().height(52.dp), color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 1.dp) {
                    Box(contentAlignment = Alignment.Center) { Text(label, fontSize = if (label == "今天") 11.sp else 22.sp, color = BcInk) }
                }
            }
            Button(
                onClick = onDone,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("quick_save"),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BcYellow, contentColor = BcInk, disabledContainerColor = Color(0xFFE6E6E6)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("完成", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BcAnalyticsScreen(vm: FinanceViewModel) {
    val rows by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val now = LocalDate.now()
    val month = now.toString().take(7)
    val expenses = rows.filter { it.deletedAt == null && it.transactionType == "expense" && it.localDate.startsWith(month) }
    val total = expenses.sumOf { it.amountCents }
    val byCategory = expenses.groupBy { it.categoryId }.map { (id, list) ->
        (categories.firstOrNull { it.id == id }?.name ?: "其他") to list.sumOf { it.amountCents }
    }.sortedByDescending { it.second }

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Column(Modifier.fillMaxWidth().background(BcYellow).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("图表分析", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BcInk)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(BcYellowDark.copy(alpha = .34f), RoundedCornerShape(20.dp)).padding(3.dp)) {
                    BcSegment("月", true, Modifier.weight(1f)); BcSegment("年", false, Modifier.weight(1f)); BcSegment("全部", false, Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.padding(18.dp)) {
                Text("总支出", fontSize = 12.sp, color = BcMuted)
                Text(MoneyParser.formatCny(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BcInk)
                Text("日均 ${MoneyParser.formatCny(if (now.dayOfMonth > 0) total / now.dayOfMonth else 0)}", fontSize = 12.sp, color = BcMuted)
            }
        }
        itemsIndexed(byCategory) { index, (name, value) ->
            val ratio = if (total == 0L) 0f else value.toFloat() / total
            Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(BcCategoryTint(name), CircleShape), contentAlignment = Alignment.Center) { Icon(BcCategoryIcon(name, "expense"), null, modifier = Modifier.size(17.dp), tint = BcInk) }
                    Spacer(Modifier.width(10.dp))
                    Text("${index + 1}  $name", modifier = Modifier.weight(1f), color = BcInk)
                    Text("${(ratio * 100).toInt()}%", fontSize = 12.sp, color = BcMuted)
                    Spacer(Modifier.width(10.dp))
                    Text(MoneyParser.formatPlain(value), color = BcInk)
                }
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp).height(3.dp), color = BcYellowDark, trackColor = BcDivider)
            }
        }
    }
}

@Composable
private fun BcSegment(label: String, selected: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = if (selected) Color.White else Color.Transparent) {
        Text(label, Modifier.padding(vertical = 7.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = BcInk, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BcAccountsScreen(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsState()
    val ledgers by vm.ledgers.collectAsState()
    val selectedId by vm.selectedLedgerId.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(BcPage)) {
        item {
            Column(Modifier.fillMaxWidth().background(BcYellow).statusBarsPadding().padding(16.dp)) {
                Text("账本", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BcInk)
                Spacer(Modifier.height(12.dp))
                Text(ledgers.firstOrNull { it.id == selectedId }?.name ?: "默认账本", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BcInk)
                Text("${accounts.size} 个账户", fontSize = 12.sp, color = BcInk.copy(alpha = .62f))
            }
        }
        item { Text("账户", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 13.sp, color = BcMuted) }
        items(accounts) { account ->
            Surface(Modifier.fillMaxWidth().clickable { }.background(Color.White), color = Color.White) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(BcYellow.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = BcInk, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(account.name, color = BcInk, fontWeight = FontWeight.Medium); Text(account.accountType, fontSize = 11.sp, color = BcMuted) }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB2B2B2))
                }
            }
            HorizontalDivider(color = BcDivider, modifier = Modifier.padding(start = 68.dp))
        }
    }
}

@Composable
private fun BcMineScreen(vm: FinanceViewModel) {
    val context = LocalContext.current
    val pending by vm.pendingSync.collectAsState()
    val conflicts by vm.conflictCount.collectAsState()
    val ledgers by vm.ledgers.collectAsState()
    val rows by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(BcPage)) {
        item {
            Column(Modifier.fillMaxWidth().background(BcYellow).statusBarsPadding().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color.White.copy(alpha = .58f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Hive, null, tint = BcInk) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("LifeTrace 记账", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BcInk); Text("本地优先 · 云端同步", fontSize = 11.sp, color = BcInk.copy(alpha = .62f)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    BcMineMetric("${ledgers.size}", "账本", Modifier.weight(1f)); BcMineMetric("${rows.count { it.deletedAt == null }}", "账单", Modifier.weight(1f)); BcMineMetric("${accounts.size}", "账户", Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Column(Modifier.background(Color.White)) {
                BcMenu(Icons.Default.Sync, "同步", "待上传 $pending · 冲突 $conflicts") { vm.syncNow() }
                BcMenu(Icons.Default.AccountBalanceWallet, "记账管理", "账本、账户、分类、预算") { context.startActivity(Intent(context, BookkeepingManagementActivity::class.java)) }
                BcMenu(Icons.Default.UploadFile, "账单导入", "CSV / XLSX") { context.startActivity(Intent(context, BillImportActivity::class.java)) }
                BcMenu(Icons.Default.AutoAwesome, "智能记账", "截图识别与 AI 设置") { context.startActivity(Intent(context, AiSettingsActivity::class.java)) }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { Column(Modifier.background(Color.White)) { BcMenu(Icons.Default.Cloud, "服务器", vm.baseUrl()) {}; BcMenu(Icons.Default.Info, "关于", "LifeTrace Finance") {} } }
    }
}

@Composable
private fun BcMineMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BcInk); Text(label, fontSize = 11.sp, color = BcInk.copy(alpha = .62f)) }
}

@Composable
private fun BcMenu(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BcYellowDark, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, color = BcInk, fontSize = 15.sp); Text(subtitle, color = BcMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB5B5B5), modifier = Modifier.size(19.dp))
    }
    HorizontalDivider(color = BcDivider, modifier = Modifier.padding(start = 52.dp))
}

private fun BcCategoryTint(name: String): Color = when {
    name.contains("餐") || name.contains("食") -> Color(0xFFFFF1C9)
    name.contains("交通") || name.contains("车") -> Color(0xFFDFF2FF)
    name.contains("购") -> Color(0xFFFFE4E8)
    name.contains("医") -> Color(0xFFE5F5EA)
    name.contains("学") || name.contains("教育") -> Color(0xFFECE7FF)
    else -> Color(0xFFF1EFF5)
}

private fun BcCategoryIcon(name: String, type: String): ImageVector = when {
    type == "transfer" -> Icons.Default.SwapHoriz
    name.contains("餐") || name.contains("食") -> Icons.Default.Restaurant
    name.contains("交通") || name.contains("车") -> Icons.Default.DirectionsCar
    name.contains("购") -> Icons.Default.ShoppingBag
    name.contains("居") || name.contains("房") -> Icons.Default.Home
    name.contains("医") -> Icons.Default.MedicalServices
    name.contains("学") || name.contains("教育") -> Icons.Default.School
    name.contains("宠") -> Icons.Default.Pets
    name.contains("运动") -> Icons.Default.SportsBasketball
    name.contains("工资") || type == "income" -> Icons.Default.Payments
    else -> Icons.Default.Circle
}

private fun BcExtractAmount(text: String): String = Regex("(?:￥|¥)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*元?").find(text)?.groupValues?.getOrNull(1).orEmpty()
