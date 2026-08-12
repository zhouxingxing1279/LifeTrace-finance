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

private val BeePrimary = Color(0xFFFFC928)
private val BeePrimaryDark = Color(0xFFF0B400)
private val BeeText = Color(0xFF202124)
private val BeeText2 = Color(0xFF7D7D7D)
private val BeeBg = Color(0xFFF7F7F7)
private val BeeBorder = Color(0xFFECECEC)
private val BeeGreen = Color(0xFF3B956F)

private enum class ExactTab(val label: String, val icon: ImageVector) {
    Home("明细", Icons.Default.ReceiptLong),
    Analytics("图表", Icons.Default.PieChart),
    Accounts("账本", Icons.Default.AccountBalanceWallet),
    Mine("我的", Icons.Default.Person),
}

@Composable
fun BeeCountExactApp(
    vm: FinanceViewModel,
    initialDestination: String,
    sharedText: String?,
    initialTransactionType: String? = null,
) {
    var tab by remember(initialDestination) {
        mutableStateOf(
            when (initialDestination) {
                "reports" -> ExactTab.Analytics
                "accounts" -> ExactTab.Accounts
                "settings" -> ExactTab.Mine
                else -> ExactTab.Home
            },
        )
    }
    var editorOpen by remember(initialDestination) { mutableStateOf(initialDestination == "quick") }
    val message by vm.message.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!editorOpen) ExactFloatingBottomBar(tab, onTab = { tab = it }, onAdd = { editorOpen = true })
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (editorOpen) {
                ExactTransactionEditor(
                    vm = vm,
                    sharedText = sharedText,
                    initialTransactionType = initialTransactionType,
                    onClose = { editorOpen = false },
                )
            } else {
                when (tab) {
                    ExactTab.Home -> ExactHome(vm)
                    ExactTab.Analytics -> ExactAnalytics(vm)
                    ExactTab.Accounts -> ExactAccounts(vm)
                    ExactTab.Mine -> ExactMine(vm)
                }
            }
            message?.let { msg ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp, start = 18.dp, end = 18.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (msg.error) MaterialTheme.colorScheme.errorContainer else Color(0xEB202124),
                    shadowElevation = 5.dp,
                ) {
                    Text(
                        msg.text,
                        Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                        color = if (msg.error) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExactFloatingBottomBar(tab: ExactTab, onTab: (ExactTab) -> Unit, onAdd: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                ExactNavItem(ExactTab.Home, tab == ExactTab.Home, Modifier.weight(1f)) { onTab(ExactTab.Home) }
                ExactNavItem(ExactTab.Analytics, tab == ExactTab.Analytics, Modifier.weight(1f)) { onTab(ExactTab.Analytics) }
                Spacer(Modifier.weight(1f))
                ExactNavItem(ExactTab.Accounts, tab == ExactTab.Accounts, Modifier.weight(1f)) { onTab(ExactTab.Accounts) }
                ExactNavItem(ExactTab.Mine, tab == ExactTab.Mine, Modifier.weight(1f)) { onTab(ExactTab.Mine) }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.Center).size(50.dp).clickable(onClick = onAdd),
            shape = CircleShape,
            color = BeePrimary,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "记一笔", tint = BeeText, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun ExactNavItem(tab: ExactTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = if (selected) BeePrimaryDark else Color(0xFF777777), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(tab.label, fontSize = 10.sp, color = if (selected) BeeText else BeeText2)
    }
}

@Composable
private fun ExactHome(vm: FinanceViewModel) {
    val rows by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val monthKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val monthRows = rows.filter { it.deletedAt == null && it.localDate.startsWith(monthKey) }
    val income = monthRows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    val expense = monthRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val visible = rows.filter { tx ->
        if (tx.deletedAt != null) false else {
            val p = presentTransaction(tx, accounts)
            query.isBlank() || listOfNotNull(p.title, p.accountLine, tx.item, tx.note, tx.localDate)
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
    }
    val grouped = visible.groupBy { it.localDate }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding()
                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 14.dp),
            ) {
                Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("蜜蜂账本", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeText, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Default.Search, "搜索", tint = BeeText, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { vm.syncNow() }) { Icon(Icons.Default.Sync, "同步", tint = BeeText, modifier = Modifier.size(21.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.width(74.dp)) {
                        Text("${today.year}年", fontSize = 11.sp, color = BeeText.copy(alpha = .65f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${today.monthValue}月", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeText)
                            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(17.dp), tint = BeeText)
                        }
                    }
                    ExactSummary("收入", income, Modifier.weight(1f))
                    ExactSummary("支出", expense, Modifier.weight(1f))
                    ExactSummary("结余", income - expense, Modifier.weight(1f))
                }
            }
        }
        if (searchOpen) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("transaction_search"),
                    placeholder = { Text("搜索") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空") } },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeePrimaryDark,
                        unfocusedBorderColor = BeeBorder,
                        focusedContainerColor = BeeBg,
                        unfocusedContainerColor = BeeBg,
                    ),
                )
            }
        } else {
            item { Box(Modifier.size(1.dp).testTag("transaction_search")) }
        }
        if (grouped.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, null, tint = Color(0xFFC4C4C4), modifier = Modifier.size(50.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("暂无账单", color = BeeText2)
                }
            }
        } else {
            grouped.forEach { (date, dayRows) ->
                item(key = "date-$date") { ExactDateHeader(date, dayRows) }
                items(dayRows, key = { it.id }) { tx ->
                    ExactTransactionRow(tx, accounts, categories)
                    HorizontalDivider(color = BeeBorder, modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ExactSummary(label: String, cents: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(label, fontSize = 11.sp, color = BeeText.copy(alpha = .62f))
        Text(MoneyParser.formatPlain(cents), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BeeText, maxLines = 1)
    }
}

@Composable
private fun ExactDateHeader(date: String, rows: List<TransactionEntity>) {
    val expense = rows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val income = rows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    Row(
        Modifier.fillMaxWidth().height(30.dp).background(BeeBg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, fontSize = 11.sp, color = BeeText2, modifier = Modifier.weight(1f))
        Text("支出 ${MoneyParser.formatPlain(expense)}  收入 ${MoneyParser.formatPlain(income)}", fontSize = 11.sp, color = BeeText2)
    }
}

@Composable
private fun ExactTransactionRow(tx: TransactionEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>) {
    val category = categories.firstOrNull { it.id == tx.categoryId }?.name ?: if (tx.transactionType == "transfer") "转账" else "其他"
    val p = presentTransaction(tx, accounts, category)
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(exactCategoryTint(category), CircleShape), contentAlignment = Alignment.Center) {
            Icon(exactCategoryIcon(category, tx.transactionType), null, tint = Color(0xFF5F5968), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = BeeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(listOfNotNull(category, p.accountLine).joinToString(" · "), fontSize = 11.sp, color = BeeText2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when (tx.transactionType) {
                "expense" -> "-${MoneyParser.formatPlain(tx.amountCents)}"
                "income" -> "+${MoneyParser.formatPlain(tx.amountCents)}"
                else -> MoneyParser.formatPlain(tx.amountCents)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (tx.transactionType == "income") BeeGreen else BeeText,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactTransactionEditor(
    vm: FinanceViewModel,
    sharedText: String?,
    initialTransactionType: String?,
    onClose: () -> Unit,
) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var type by remember(initialTransactionType) {
        mutableStateOf(when (initialTransactionType) { "income" -> TransactionType.INCOME; "transfer" -> TransactionType.TRANSFER; else -> TransactionType.EXPENSE })
    }
    var selectedCategory by remember(type) { mutableStateOf<CategoryEntity?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    val relevant = categories.filter { it.categoryType == type.wire }.take(16)

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(BeePrimary).padding(start = 8.dp, end = 8.dp, top = 4.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(58.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    ExactEditorTab("支出", type == TransactionType.EXPENSE) { type = TransactionType.EXPENSE }
                    ExactEditorTab("收入", type == TransactionType.INCOME) { type = TransactionType.INCOME }
                    ExactEditorTab("转账", type == TransactionType.TRANSFER) { type = TransactionType.TRANSFER }
                }
                TextButton(onClick = onClose, modifier = Modifier.width(58.dp)) { Text("取消", color = BeeText, fontSize = 14.sp) }
            }
        }

        if (type == TransactionType.TRANSFER) {
            ExactTransferEditor(vm, accounts, onDone = onClose)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(relevant.chunked(4)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEachIndexed { index, cat ->
                            ExactCategoryChoice(
                                category = cat,
                                modifier = Modifier.weight(1f).testTag(if (index == 0 && relevant.firstOrNull()?.id == cat.id) "entry_first_category" else "entry_category_${cat.id}"),
                                onClick = { selectedCategory = cat; sheetVisible = true },
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { }) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(6.dp)); Text("分类管理", color = BeePrimaryDark, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (sheetVisible && selectedCategory != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            ExactAmountSheet(
                vm = vm,
                type = type,
                category = selectedCategory,
                accounts = accounts,
                sharedText = sharedText,
                onSaved = { sheetVisible = false; onClose() },
            )
        }
    }
}

@Composable
private fun ExactEditorTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, color = BeeText, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(26.dp).height(2.dp).background(if (selected) BeeText else Color.Transparent))
    }
}

@Composable
private fun ExactCategoryChoice(category: CategoryEntity, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(exactCategoryTint(category.name), CircleShape), contentAlignment = Alignment.Center) {
            Icon(exactCategoryIcon(category.name, category.categoryType), null, tint = BeeText, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(category.name, fontSize = 12.sp, color = BeeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExactAmountSheet(
    vm: FinanceViewModel,
    type: TransactionType,
    category: CategoryEntity?,
    accounts: List<AccountEntity>,
    sharedText: String?,
    onSaved: () -> Unit,
) {
    var amount by remember(sharedText) { mutableStateOf(exactExtractAmount(sharedText.orEmpty())) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var note by remember(sharedText) { mutableStateOf(sharedText?.take(100).orEmpty()) }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            if (amount.isBlank()) "0" else amount,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp).testTag("quick_amount"),
            textAlign = TextAlign.End,
            fontSize = 34.sp,
            fontWeight = FontWeight.Medium,
            color = BeeText,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExactAccountPill(accounts, accountId, Modifier.weight(1f)) { accountId = it }
            Surface(shape = RoundedCornerShape(14.dp), color = BeeBg, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(15.dp), tint = BeeText2); Spacer(Modifier.width(6.dp)); Text("今天", fontSize = 12.sp, color = BeeText)
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            placeholder = { Text("备注") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = BeeBg,
                unfocusedContainerColor = BeeBg,
            ),
        )
        ExactKeypad(
            onKey = { key ->
                amount = when (key) {
                    "⌫" -> amount.dropLast(1)
                    "." -> if (amount.contains('.')) amount else if (amount.isBlank()) "0." else "$amount."
                    else -> if (amount.contains('.') && amount.substringAfter('.').length >= 2) amount else (amount + key).take(12)
                }
            },
            onDone = {
                vm.save(type, amount, accountId, null, category?.id, null, note.ifBlank { null })
                onSaved()
            },
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && accounts.isNotEmpty(),
        )
    }
}

@Composable
private fun ExactAccountPill(accounts: List<AccountEntity>, selectedId: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(Modifier.fillMaxWidth().clickable { open = true }, shape = RoundedCornerShape(14.dp), color = BeeBg) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(15.dp), tint = BeeText2); Spacer(Modifier.width(6.dp)); Text(accounts.firstOrNull { it.id == selectedId }?.name ?: "账户", fontSize = 12.sp, color = BeeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account.id); open = false }) }
        }
    }
}

