package com.lifetrace.finance.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.*
import com.lifetrace.finance.importer.BillImportActivity
import java.time.LocalDate
import kotlin.math.min
import kotlinx.coroutines.delay

class BookkeepingManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1201)
        }
        setContent {
            LifeTraceTheme {
                val vm: BookkeepingManagementViewModel = viewModel()
                BookkeepingManagementScreen(
                    vm = vm,
                    onImport = { startActivity(Intent(this, BillImportActivity::class.java)) },
                    onVisionSettings = { startActivity(Intent(this, AiSettingsActivity::class.java)) },
                    onBack = ::finish,
                )
            }
        }
    }
}

private enum class ManagementTab(val label: String) {
    LEDGER("账本"), ACCOUNT("账户"), CATEGORY("分类"), TAG("标签"), BUDGET("预算"), RECURRING("周期")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookkeepingManagementScreen(
    vm: BookkeepingManagementViewModel,
    onImport: () -> Unit,
    onVisionSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val ledgers by vm.ledgers.collectAsState()
    val selectedId by vm.selectedLedgerId.collectAsState()
    val message by vm.message.collectAsState()
    var tab by remember { mutableStateOf(ManagementTab.LEDGER) }
    LaunchedEffect(message) {
        val shownMessage = message ?: return@LaunchedEffect
        delay(if (shownMessage.error) 5_000L else 3_000L)
        vm.dismissMessage(shownMessage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记账管理") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = onImport) { Text("导入") }
                    TextButton(onClick = onVisionSettings) { Text("截图") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            message?.let {
                Surface(
                    modifier = Modifier.clickable { vm.dismissMessage(it) },
                    color = if (it.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(it.text, Modifier.fillMaxWidth().padding(10.dp))
                }
            }
            if (ledgers.isNotEmpty()) {
                LedgerSelector(ledgers, selectedId, vm::selectLedger)
            }
            ScrollableTabRow(selectedTabIndex = tab.ordinal) {
                ManagementTab.entries.forEach { item ->
                    Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.label) })
                }
            }
            when (tab) {
                ManagementTab.LEDGER -> LedgerTab(vm)
                ManagementTab.ACCOUNT -> AccountTab(vm)
                ManagementTab.CATEGORY -> CategoryTab(vm)
                ManagementTab.TAG -> TagTab(vm)
                ManagementTab.BUDGET -> BudgetTab(vm)
                ManagementTab.RECURRING -> RecurringTab(vm)
            }
        }
    }
}

@Composable
private fun LedgerSelector(ledgers: List<LedgerEntity>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("当前账本：${ledgers.firstOrNull { it.id == selected }?.name ?: "请选择"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ledgers.forEach { ledger ->
                DropdownMenuItem(text = { Text("${ledger.name} · ${ledger.currency}") }, onClick = { onSelect(ledger.id); open = false })
            }
        }
    }
}

@Composable
private fun LedgerTab(vm: BookkeepingManagementViewModel) {
    val ledgers by vm.ledgers.collectAsState()
    val selected by vm.selectedLedgerId.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) { Text("新建账本") }
                OutlinedButton(onClick = vm::archiveCurrentLedger, enabled = ledgers.size > 1 && selected != null, modifier = Modifier.weight(1f)) { Text("归档当前账本") }
            }
        }
        items(ledgers, key = { it.id }) { ledger ->
            ListItem(
                headlineContent = { Text(ledger.name, fontWeight = if (ledger.id == selected) FontWeight.Bold else FontWeight.Normal) },
                supportingContent = { Text("${ledger.currency} · 每月 ${ledger.monthStartDay} 日起算") },
            )
            HorizontalDivider()
        }
    }
    if (showCreate) CreateLedgerDialog(onDismiss = { showCreate = false }) { name, day ->
        vm.createLedger(name, "CNY", day); showCreate = false
    }
}

