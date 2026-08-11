package com.lifetrace.finance.ui

import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.core.CategoryClassifier
import com.lifetrace.finance.core.CategorySuggestion
import com.lifetrace.finance.core.ClassificationCategory
import com.lifetrace.finance.core.ClassificationHistory
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.CategoryEntity
import com.lifetrace.finance.data.ConflictEntity
import com.lifetrace.finance.data.NotificationEventEntity
import com.lifetrace.finance.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Destination(val label: String) {
    HOME("首页"), TRANSACTIONS("明细"), QUICK("记账"), INBOX("待确认"), REPORTS("统计"), PROFILE("我的")
}

private val bottomDestinations = listOf(
    Destination.HOME,
    Destination.TRANSACTIONS,
    Destination.QUICK,
    Destination.REPORTS,
    Destination.PROFILE,
)

private data class AccountTypeOption(val wire: String, val label: String)

private val accountTypeOptions = listOf(
    AccountTypeOption("cash", "现金"),
    AccountTypeOption("bank", "银行卡"),
    AccountTypeOption("wechat", "微信"),
    AccountTypeOption("alipay", "支付宝"),
    AccountTypeOption("investment", "投资/理财"),
    AccountTypeOption("other", "其他"),
)

private fun accountTypeLabel(wire: String): String = accountTypeOptions.firstOrNull { it.wire == wire }?.label ?: wire

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeTraceFinanceApp(vm: FinanceViewModel, initialDestination: String, sharedText: String?, initialTransactionType: String? = null) {
    var destination by remember { mutableStateOf(initialDestination.toDestination()) }
    var quickType by remember { mutableStateOf(initialTransactionType) }
    val message by vm.message.collectAsState()
    val inbox by vm.inbox.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (destination != Destination.HOME) {
                CenterAlignedTopAppBar(
                    title = { Text(destination.label, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (destination == Destination.INBOX) {
                            IconButton(onClick = { destination = Destination.HOME }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回首页")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            if (item == Destination.QUICK) {
                                Box(
                                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.Add, contentDescription = "记一笔", tint = MaterialTheme.colorScheme.onPrimary) }
                            } else {
                                BadgedBox(badge = {
                                    if (item == Destination.HOME && inbox.isNotEmpty()) Badge { Text(inbox.size.toString()) }
                                }) { Icon(item.icon(), contentDescription = item.label) }
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            message?.let { msg ->
                Surface(color = if (msg.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Text(msg.text, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            when (destination) {
                Destination.HOME -> HomeScreen(
                    vm,
                    onQuickEntry = { type -> quickType = type; destination = Destination.QUICK },
                    onOpenInbox = { destination = Destination.INBOX },
                    onOpenTransactions = { destination = Destination.TRANSACTIONS },
                )
                Destination.QUICK -> QuickEntryScreen(vm, sharedText, quickType)
                Destination.TRANSACTIONS -> TransactionsScreen(vm)
                Destination.INBOX -> InboxScreen(vm)
                Destination.REPORTS -> ReportsScreen(vm)
                Destination.PROFILE -> ProfileScreen(vm)
            }
        }
    }
}

private fun String.toDestination(): Destination = when (this) {
    "transactions" -> Destination.TRANSACTIONS
    "inbox" -> Destination.INBOX
    "reports" -> Destination.REPORTS
    "accounts", "settings" -> Destination.PROFILE
    "quick" -> Destination.QUICK
    else -> Destination.HOME
}

private fun Destination.icon() = when (this) {
    Destination.HOME -> Icons.Default.Home
    Destination.QUICK -> Icons.Default.Add
    Destination.TRANSACTIONS -> Icons.Default.ReceiptLong
    Destination.INBOX -> Icons.Default.Inbox
    Destination.REPORTS -> Icons.Default.BarChart
    Destination.PROFILE -> Icons.Default.Person
}

@Composable
private fun HomeScreen(
    vm: FinanceViewModel,
    onQuickEntry: (String) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenTransactions: () -> Unit,
) {
    val transactions by vm.transactions.collectAsState()
    val inbox by vm.inbox.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val now = LocalDate.now()
    val month = now.toString().take(7)
    val monthRows = transactions.filter { it.deletedAt == null && it.status == "confirmed" && it.localDate.startsWith(month) }
    val expense = monthRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val income = monthRows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    val recentRows = transactions.filter { it.deletedAt == null }.take(4)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LifeTrace", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${now.format(DateTimeFormatter.ofPattern("M月d日"))} · 把每一笔花费变成线索",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column {
                        Text("本月结余", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                        Text(
                            MoneyParser.formatCny(income - expense),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Row {
                        SummaryMetric("收入", MoneyParser.formatCny(income), Modifier.weight(1f))
                        SummaryMetric("支出", MoneyParser.formatCny(expense), Modifier.weight(1f))
                        SummaryMetric("账户", "${accounts.size} 个", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeQuickAction("记支出", Icons.Default.Remove, Modifier.weight(1f)) { onQuickEntry("expense") }
                HomeQuickAction("记收入", Icons.Default.Add, Modifier.weight(1f)) { onQuickEntry("income") }
                HomeQuickAction("转账", Icons.Default.SwapHoriz, Modifier.weight(1f)) { onQuickEntry("transfer") }
            }
        }
        if (inbox.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenInbox),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${inbox.size} 笔账单等待确认", fontWeight = FontWeight.SemiBold)
                            Text("自动捕获与导入记录需要补充分类", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "查看待确认账单")
                    }
                }
            }
        }
        item { SectionTitle("最近明细", "查看全部", onOpenTransactions) }
        if (recentRows.isEmpty()) {
            item { EmptyHint("还没有账单，先记下第一笔吧") }
        } else {
            items(recentRows, key = { it.id }) { item ->
                val category = categories.firstOrNull { it.id == item.categoryId }?.name
                CompactTransactionRow(item, category)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
    }
}

@Composable
private fun HomeQuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun CompactTransactionRow(item: TransactionEntity, category: String?) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    if (item.transactionType == "income") Icons.Default.SouthWest else Icons.Default.NorthEast,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (item.transactionType == "income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.merchant ?: item.counterparty ?: category ?: "未分类账单", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${category ?: "未分类"} · ${item.localDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${if (item.transactionType == "income") "+" else "−"}${MoneyParser.formatCny(item.amountCents)}",
                fontWeight = FontWeight.SemiBold,
                color = if (item.transactionType == "income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ProfileScreen(vm: FinanceViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("账户与分类") }, leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) }, modifier = Modifier.weight(1f))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("同步与安全") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, modifier = Modifier.weight(1f))
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) AccountsCategoriesScreen(vm) else SettingsScreen(vm)
        }
    }
}

@Composable
private fun QuickEntryScreen(vm: FinanceViewModel, sharedText: String?, initialTransactionType: String?) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val amountFocus = remember { FocusRequester() }
    var amountFieldAttached by remember { mutableStateOf(false) }
    var focusRequested by remember { mutableStateOf(false) }
    var type by remember(initialTransactionType) {
        mutableStateOf(
            when (initialTransactionType) {
                "income" -> TransactionType.INCOME
                "transfer" -> TransactionType.TRANSFER
                else -> TransactionType.EXPENSE
            },
        )
    }
    var amount by remember(sharedText) { mutableStateOf(sharedText?.let(::extractSharedAmount).orEmpty()) }
    var merchant by remember { mutableStateOf("") }
    var note by remember(sharedText) { mutableStateOf(sharedText?.take(160).orEmpty()) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember(type, categories) { mutableStateOf(categories.firstOrNull { it.categoryType == type.wire }?.id) }

    LaunchedEffect(amountFieldAttached) {
        if (amountFieldAttached && !focusRequested) {
            focusRequested = true
            amountFocus.requestFocus()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今天记一笔", style = MaterialTheme.typography.headlineSmall)
                Text("先填金额，其他信息可以稍后补充", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("支出", type == TransactionType.EXPENSE, Modifier.weight(1f)) { type = TransactionType.EXPENSE }
                TypeChip("收入", type == TransactionType.INCOME, Modifier.weight(1f)) { type = TransactionType.INCOME }
                TypeChip("转账", type == TransactionType.TRANSFER, Modifier.weight(1f)) { type = TransactionType.TRANSFER }
            }
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(amountFocus)
                    .onGloballyPositioned { amountFieldAttached = true }
                    .testTag("quick_amount"),
                label = { Text("金额") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium,
            )
        }
        item { Selector("账户", accounts, accountId, { it.id }, { it.name }) { accountId = it } }
        if (type == TransactionType.TRANSFER) {
            item { Selector("转入账户", accounts.filter { it.id != accountId }, toAccountId, { it.id }, { it.name }) { toAccountId = it } }
        } else {
            item { Selector("分类", categories.filter { it.categoryType == type.wire }, categoryId, { it.id }, { it.name }) { categoryId = it } }
        }
        item { OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("商户/对方（可选）") }, singleLine = true) }
        item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, maxLines = 2) }
        item {
            Button(
                onClick = {
                    vm.save(type, amount, accountId, toAccountId, categoryId, merchant.ifBlank { null }, note.ifBlank { null })
                    amount = ""; merchant = ""; note = ""
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("quick_save"),
                enabled = accounts.isNotEmpty(),
            ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("保存账单") }
        }
        item { Text("保存先写入本机数据库和 Outbox，不等待网络。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = modifier)
}

private fun extractSharedAmount(text: String): String =
    Regex("(?:￥|¥)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*元?").find(text)?.groupValues?.getOrNull(1).orEmpty()

@Composable
private fun <T> Selector(label: String, values: List<T>, selected: String?, id: (T) -> String, title: (T) -> String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：${values.firstOrNull { id(it) == selected }?.let(title) ?: "请选择"}")
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(title(value)) }, onClick = { onSelect(id(value)); open = false }) }
        }
    }
}