@Composable
private fun ExactKeypad(onKey: (String) -> Unit, onDone: () -> Unit, enabled: Boolean) {
    val rows = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf(".", "0", "⌫"))
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key ->
                        Surface(Modifier.weight(1f).height(50.dp).clickable { onKey(key) }, shape = RoundedCornerShape(5.dp), color = Color.White, shadowElevation = 1.dp) {
                            Box(contentAlignment = Alignment.Center) { Text(key, fontSize = 21.sp, color = BeeText, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("今天", "+", "−").forEach { label ->
                Surface(Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(5.dp), color = Color.White, shadowElevation = 1.dp) {
                    Box(contentAlignment = Alignment.Center) { Text(label, fontSize = if (label == "今天") 11.sp else 21.sp, color = BeeText) }
                }
            }
            Button(
                onClick = onDone,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("quick_save"),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeePrimary, contentColor = BeeText, disabledContainerColor = Color(0xFFE4E4E4)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("完成", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ExactTransferEditor(vm: FinanceViewModel, accounts: List<AccountEntity>, onDone: () -> Unit) {
    var from by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var to by remember(accounts) { mutableStateOf(accounts.drop(1).firstOrNull()?.id) }
    var amount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("转账", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExactAccountPill(accounts, from, Modifier.weight(1f)) { from = it }
            ExactAccountPill(accounts.filter { it.id != from }, to, Modifier.weight(1f)) { to = it }
        }
        Text(if (amount.isBlank()) "¥0.00" else "¥$amount", Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 32.sp, color = BeeText)
        Spacer(Modifier.weight(1f))
        ExactKeypad(
            onKey = { key -> amount = when (key) { "⌫" -> amount.dropLast(1); "." -> if (amount.contains('.')) amount else if (amount.isBlank()) "0." else "$amount."; else -> (amount + key).take(12) } },
            onDone = { vm.save(TransactionType.TRANSFER, amount, from, to, null, null, null); onDone() },
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && from != null && to != null,
        )
    }
}

@Composable
private fun ExactAnalytics(vm: FinanceViewModel) {
    val rows by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val now = LocalDate.now()
    val month = now.toString().take(7)
    val expenses = rows.filter { it.deletedAt == null && it.transactionType == "expense" && it.localDate.startsWith(month) }
    val total = expenses.sumOf { it.amountCents }
    val ranked = expenses.groupBy { it.categoryId }.map { (id, list) -> (categories.firstOrNull { it.id == id }?.name ?: "其他") to list.sumOf { it.amountCents } }.sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Column(Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().padding(16.dp)) {
                Text("图表分析", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeText)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(BeePrimaryDark.copy(alpha = .3f), RoundedCornerShape(20.dp)).padding(3.dp)) {
                    ExactSegment("月", true, Modifier.weight(1f)); ExactSegment("年", false, Modifier.weight(1f)); ExactSegment("全部", false, Modifier.weight(1f))
                }
            }
        }
        item { Column(Modifier.padding(18.dp)) { Text("总支出", fontSize = 12.sp, color = BeeText2); Text(MoneyParser.formatCny(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BeeText); Text("日均 ${MoneyParser.formatCny(if (now.dayOfMonth > 0) total / now.dayOfMonth else 0)}", fontSize = 12.sp, color = BeeText2) } }
        itemsIndexed(ranked) { index, (name, value) ->
            val ratio = if (total == 0L) 0f else value.toFloat() / total
            Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(exactCategoryTint(name), CircleShape), contentAlignment = Alignment.Center) { Icon(exactCategoryIcon(name, "expense"), null, tint = BeeText, modifier = Modifier.size(17.dp)) }
                    Spacer(Modifier.width(10.dp)); Text("${index + 1}  $name", modifier = Modifier.weight(1f), color = BeeText); Text("${(ratio * 100).toInt()}%", fontSize = 12.sp, color = BeeText2); Spacer(Modifier.width(10.dp)); Text(MoneyParser.formatPlain(value), color = BeeText)
                }
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp).height(3.dp), color = BeePrimaryDark, trackColor = BeeBorder)
            }
        }
    }
}