@Composable
private fun CreateLedgerDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var startDay by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建账本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("账本名称") }, singleLine = true)
                OutlinedTextField("人民币（CNY）", {}, label = { Text("币种") }, singleLine = true, enabled = false)
                OutlinedTextField(startDay, { startDay = it.filter(Char::isDigit).take(2) }, label = { Text("月度起算日 1-28") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, startDay.toIntOrNull() ?: 1) }, enabled = name.isNotBlank()) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AccountTab(vm: BookkeepingManagementViewModel) {
    val accounts by vm.accounts.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("添加账户") } }
        items(accounts, key = { it.id }) { account ->
            Card(Modifier.fillMaxWidth().clickable { editing = account }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(account.name, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(account.accountType, account.bankName, account.last4?.let { "尾号 $it" }, account.currency).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (account.creditLimitCents != null) Text("额度 ${MoneyParser.formatCny(account.creditLimitCents)} · 账单日 ${account.billingDay ?: "-"} · 还款日 ${account.paymentDueDay ?: "-"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (creating) AccountDialog(null, { creating = false }) { values ->
        vm.createAccount(values.name, values.type, "CNY", values.opening, values.bank, values.last4, values.creditLimit, values.billingDay, values.dueDay, values.note)
        creating = false
    }
    editing?.let { account ->
        AccountDialog(account, { editing = null }) { values ->
            vm.updateAccount(account, values.name, values.type, "CNY", values.opening, values.bank, values.last4, values.creditLimit, values.billingDay, values.dueDay, values.note, values.hidden)
            editing = null
        }
    }
}

private data class AccountForm(
    val name: String, val type: String, val opening: String,
    val bank: String, val last4: String, val creditLimit: String, val billingDay: String,
    val dueDay: String, val note: String, val hidden: Boolean,
)

@Composable
private fun AccountDialog(account: AccountEntity?, onDismiss: () -> Unit, onSave: (AccountForm) -> Unit) {
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.accountType ?: "other") }
    var opening by remember { mutableStateOf(account?.openingBalanceCents?.let(MoneyParser::formatPlain).orEmpty()) }
    var bank by remember { mutableStateOf(account?.bankName.orEmpty()) }
    var last4 by remember { mutableStateOf(account?.last4.orEmpty()) }
    var credit by remember { mutableStateOf(account?.creditLimitCents?.let(MoneyParser::formatPlain).orEmpty()) }
    var billing by remember { mutableStateOf(account?.billingDay?.toString().orEmpty()) }
    var due by remember { mutableStateOf(account?.paymentDueDay?.toString().orEmpty()) }
    var note by remember { mutableStateOf(account?.note.orEmpty()) }
    var hidden by remember { mutableStateOf(account?.isHidden ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "添加账户" else "高级账户设置") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true) }
                item { ChoiceSelector("账户类型", listOf("cash" to "现金", "bank" to "银行卡", "wechat" to "微信", "alipay" to "支付宝", "investment" to "投资/理财", "other" to "其他"), type) { type = it } }
                item { OutlinedTextField("人民币（CNY）", {}, label = { Text("币种") }, singleLine = true, enabled = false) }
                item { OutlinedTextField(opening, { opening = it }, label = { Text("期初余额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
                item { OutlinedTextField(bank, { bank = it }, label = { Text("银行名称") }, singleLine = true) }
                item { OutlinedTextField(last4, { last4 = it.filter(Char::isDigit).take(4) }, label = { Text("卡号尾四位") }, singleLine = true) }
                item { OutlinedTextField(credit, { credit = it }, label = { Text("信用额度") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(billing, { billing = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("账单日") }, singleLine = true)
                    OutlinedTextField(due, { due = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("还款日") }, singleLine = true)
                } }
                item { OutlinedTextField(note, { note = it }, label = { Text("备注") }) }
                if (account != null) item { Row(verticalAlignment = Alignment.CenterVertically) { Text("隐藏账户", Modifier.weight(1f)); Switch(hidden, { hidden = it }) } }
            }
        },
        confirmButton = { Button(onClick = { onSave(AccountForm(name, type, opening, bank, last4, credit, billing, due, note, hidden)) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CategoryTab(vm: BookkeepingManagementViewModel) {
    val categories by vm.categories.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Button(onClick = { creating = true }, Modifier.fillMaxWidth()) { Text("添加一级/二级分类") } }
        items(categories, key = { it.id }) { category ->
            ListItem(
                modifier = Modifier.padding(start = if (category.level == 2) 24.dp else 0.dp),
                headlineContent = { Text(if (category.level == 2) "↳ ${category.name}" else category.name) },
                supportingContent = { Text(if (category.categoryType == "income") "收入" else "支出") },
                leadingContent = { Icon(categoryManagementIcon(category.icon), contentDescription = category.icon ?: "分类") },
                trailingContent = { Row { IconButton(onClick = { vm.moveCategory(category.id, -1) }) { Icon(Icons.Default.KeyboardArrowUp, "上移") }; IconButton(onClick = { vm.moveCategory(category.id, 1) }) { Icon(Icons.Default.KeyboardArrowDown, "下移") }; TextButton(onClick = { editing = category }) { Text("编辑") }; TextButton(onClick = { vm.archiveCategory(category.id) }) { Text("归档") } } },
            )
        }
    }
    if (creating) CategoryDialog(categories, { creating = false }) { name, type, parent, icon ->
        vm.createCategory(name, type, parent, icon); creating = false
    }
    editing?.let { current -> CategoryDialog(categories, { editing = null }, current) { name, _, parent, icon -> vm.updateCategory(current.id, name, parent, icon); editing = null } }
}

@Composable
private fun CategoryDialog(categories: List<CategoryEntity>, onDismiss: () -> Unit, current: CategoryEntity? = null, onCreate: (String, TransactionType, String?, String?) -> Unit) {
    var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var type by remember(current?.id) { mutableStateOf(if (current?.categoryType == "income") TransactionType.INCOME else TransactionType.EXPENSE) }
    var parentId by remember(current?.id) { mutableStateOf(current?.parentId) }
    var icon by remember(current?.id) { mutableStateOf(current?.icon ?: "category") }
    val parents = categories.filter { it.id != current?.id && it.level == 1 && it.categoryType == type.wire }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "添加分类" else "编辑分类") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("分类名称") }, singleLine = true)
            ChoiceSelector("类型", listOf("expense" to "支出", "income" to "收入"), type.wire) { if (current == null) { type = if (it == "income") TransactionType.INCOME else TransactionType.EXPENSE; parentId = null } }
            NullableSelector("父分类（留空即一级）", parents.map { it.id to it.name }, parentId) { parentId = it }
            ChoiceSelector("图标", categoryIconOptions, icon) { icon = it }
        } },
        confirmButton = { Button(onClick = { onCreate(name, type, parentId, icon) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TagTab(vm: BookkeepingManagementViewModel) {
    val tags by vm.tags.collectAsState()
    val transactions by vm.transactions.collectAsState()
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { creating = true }, Modifier.fillMaxWidth()) { Text("添加标签") } }
        items(tags, key = { it.id }) { tag ->
            ListItem(
                leadingContent = { Surface(Modifier.size(20.dp), shape = CircleShape, color = parseTagColor(tag.color)) {} },
                headlineContent = { Text(tag.name) },
                supportingContent = { Text(tag.color ?: "默认颜色") },
                trailingContent = { Row { TextButton(onClick = { editingTag = tag }) { Text("编辑") }; TextButton(onClick = { vm.archiveTag(tag.id) }) { Text("归档") } } },
            )
        }
        item { HorizontalDivider(); Text("给最近账单分配标签", fontWeight = FontWeight.SemiBold) }
        items(transactions.take(20), key = { "tag-${it.id}" }) { tx ->
            ListItem(
                headlineContent = { Text(tx.merchant ?: tx.item ?: tx.note ?: "账单") },
                supportingContent = { Text("${tx.localDate} · ${MoneyParser.formatCny(tx.amountCents)}") },
                trailingContent = { TextButton(onClick = { editingTransaction = tx }) { Text("标签") } },
            )
        }
    }
    if (creating) TagEditDialog(null, { creating = false }) { name, color -> vm.createTag(name, color); creating = false }
    editingTag?.let { tag -> TagEditDialog(tag, { editingTag = null }) { name, color -> vm.updateTag(tag.id, name, color); editingTag = null } }
    editingTransaction?.let { tx -> TransactionTagDialog(vm, tx, tags) { editingTransaction = null } }
}