@Composable
private fun TransactionsScreen(vm: FinanceViewModel) {
    val rows by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val filteredRows = remember(rows, query, typeFilter) {
        val needle = query.trim()
        rows.filter { item ->
            val typeMatches = typeFilter == null || item.transactionType == typeFilter
            val searchMatches = needle.isBlank() || listOfNotNull(
                item.merchant,
                item.counterparty,
                item.item,
                item.note,
                item.localDate,
                MoneyParser.formatPlain(item.amountCents),
                MoneyParser.formatCny(item.amountCents),
            ).any { it.contains(needle, ignoreCase = true) }
            typeMatches && searchMatches
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("最近账单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("transaction_search"),
                label = { Text("搜索商户、备注、日期或金额") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "清空搜索") }
                    }
                },
                singleLine = true,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("全部") })
                FilterChip(selected = typeFilter == "expense", onClick = { typeFilter = "expense" }, label = { Text("支出") })
                FilterChip(selected = typeFilter == "income", onClick = { typeFilter = "income" }, label = { Text("收入") })
                FilterChip(selected = typeFilter == "transfer", onClick = { typeFilter = "transfer" }, label = { Text("转账") })
            }
        }
        item {
            Text(
                "显示 ${filteredRows.size} / ${rows.size} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(filteredRows, key = { it.id }) { item ->
            TransactionRow(item, categories.firstOrNull { it.id == item.categoryId }?.name) { editing = item }
        }
        if (filteredRows.isEmpty()) item { EmptyHint(if (rows.isEmpty()) "暂无账单" else "没有匹配的账单") }
    }
    editing?.let { item ->
        EditTransactionDialog(
            item,
            categories,
            onDismiss = { editing = null },
            onSave = { amount, categoryId, merchant, note -> vm.updateTransaction(item.id, amount, categoryId, merchant, note); editing = null },
            onDelete = { vm.deleteTransaction(item.id); editing = null },
        )
    }
}