@Composable
private fun ExactSegment(label: String, selected: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = if (selected) Color.White else Color.Transparent) { Text(label, Modifier.padding(vertical = 7.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = BeeText, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun ExactAccounts(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsState(); val ledgers by vm.ledgers.collectAsState(); val selected by vm.selectedLedgerId.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(BeeBg)) {
        item { Column(Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().padding(16.dp)) { Text("账本", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeText); Spacer(Modifier.height(12.dp)); Text(ledgers.firstOrNull { it.id == selected }?.name ?: "默认账本", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BeeText); Text("${accounts.size} 个账户", fontSize = 12.sp, color = BeeText.copy(alpha = .62f)) } }
        item { Text("账户", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 13.sp, color = BeeText2) }
        items(accounts) { account ->
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(BeePrimary.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = BeeText, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(account.name, color = BeeText, fontWeight = FontWeight.Medium); Text(account.accountType, fontSize = 11.sp, color = BeeText2) }; Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB2B2B2))
            }
            HorizontalDivider(color = BeeBorder, modifier = Modifier.padding(start = 68.dp))
        }
    }
}

@Composable
private fun ExactMine(vm: FinanceViewModel) {
    val context = LocalContext.current; val pending by vm.pendingSync.collectAsState(); val conflicts by vm.conflictCount.collectAsState(); val ledgers by vm.ledgers.collectAsState(); val rows by vm.transactions.collectAsState(); val accounts by vm.accounts.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(BeeBg)) {
        item { Column(Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Color.White.copy(alpha = .58f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Hive, null, tint = BeeText) }; Spacer(Modifier.width(12.dp)); Column { Text("LifeTrace 记账", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BeeText); Text("本地优先 · 云端同步", fontSize = 11.sp, color = BeeText.copy(alpha = .62f)) } }; Row(Modifier.fillMaxWidth().padding(top = 16.dp)) { ExactMineMetric("${ledgers.size}", "账本", Modifier.weight(1f)); ExactMineMetric("${rows.count { it.deletedAt == null }}", "账单", Modifier.weight(1f)); ExactMineMetric("${accounts.size}", "账户", Modifier.weight(1f)) } } }
        item { Spacer(Modifier.height(10.dp)) }
        item { Column(Modifier.background(Color.White)) { ExactMenu(Icons.Default.Sync, "同步", "待上传 $pending · 冲突 $conflicts") { vm.syncNow() }; ExactMenu(Icons.Default.AccountBalanceWallet, "记账管理", "账本、账户、分类、预算") { context.startActivity(Intent(context, BookkeepingManagementActivity::class.java)) }; ExactMenu(Icons.Default.UploadFile, "账单导入", "CSV / XLSX") { context.startActivity(Intent(context, BillImportActivity::class.java)) }; ExactMenu(Icons.Default.AutoAwesome, "智能记账", "截图识别与 AI 设置") { context.startActivity(Intent(context, AiSettingsActivity::class.java)) } } }
        item { Spacer(Modifier.height(10.dp)) }
        item { Column(Modifier.background(Color.White)) { ExactMenu(Icons.Default.Cloud, "服务器", vm.baseUrl()) {}; ExactMenu(Icons.Default.Info, "关于", "LifeTrace Finance") {} } }
    }
}

@Composable
private fun ExactMineMetric(value: String, label: String, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BeeText); Text(label, fontSize = 11.sp, color = BeeText.copy(alpha = .62f)) } }

@Composable
private fun ExactMenu(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = BeePrimaryDark, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, color = BeeText, fontSize = 15.sp); Text(subtitle, color = BeeText2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB5B5B5), modifier = Modifier.size(19.dp)) }; HorizontalDivider(color = BeeBorder, modifier = Modifier.padding(start = 52.dp))
}

private fun exactCategoryTint(name: String): Color = when {
    name.contains("餐") || name.contains("食") -> Color(0xFFFFF1C9)
    name.contains("交通") || name.contains("车") -> Color(0xFFDFF2FF)
    name.contains("购") -> Color(0xFFFFE4E8)
    name.contains("医") -> Color(0xFFE5F5EA)
    name.contains("学") || name.contains("教育") -> Color(0xFFECE7FF)
    else -> Color(0xFFF1EFF5)
}

private fun exactCategoryIcon(name: String, type: String): ImageVector = when {
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

private fun exactExtractAmount(text: String): String = Regex("(?:￥|¥)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*元?").find(text)?.groupValues?.getOrNull(1).orEmpty()