private val tagColors = listOf("#FFC928" to "蜂蜜黄", "#EF6C6C" to "珊瑚红", "#4F9DDE" to "湖蓝", "#55A86B" to "草绿", "#8B6CCF" to "紫罗兰", "#7D7D7D" to "灰色")

private fun parseTagColor(value: String?) = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value ?: "#FFC928")) }.getOrDefault(androidx.compose.ui.graphics.Color(0xFFFFC928))

@Composable
private fun TagEditDialog(current: TagEntity?, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var color by remember(current?.id) { mutableStateOf(current?.color ?: tagColors.first().first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "添加标签" else "编辑标签") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("标签名称") }, singleLine = true)
            ChoiceSelector("颜色", tagColors, color) { color = it }
        } },
        confirmButton = { Button(onClick = { onSave(name, color) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TransactionTagDialog(vm: BookkeepingManagementViewModel, tx: TransactionEntity, tags: List<TagEntity>, onDismiss: () -> Unit) {
    val assigned by vm.run { com.lifetrace.finance.AppGraph.get(getApplication()).bookkeeping.tagsForTransaction(tx.id) }
        .collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账单标签") },
        text = { Column { tags.forEach { tag ->
            val checked = assigned.any { it.id == tag.id }
            Row(Modifier.fillMaxWidth().clickable { if (checked) vm.removeTag(tx.id, tag.id) else vm.addTag(tx.id, tag.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text(tag.name)
            }
        } } },
        confirmButton = { Button(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun BudgetTab(vm: BookkeepingManagementViewModel) {
    val budgets by vm.budgets.collectAsState()
    val categories by vm.categories.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = { creating = true }, Modifier.fillMaxWidth()) { Text("添加预算") } }
        items(budgets, key = { it.id }) { budget ->
            val used = vm.budgetUsage(budget)
            val ratio = if (budget.amountCents <= 0) 0f else min(1f, used.toFloat() / budget.amountCents.toFloat())
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(categories.firstOrNull { it.id == budget.categoryId }?.name ?: "总预算", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(budget.enabled, { vm.setBudgetEnabled(budget.id, it) })
                }
                Text("${MoneyParser.formatCny(used)} / ${MoneyParser.formatCny(budget.amountCents)} · ${budget.period}", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                Text("剩余 ${MoneyParser.formatCny((budget.amountCents - used).coerceAtLeast(0))}", style = MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick = { editing = budget }) { Text("编辑") }; TextButton(onClick = { vm.archiveBudget(budget.id) }) { Text("删除") } }
            } }
        }
    }
    if (creating) BudgetDialog(categories, { creating = false }) { amount, category, period, day ->
        vm.createBudget(amount, category, period, day); creating = false
    }
    editing?.let { current -> BudgetDialog(categories, { editing = null }, current) { amount, category, period, day -> vm.updateBudget(current.id, amount, category, period, day); editing = null } }
}

private val categoryIconOptions = listOf("category" to "默认", "restaurant" to "餐饮", "car" to "交通", "shopping" to "购物", "home" to "居家", "medical" to "医疗", "school" to "教育", "sports" to "运动", "salary" to "工资", "gift" to "礼物")

private fun categoryManagementIcon(value: String?) = when (value) {
    "restaurant" -> Icons.Default.Restaurant
    "car" -> Icons.Default.DirectionsCar
    "shopping" -> Icons.Default.ShoppingBag
    "home" -> Icons.Default.Home
    "medical" -> Icons.Default.MedicalServices
    "school" -> Icons.Default.School
    "sports" -> Icons.Default.SportsSoccer
    "salary" -> Icons.Default.Payments
    "gift" -> Icons.Default.CardGiftcard
    else -> Icons.Default.Category
}

@Composable
private fun BudgetDialog(categories: List<CategoryEntity>, onDismiss: () -> Unit, current: BudgetEntity? = null, onCreate: (String, String?, String, Int) -> Unit) {
    var amount by remember(current?.id) { mutableStateOf(current?.amountCents?.let(MoneyParser::formatPlain).orEmpty()) }
    var categoryId by remember(current?.id) { mutableStateOf(current?.categoryId) }
    var period by remember(current?.id) { mutableStateOf(current?.period ?: "monthly") }
    var day by remember(current?.id) { mutableStateOf((current?.startDay ?: 1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "添加预算" else "编辑预算") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(amount, { amount = it }, label = { Text("预算金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            NullableSelector("分类（空=总预算）", categories.filter { it.categoryType == "expense" }.map { it.id to it.name }, categoryId) { categoryId = it }
            ChoiceSelector("周期", listOf("monthly" to "月", "weekly" to "周", "yearly" to "年"), period) { period = it }
            if (period == "monthly") OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("每月起算日") }, singleLine = true)
        } },
        confirmButton = { Button(onClick = { onCreate(amount, categoryId, period, day.toIntOrNull()?.coerceIn(1, 28) ?: 1) }, enabled = amount.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RecurringTab(vm: BookkeepingManagementViewModel) {
    val recurring by vm.recurring.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creating = true }, Modifier.weight(1f)) { Text("添加周期规则") }
            OutlinedButton(onClick = vm::runRecurringNow, Modifier.weight(1f)) { Text("立即检查") }
        } }
        items(recurring, key = { it.id }) { rule ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${rule.frequency} · ${MoneyParser.formatCny(rule.amountCents)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(rule.enabled, { vm.setRecurringEnabled(rule.id, it) })
                }
                Text("开始 ${rule.startDate} · 最近生成 ${rule.lastGeneratedDate ?: "无"}", style = MaterialTheme.typography.bodySmall)
                rule.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row { TextButton(onClick = { editing = rule }) { Text("编辑") }; TextButton(onClick = { vm.archiveRecurring(rule.id) }) { Text("删除") } }
            } }
        }
    }
    if (creating) RecurringDialog(accounts, categories, { creating = false }) { form ->
        vm.createRecurring(
            form.type, form.amount, form.accountId, form.toAccountId, form.categoryId, form.note,
            form.frequency, form.interval, form.startDate, form.dayOfMonth, form.dayOfWeek, form.monthOfYear, form.endDate,
        ); creating = false
    }
    editing?.let { current -> RecurringDialog(accounts, categories, { editing = null }, current) { form ->
        vm.updateRecurring(current.id, form.toViewModel()); editing = null
    } }
}