@Composable
private fun TransactionRow(item: TransactionEntity, categoryName: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    if (item.transactionType == "income") Icons.Default.SouthWest else if (item.transactionType == "transfer") Icons.Default.SwapHoriz else Icons.Default.NorthEast,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (item.transactionType == "income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.merchant ?: item.counterparty ?: item.note ?: categoryName ?: "未分类账单", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${categoryName ?: "未分类"} · ${item.localDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${if (item.transactionType == "income") "+" else if (item.transactionType == "expense") "−" else ""}${MoneyParser.formatCny(item.amountCents)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (item.transactionType == "income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EditTransactionDialog(item: TransactionEntity, categories: List<CategoryEntity>, onDismiss: () -> Unit, onSave: (String, String?, String?, String?) -> Unit, onDelete: () -> Unit) {
    var amount by remember { mutableStateOf(MoneyParser.formatPlain(item.amountCents)) }
    var merchant by remember { mutableStateOf(item.merchant.orEmpty()) }
    var note by remember { mutableStateOf(item.note.orEmpty()) }
    var categoryId by remember { mutableStateOf(item.categoryId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (item.transactionType != "transfer") Selector("分类", categories.filter { it.categoryType == item.transactionType }, categoryId, { it.id }, { it.name }) { categoryId = it }
                OutlinedTextField(merchant, { merchant = it }, label = { Text("商户/对方") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除账单") }
            }
        },
        confirmButton = { Button(onClick = { onSave(amount, categoryId, merchant.ifBlank { null }, note.ifBlank { null }) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun InboxScreen(vm: FinanceViewModel) {
    val rows by vm.inbox.collectAsState()
    val categories by vm.categories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val notificationEvents by vm.notificationEvents.collectAsState()
    val classifierCategories = remember(categories) {
        categories.map { ClassificationCategory(it.id, it.name, it.categoryType) }
    }
    val history = remember(transactions) {
        transactions.filter { it.status == "confirmed" && it.categoryId != null }
            .map { ClassificationHistory(it.merchant, it.counterparty, it.item, requireNotNull(it.categoryId)) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column {
                Text("待分类账单", style = MaterialTheme.typography.headlineSmall)
                Text("建议完全在本机计算，确认一次后会记住该商户", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(rows, key = { it.id }) { item ->
            val suggestion = CategoryClassifier.suggest(
                transactionType = item.transactionType,
                merchant = item.merchant,
                counterparty = item.counterparty,
                item = item.item,
                note = item.note,
                categories = classifierCategories,
                history = history.filter { it.categoryId != item.categoryId },
            )
            CandidateCard(
                item = item,
                categories = categories,
                suggestion = suggestion,
                notificationEvent = notificationEvents.firstOrNull { it.transactionId == item.id },
                vm = vm,
            )
        }
        if (rows.isEmpty()) item { EmptyHint("暂无待确认账单") }
    }
}

@Composable
private fun CandidateCard(
    item: TransactionEntity,
    categories: List<CategoryEntity>,
    suggestion: CategorySuggestion?,
    notificationEvent: NotificationEventEntity?,
    vm: FinanceViewModel,
) {
    val matchingCategories = categories.filter { it.categoryType == item.transactionType }
    var categoryId by remember(item.id, matchingCategories, suggestion) { mutableStateOf(item.categoryId ?: suggestion?.categoryId) }
    val sourceLabel = candidateSourceLabel(item.sourceType, notificationEvent?.sourcePackage)
    val title = item.merchant ?: item.counterparty ?: item.item ?: sourceLabel
    val time = remember(item.occurredAt) {
        runCatching {
            Instant.parse(item.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        }.getOrElse { item.localDate }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(candidateSourceIcon(notificationEvent?.sourcePackage), contentDescription = null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$sourceLabel · $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(MoneyParser.formatCny(item.amountCents), style = MaterialTheme.typography.titleMedium)
            }
            if (item.merchant == null && item.counterparty == null && item.item == null) {
                Text(
                    buildString {
                        append("原始${if (notificationEvent != null) "通知" else "账单"}未提供商户信息")
                        notificationEvent?.accountHint?.let { append(" · 账户尾号 $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (suggestion != null) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("建议：${suggestion.categoryName}", fontWeight = FontWeight.SemiBold)
                            Text("${suggestion.reason} · ${(suggestion.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Selector("分类", matchingCategories, categoryId, { it.id }, { it.name }) { categoryId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.confirm(item.id, categoryId) }, enabled = categoryId != null) { Text("分类并确认") }
                OutlinedButton(onClick = { vm.ignore(item.id) }) { Text("忽略") }
            }
        }
    }
}

@Composable
private fun AccountsCategoriesScreen(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var accountName by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("other") }
    var categoryName by remember { mutableStateOf("") }
    var categoryType by remember { mutableStateOf(TransactionType.EXPENSE) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("账户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        items(accounts, key = { it.id }) { account ->
            ListItem(
                headlineContent = { Text(account.name) },
                supportingContent = { Text(accountTypeLabel(account.accountType)) },
                leadingContent = { Icon(Icons.Default.AccountBalanceWallet, null) },
                trailingContent = {
                    IconButton(onClick = { vm.archiveAccount(account.id) }, enabled = accounts.size > 1) {
                        Icon(Icons.Default.Archive, contentDescription = if (accounts.size > 1) "归档账户" else "至少保留一个账户")
                    }
                },
            )
        }
        item { Selector("账户类型", accountTypeOptions, accountType, { it.wire }, { it.label }) { accountType = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(accountName, { accountName = it }, Modifier.weight(1f), label = { Text("新账户") }, singleLine = true)
                Button(onClick = { if (accountName.isNotBlank()) { vm.addAccount(accountName, accountType); accountName = "" } }) { Text("添加") }
            }
        }
        item {
            Text(
                "归档账户不会删除历史账单，只会从后续记账选择中隐藏；至少保留一个可用账户。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { HorizontalDivider(); Text("分类", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        items(categories, key = { it.id }) { category ->
            ListItem(
                headlineContent = { Text(category.name) },
                supportingContent = { Text(if (category.categoryType == "income") "收入" else "支出") },
                trailingContent = {
                    IconButton(onClick = { vm.archiveCategory(category.id) }) {
                        Icon(Icons.Default.Archive, contentDescription = "归档分类")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("支出", categoryType == TransactionType.EXPENSE, Modifier.weight(1f)) { categoryType = TransactionType.EXPENSE }
                TypeChip("收入", categoryType == TransactionType.INCOME, Modifier.weight(1f)) { categoryType = TransactionType.INCOME }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(categoryName, { categoryName = it }, Modifier.weight(1f), label = { Text("新分类") }, singleLine = true)
                Button(onClick = { if (categoryName.isNotBlank()) { vm.addCategory(categoryName, categoryType); categoryName = "" } }) { Text("添加") }
            }
        }
        item {
            Text(
                "归档分类不会修改历史账单，只会从后续分类选择中隐藏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportsScreen(vm: FinanceViewModel) {
    val tx by vm.transactions.collectAsState()
    val now = LocalDate.now(); val month = now.toString().take(7)
    val confirmed = tx.filter { it.deletedAt == null && it.status == "confirmed" && it.localDate.startsWith(month) }
    val expense = confirmed.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val income = confirmed.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    val categoryTotals = confirmed.filter { it.transactionType == "expense" }.groupBy { it.categoryId }
        .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }.entries.sortedByDescending { it.value }.take(5)
    val categories by vm.categories.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("${now.monthValue}月收支分析", style = MaterialTheme.typography.headlineSmall)
                Text("只统计已经确认的账单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("净现金流", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                        Text(MoneyParser.formatCny(income - expense), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Row {
                        ReportMetric("收入", income, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        ReportMetric("支出", expense, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }
                }
            }
        }
        if (categoryTotals.isNotEmpty()) item { SectionTitle("支出分类") }
        items(categoryTotals.toList()) { entry ->
            val name = categories.firstOrNull { it.id == entry.key }?.name ?: "未分类"
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(MoneyParser.formatCny(entry.value))
                    }
                    LinearProgressIndicator(
                        progress = { (entry.value.toFloat() / expense.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        "占本月支出 ${((entry.value.toDouble() / expense.coerceAtLeast(1)) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("预算 / 订阅", fontWeight = FontWeight.SemiBold)
                    Text("等待上游正式 finance.* Contract", style = MaterialTheme.typography.bodySmall)
                    Text("本版不创建 Android 私有同步实体，避免后续协议冲突。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(MoneyParser.formatCny(value), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun candidateSourceLabel(sourceType: String, sourcePackage: String?): String = when {
    sourcePackage == "com.tencent.mm" || sourceType.contains("wechat", true) -> "微信支付"
    sourcePackage == "com.eg.android.AlipayGphone" || sourceType.contains("alipay", true) -> "支付宝"
    sourcePackage == "com.unionpay" || sourceType.contains("unionpay", true) -> "云闪付"
    sourcePackage?.startsWith("com.bank.") == true || sourceType.contains("bank", true) -> "银行通知"
    sourceType.contains("import", true) -> "账单导入"
    sourceType == "notification" -> "支付通知"
    else -> "${sourceType.replace('_', ' ')}记录"
}

private fun candidateSourceIcon(sourcePackage: String?) = when (sourcePackage) {
    "com.tencent.mm" -> Icons.Default.Chat
    "com.eg.android.AlipayGphone" -> Icons.Default.AccountBalanceWallet
    "com.unionpay" -> Icons.Default.CreditCard
    else -> Icons.Default.Notifications
}

@Composable
private fun SettingsScreen(vm: FinanceViewModel) {
    val authenticated by vm.authenticated.collectAsState()
    val pending by vm.pendingSync.collectAsState()
    val conflicts by vm.conflicts.collectAsState()
    val state by vm.syncState.collectAsState()
    val lastNotificationCapture by vm.lastNotificationCapture.collectAsState()
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var baseUrl by remember { mutableStateOf(vm.baseUrl()) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("账户与同步", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        if (!authenticated) {
            item { OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true) }
            item { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) }
            item { Button(onClick = { vm.login(email, password) }, Modifier.fillMaxWidth()) { Text("登录") } }
        } else {
            item { OutlinedButton(onClick = { vm.logout() }, Modifier.fillMaxWidth()) { Text("退出登录（本地数据保留）") } }
        }
        item {
            OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("LifeTrace Cloud 地址") }, singleLine = true)
            Button(onClick = { vm.setBaseUrl(baseUrl) }, Modifier.padding(top = 6.dp)) { Text("保存服务器地址") }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("同步状态", fontWeight = FontWeight.SemiBold)
                    Text("待上传：$pending · 冲突：${conflicts.size}")
                    Text("Cursor：${state?.cursor?.takeLast(12) ?: "未初始化"}")
                    Text("最近 Push：${state?.lastPushAt ?: "无"}")
                    Text("最近 Pull：${state?.lastPullAt ?: "无"}")
                    state?.lastError?.let { Text("错误：$it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::syncNow) { Text("立即同步") }
                OutlinedButton(onClick = { vm.snapshot() }) { Text("重新 Snapshot") }
            }
        }
        if (conflicts.isNotEmpty()) {
            item { Text("同步冲突", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(conflicts, key = { it.conflictId }) { conflict -> ConflictCard(conflict, vm) }
        }
        item {
            val notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("自动账单捕获", fontWeight = FontWeight.SemiBold)
                    Text("通知读取权限：${if (notificationAccess) "已开启" else "未开启"}")
                    Text("最近捕获：${lastNotificationCapture ?: "无"}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("管理通知读取权限") }
                    TextButton(onClick = { vm.clearNotificationCache() }) { Text("清空通知证据缓存") }
                }
            }
        }
        item { OutlinedButton(onClick = { vm.shareDiagnostics() }, Modifier.fillMaxWidth()) { Text("导出脱敏诊断日志") } }
        item { Text("Refresh Token 使用 Android Keystore 加密；Access Token 仅在进程内存。完整通知原文默认不持久化。", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ConflictCard(conflict: ConflictEntity, vm: FinanceViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${conflict.entityType} · ${conflict.entityId.take(8)}", fontWeight = FontWeight.Medium)
            Text("本地基线 ${conflict.baseServerVersion} / 云端 ${conflict.remoteServerVersion}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.resolveKeepLocal(conflict.conflictId) }) { Text("保留本地") }
                OutlinedButton(onClick = { vm.resolveUseRemote(conflict.conflictId) }) { Text("采用云端") }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
