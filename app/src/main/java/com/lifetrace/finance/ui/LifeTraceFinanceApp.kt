package com.lifetrace.finance.ui

import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.CategoryEntity
import com.lifetrace.finance.data.ConflictEntity
import com.lifetrace.finance.data.TransactionEntity
import java.time.LocalDate

private enum class Destination(val label: String) {
    QUICK("记账"), TRANSACTIONS("账单"), INBOX("待确认"), ACCOUNTS("账户"), REPORTS("报表"), SETTINGS("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeTraceFinanceApp(vm: FinanceViewModel, initialDestination: String, sharedText: String?, initialTransactionType: String? = null) {
    var destination by remember { mutableStateOf(initialDestination.toDestination()) }
    val message by vm.message.collectAsState()
    val inbox by vm.inbox.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("LifeTrace Finance") }) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon(), contentDescription = item.label) },
                        label = { Text(if (item == Destination.INBOX && inbox.isNotEmpty()) "${item.label} ${inbox.size}" else item.label) },
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
                Destination.QUICK -> QuickEntryScreen(vm, sharedText, initialTransactionType)
                Destination.TRANSACTIONS -> TransactionsScreen(vm)
                Destination.INBOX -> InboxScreen(vm)
                Destination.ACCOUNTS -> AccountsCategoriesScreen(vm)
                Destination.REPORTS -> ReportsScreen(vm)
                Destination.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

private fun String.toDestination(): Destination = when (this) {
    "transactions" -> Destination.TRANSACTIONS
    "inbox" -> Destination.INBOX
    "accounts" -> Destination.ACCOUNTS
    "reports" -> Destination.REPORTS
    "settings" -> Destination.SETTINGS
    else -> Destination.QUICK
}

private fun Destination.icon() = when (this) {
    Destination.QUICK -> Icons.Default.AddCircle
    Destination.TRANSACTIONS -> Icons.Default.ReceiptLong
    Destination.INBOX -> Icons.Default.Inbox
    Destination.ACCOUNTS -> Icons.Default.AccountBalanceWallet
    Destination.REPORTS -> Icons.Default.BarChart
    Destination.SETTINGS -> Icons.Default.Settings
}

@Composable
private fun QuickEntryScreen(vm: FinanceViewModel, sharedText: String?, initialTransactionType: String?) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val amountFocus = remember { FocusRequester() }
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

    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("快速记账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
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
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).testTag("quick_amount"),
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
            ) { Text("保存") }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("最近账单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        items(rows, key = { it.id }) { item -> TransactionRow(item) { editing = item } }
        if (rows.isEmpty()) item { EmptyHint("暂无账单") }
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
private fun TransactionRow(item: TransactionEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.merchant ?: item.counterparty ?: item.note ?: item.transactionType, fontWeight = FontWeight.Medium)
                Text("${item.localDate} · ${item.status} · ${item.sourceType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(MoneyParser.formatCny(item.amountCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("待确认箱", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        items(rows, key = { it.id }) { item -> CandidateCard(item, categories, vm) }
        if (rows.isEmpty()) item { EmptyHint("暂无待确认账单") }
    }
}

@Composable
private fun CandidateCard(item: TransactionEntity, categories: List<CategoryEntity>, vm: FinanceViewModel) {
    val expenseCategories = categories.filter { it.categoryType == TransactionType.EXPENSE.wire }
    var categoryId by remember(item.id, expenseCategories) { mutableStateOf(item.categoryId ?: expenseCategories.firstOrNull()?.id) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(item.merchant ?: "自动捕获", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(MoneyParser.formatCny(item.amountCents), fontWeight = FontWeight.Bold)
            }
            Text("${item.sourceType} · ${item.localDate} · ${item.status}", style = MaterialTheme.typography.bodySmall)
            Selector("建议分类", expenseCategories, categoryId, { it.id }, { it.name }) { categoryId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.confirm(item.id, categoryId) }, enabled = categoryId != null) { Text("分类并确认") }
                OutlinedButton(onClick = { vm.ignore(item.id) }) { Text("忽略") }
            }
        }
    }
}

@Composable
private fun AccountsCategoriesScreen(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsState(); val categories by vm.categories.collectAsState()
    var accountName by remember { mutableStateOf("") }; var categoryName by remember { mutableStateOf("") }
    var categoryType by remember { mutableStateOf(TransactionType.EXPENSE) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("账户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        items(accounts, key = { it.id }) { account -> ListItem(headlineContent = { Text(account.name) }, supportingContent = { Text(account.accountType) }, leadingContent = { Icon(Icons.Default.AccountBalanceWallet, null) }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(accountName, { accountName = it }, Modifier.weight(1f), label = { Text("新账户") }, singleLine = true)
                Button(onClick = { if (accountName.isNotBlank()) { vm.addAccount(accountName, "other"); accountName = "" } }) { Text("添加") }
            }
        }
        item { HorizontalDivider(); Text("分类", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        items(categories, key = { it.id }) { category -> ListItem(headlineContent = { Text(category.name) }, supportingContent = { Text(category.categoryType) }) }
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
        item { Text("本月报表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item { MetricCard("支出", MoneyParser.formatCny(expense)) }
        item { MetricCard("收入", MoneyParser.formatCny(income)) }
        item { MetricCard("净现金流", MoneyParser.formatCny(income - expense)) }
        if (categoryTotals.isNotEmpty()) item { Text("支出分类 Top 5", style = MaterialTheme.typography.titleMedium) }
        items(categoryTotals.toList()) { entry ->
            val name = categories.firstOrNull { it.id == entry.key }?.name ?: "未分类"
            ListItem(headlineContent = { Text(name) }, trailingContent = { Text(MoneyParser.formatCny(entry.value)) })
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
private fun MetricCard(label: String, value: String) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(18.dp)) { Text(label, Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
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