private data class RecurringForm(
    val type: TransactionType, val amount: String, val accountId: String?, val toAccountId: String?,
    val categoryId: String?, val note: String?, val frequency: String, val interval: Int,
    val startDate: LocalDate, val dayOfMonth: Int?, val dayOfWeek: Int?, val monthOfYear: Int?, val endDate: LocalDate?,
)

private fun RecurringForm.toViewModel() = RecurringFormData(type, amount, accountId, toAccountId, categoryId, note, frequency, interval, startDate, dayOfMonth, dayOfWeek, monthOfYear, endDate)

@Composable
private fun RecurringDialog(accounts: List<AccountEntity>, categories: List<CategoryEntity>, onDismiss: () -> Unit, current: RecurringTransactionEntity? = null, onCreate: (RecurringForm) -> Unit) {
    var type by remember(current?.id) { mutableStateOf(TransactionType.fromWire(current?.transactionType ?: "expense") ?: TransactionType.EXPENSE) }
    var amount by remember(current?.id) { mutableStateOf(current?.amountCents?.let(MoneyParser::formatPlain).orEmpty()) }
    var accountId by remember(current?.id) { mutableStateOf(current?.accountId ?: accounts.firstOrNull()?.id) }
    var toAccountId by remember(current?.id) { mutableStateOf(current?.toAccountId) }
    var categoryId by remember(current?.id) { mutableStateOf(current?.categoryId) }
    var note by remember(current?.id) { mutableStateOf(current?.note.orEmpty()) }
    var frequency by remember(current?.id) { mutableStateOf(current?.frequency ?: "monthly") }
    var interval by remember(current?.id) { mutableStateOf((current?.interval ?: 1).toString()) }
    var start by remember(current?.id) { mutableStateOf(current?.startDate ?: LocalDate.now().toString()) }
    var dayOfMonth by remember(current?.id) { mutableStateOf((current?.dayOfMonth ?: LocalDate.now().dayOfMonth).toString()) }
    var dayOfWeek by remember(current?.id) { mutableStateOf((current?.dayOfWeek ?: LocalDate.now().dayOfWeek.value).toString()) }
    var monthOfYear by remember(current?.id) { mutableStateOf((current?.monthOfYear ?: LocalDate.now().monthValue).toString()) }
    var end by remember(current?.id) { mutableStateOf(current?.endDate.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "添加周期记账" else "编辑周期记账") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChoiceSelector("类型", listOf("expense" to "支出", "income" to "收入", "transfer" to "转账"), type.wire) { type = TransactionType.fromWire(it) ?: TransactionType.EXPENSE } }
            item { OutlinedTextField(amount, { amount = it }, label = { Text("金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item { NullableSelector("账户", accounts.map { it.id to it.name }, accountId) { accountId = it } }
            if (type == TransactionType.TRANSFER) item { NullableSelector("转入账户", accounts.filter { it.id != accountId }.map { it.id to it.name }, toAccountId) { toAccountId = it } }
            else item { NullableSelector("分类", categories.filter { it.categoryType == type.wire }.map { it.id to it.name }, categoryId) { categoryId = it } }
            item { ChoiceSelector("频率", listOf("daily" to "每天", "weekly" to "每周", "monthly" to "每月", "yearly" to "每年"), frequency) { frequency = it } }
            item { OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, label = { Text("间隔") }, singleLine = true) }
            item { OutlinedTextField(start, { start = it }, label = { Text("开始日期 YYYY-MM-DD") }, singleLine = true) }
            if (frequency == "weekly") item { OutlinedTextField(dayOfWeek, { dayOfWeek = it.filter(Char::isDigit).take(1) }, label = { Text("星期 1-7") }, singleLine = true) }
            if (frequency == "monthly" || frequency == "yearly") item { OutlinedTextField(dayOfMonth, { dayOfMonth = it.filter(Char::isDigit).take(2) }, label = { Text("每月日期 1-31") }, singleLine = true) }
            if (frequency == "yearly") item { OutlinedTextField(monthOfYear, { monthOfYear = it.filter(Char::isDigit).take(2) }, label = { Text("月份 1-12") }, singleLine = true) }
            item { OutlinedTextField(end, { end = it }, label = { Text("结束日期（可选）") }, singleLine = true) }
            item { OutlinedTextField(note, { note = it }, label = { Text("备注") }) }
        } },
        confirmButton = { Button(onClick = {
            val startDate = runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now())
            val endDate = end.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            onCreate(RecurringForm(type, amount, accountId, toAccountId, categoryId, note.ifBlank { null }, frequency, interval.toIntOrNull()?.coerceAtLeast(1) ?: 1, startDate, dayOfMonth.toIntOrNull(), dayOfWeek.toIntOrNull(), monthOfYear.toIntOrNull(), endDate))
        }, enabled = amount.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChoiceSelector(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("$label：${options.firstOrNull { it.first == selected }?.second ?: selected}") }
        DropdownMenu(open, { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelect(option.first); open = false }) }
        }
    }
}

@Composable
private fun NullableSelector(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("$label：${options.firstOrNull { it.first == selected }?.second ?: "无"}") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("无") }, onClick = { onSelect(null); open = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelect(option.first); open = false }) }
        }
    }
}
