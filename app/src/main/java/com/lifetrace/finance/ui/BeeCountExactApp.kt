package com.lifetrace.finance.ui

import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.core.CategoryClassifier
import com.lifetrace.finance.core.ClassificationCategory
import com.lifetrace.finance.core.ClassificationHistory
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.CategoryEntity
import com.lifetrace.finance.data.TransactionEntity
import com.lifetrace.finance.importer.BillImportActivity
import com.lifetrace.finance.domain.AccountBalance
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BeePrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
private val BeePrimaryDark: Color @Composable get() = MaterialTheme.colorScheme.primary
private val BeeText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val BeeText2: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val BeeBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val BeeBorder: Color @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
private val BeeGreen: Color @Composable get() = MaterialTheme.colorScheme.secondary

private enum class ExactTab(val label: String, val icon: ImageVector) {
    Home("明细", Icons.Default.ReceiptLong),
    Analytics("图表", Icons.Default.PieChart),
    Accounts("账本", Icons.Default.AccountBalanceWallet),
    Mine("我的", Icons.Default.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var inboxOpen by remember(initialDestination) { mutableStateOf(initialDestination == "inbox") }
    var addMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val cachedImages = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "selected-bills").apply { mkdirs() }
                uris.mapNotNull { uri ->
                    runCatching {
                        val target = File(directory, "${UUID.randomUUID()}.image")
                        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
                            ?: error("无法读取图片")
                        target
                    }.getOrNull()
                }
            }
            if (cachedImages.isEmpty()) return@launch
            val graph = AppGraph.get(context)
            if (graph.aiProviderFactory.isVisionConfigured()) {
                graph.autoBilling.submitImages(cachedImages.map { android.net.Uri.fromFile(it) }, "image_picker", deleteAfter = true)
                inboxOpen = true
            } else {
                graph.pendingShare.saveAll(cachedImages.map { it.absolutePath })
                context.startActivity(Intent(context, AiSettingsActivity::class.java))
            }
        }
    }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        val shownMessage = message ?: return@LaunchedEffect
        delay(if (shownMessage.error) 5_000L else 3_000L)
        vm.dismissMessage(shownMessage)
    }

    Scaffold(
        containerColor = BeeBg,
        bottomBar = {
            if (!editorOpen && !inboxOpen) ExactFloatingBottomBar(tab, onTab = { tab = it }, onAdd = { addMenuOpen = true })
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (inboxOpen) {
                ExactInbox(vm, onBack = { inboxOpen = false })
            } else if (editorOpen) {
                ExactTransactionEditor(
                    vm = vm,
                    sharedText = sharedText,
                    initialTransactionType = initialTransactionType,
                    onClose = { editorOpen = false },
                )
            } else {
                when (tab) {
                    ExactTab.Home -> ExactHome(vm, onOpenInbox = { inboxOpen = true })
                    ExactTab.Analytics -> ExactAnalytics(vm)
                    ExactTab.Accounts -> ExactAccounts(vm)
                    ExactTab.Mine -> ExactMine(vm)
                }
            }
            message?.let { msg ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = 7.dp, start = 18.dp, end = 18.dp)
                        .clickable { vm.dismissMessage(msg) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (msg.error) MaterialTheme.colorScheme.errorContainer else Color(0xEB202124),
                    shadowElevation = 5.dp,
                ) {
                    Text(
                        msg.text,
                        Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                        color = if (msg.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
    if (addMenuOpen) {
        ModalBottomSheet(onDismissRequest = { addMenuOpen = false }) {
            Text("选择记账方式", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { addMenuOpen = false; editorOpen = true },
                leadingContent = { Icon(Icons.Default.EditNote, null, tint = BeePrimaryDark) },
                headlineContent = { Text("手动记账") },
                supportingContent = { Text("填写金额、分类和账户") },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { addMenuOpen = false; imagePicker.launch(arrayOf("image/*")) },
                leadingContent = { Icon(Icons.Default.AddPhotoAlternate, null, tint = BeePrimaryDark) },
                headlineContent = { Text("选择账单图片") },
                supportingContent = { Text("支持多选，AI 识别后确认入账") },
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
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
            color = MaterialTheme.colorScheme.surface,
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
                Icon(Icons.Default.Add, contentDescription = "选择记账方式", tint = BeeText, modifier = Modifier.size(28.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactHome(vm: FinanceViewModel, onOpenInbox: () -> Unit) {
    val context = LocalContext.current
    val rows by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val tags by vm.tags.collectAsState()
    val transactionTags by vm.transactionTags.collectAsState()
    val inbox by vm.inbox.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterOpen by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    val today = LocalDate.now()
    val monthKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val monthRows = rows.filter { it.deletedAt == null && it.localDate.startsWith(monthKey) }
    val income = monthRows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    val expense = monthRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val visible = rows.filter { tx ->
        if (tx.deletedAt != null) false else {
            val p = presentTransaction(tx, accounts)
            val keywordMatches = query.isBlank() || listOfNotNull(p.title, p.accountLine, tx.item, tx.note, tx.localDate).any { it.contains(query.trim(), ignoreCase = true) }
            keywordMatches && (typeFilter == null || tx.transactionType == typeFilter) &&
                (accountFilter == null || tx.accountId == accountFilter || tx.toAccountId == accountFilter) &&
                (categoryFilter == null || tx.categoryId == categoryFilter) &&
                (tagFilter == null || transactionTags.any { it.transactionId == tx.id && it.tagId == tagFilter }) &&
                (fromDate.isBlank() || tx.localDate >= fromDate) && (toDate.isBlank() || tx.localDate <= toDate)
        }
    }
    val grouped = visible.groupBy { it.localDate }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding()
                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 14.dp),
            ) {
                Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("蜜蜂账本", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeText, modifier = Modifier.weight(1f))
                    BadgedBox(badge = { if (inbox.isNotEmpty()) Badge { Text(inbox.size.toString()) } }) {
                        IconButton(onClick = onOpenInbox) { Icon(Icons.Default.Inbox, "待确认账单", tint = BeeText, modifier = Modifier.size(21.dp)) }
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, CalendarActivity::class.java)) }) { Icon(Icons.Default.CalendarMonth, "账单日历", tint = BeeText, modifier = Modifier.size(21.dp)) }
                    IconButton(onClick = { filterOpen = true }) { Icon(Icons.Default.FilterList, "高级筛选", tint = BeeText, modifier = Modifier.size(21.dp)) }
                    IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Default.Search, "搜索", tint = BeeText, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { context.startActivity(Intent(context, SyncStatusActivity::class.java)) }) { Icon(Icons.Default.Sync, "同步", tint = BeeText, modifier = Modifier.size(21.dp)) }
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
                    ExactTransactionRow(tx, accounts, categories) { selectedTransaction = tx }
                    HorizontalDivider(color = BeeBorder, modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (filterOpen) TransactionFilterDialog(
        accounts = accounts,
        categories = categories,
        tags = tags,
        initialType = typeFilter,
        initialAccount = accountFilter,
        initialCategory = categoryFilter,
        initialTag = tagFilter,
        initialFrom = fromDate,
        initialTo = toDate,
        onDismiss = { filterOpen = false },
        onApply = { type, account, category, tag, from, to -> typeFilter = type; accountFilter = account; categoryFilter = category; tagFilter = tag; fromDate = from; toDate = to; filterOpen = false },
    )
    selectedTransaction?.let { tx ->
        EditExactTransactionDialog(
            vm = vm,
            transaction = tx,
            accounts = accounts,
            categories = categories,
            onDismiss = { selectedTransaction = null },
        )
    }
}

@Composable
private fun ExactInbox(vm: FinanceViewModel, onBack: () -> Unit) {
    val rows by vm.inbox.collectAsState()
    val categories by vm.categories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val notificationEvents by vm.notificationEvents.collectAsState()
    val classifierCategories = remember(categories) { categories.map { ClassificationCategory(it.id, it.name, it.categoryType) } }
    val history = remember(transactions) {
        transactions.filter { it.status == "confirmed" && it.categoryId != null }
            .map { ClassificationHistory(it.merchant, it.counterparty, it.item, requireNotNull(it.categoryId)) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = BeeText) }
            Column(Modifier.weight(1f)) {
                Text("待确认", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeText)
                Text("${rows.size} 笔需要分类或核对", fontSize = 10.sp, color = BeeText2)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = BeeGreen)
                    Spacer(Modifier.height(10.dp)); Text("没有待确认账单", color = BeeText2)
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
                    history = history,
                )
                ExactCandidateCard(
                    item = item,
                    categories = categories.filter { it.categoryType == item.transactionType },
                    suggestedCategoryId = suggestion?.categoryId,
                    suggestionReason = suggestion?.let { "建议 ${it.categoryName} · ${it.reason}" },
                    sourcePackage = notificationEvents.firstOrNull { it.transactionId == item.id }?.sourcePackage,
                    onConfirm = { vm.confirm(item.id, it) },
                    onIgnore = { vm.ignore(item.id) },
                )
            }
        }
    }
}

@Composable
private fun ExactCandidateCard(
    item: TransactionEntity,
    categories: List<CategoryEntity>,
    suggestedCategoryId: String?,
    suggestionReason: String?,
    sourcePackage: String?,
    onConfirm: (String) -> Unit,
    onIgnore: () -> Unit,
) {
    var categoryId by remember(item.id, suggestedCategoryId, categories) { mutableStateOf(item.categoryId ?: suggestedCategoryId) }
    var categoryMenu by remember { mutableStateOf(false) }
    val source = when {
        sourcePackage == "com.tencent.mm" || item.sourceType.contains("wechat", true) -> "微信支付"
        sourcePackage == "com.eg.android.AlipayGphone" || item.sourceType.contains("alipay", true) -> "支付宝"
        item.sourceType.contains("import", true) -> "账单导入"
        else -> "自动捕获"
    }
    val title = listOfNotNull(item.merchant, item.counterparty, item.item, item.note).firstOrNull { it.isNotBlank() } ?: source
    val time = runCatching { Instant.parse(item.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) }.getOrDefault(item.localDate)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(BeePrimary.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (source.contains("微信")) Icons.Default.Chat else Icons.Default.ReceiptLong, null, tint = BeePrimaryDark)
                }
                Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = BeeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$source · $time", fontSize = 11.sp, color = BeeText2)
                }
                Text(MoneyParser.formatCny(item.amountCents), fontWeight = FontWeight.Bold, color = BeeText)
            }
            if (title == source) Text("支付通知只提供了金额，请选择分类后确认", fontSize = 11.sp, color = BeeText2)
            suggestionReason?.let { Text(it, fontSize = 11.sp, color = BeePrimaryDark) }
            Box {
                OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(categories.firstOrNull { it.id == categoryId }?.name ?: "选择分类", Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; categoryMenu = false }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { categoryId?.let(onConfirm) }, enabled = categoryId != null, modifier = Modifier.weight(1f)) { Text("分类并确认") }
                OutlinedButton(onClick = onIgnore) { Text("忽略") }
            }
        }
    }
}

@Composable
private fun TransactionFilterDialog(
    accounts: List<AccountEntity>, categories: List<CategoryEntity>, tags: List<com.lifetrace.finance.data.TagEntity>, initialType: String?, initialAccount: String?, initialCategory: String?, initialTag: String?, initialFrom: String, initialTo: String,
    onDismiss: () -> Unit, onApply: (String?, String?, String?, String?, String, String) -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var account by remember { mutableStateOf(initialAccount) }
    var category by remember { mutableStateOf(initialCategory) }
    var tag by remember { mutableStateOf(initialTag) }
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    val validDates = (from.isBlank() || runCatching { LocalDate.parse(from) }.isSuccess) && (to.isBlank() || runCatching { LocalDate.parse(to) }.isSuccess) && (from.isBlank() || to.isBlank() || from <= to)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("高级筛选") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ExactNullableSelector("类型", listOf("expense" to "支出", "income" to "收入", "transfer" to "转账", "refund" to "退款"), type) { type = it } }
            item { ExactNullableSelector("账户", accounts.map { it.id to it.name }, account) { account = it } }
            item { ExactNullableSelector("分类", categories.map { it.id to it.name }, category) { category = it } }
            item { ExactNullableSelector("标签", tags.map { it.id to it.name }, tag) { tag = it } }
            item { OutlinedTextField(from, { from = it }, label = { Text("开始日期 YYYY-MM-DD") }, singleLine = true, isError = !validDates) }
            item { OutlinedTextField(to, { to = it }, label = { Text("结束日期 YYYY-MM-DD") }, singleLine = true, isError = !validDates) }
        } },
        confirmButton = { Button(onClick = { onApply(type, account, category, tag, from, to) }, enabled = validDates) { Text("应用") } },
        dismissButton = { Row { TextButton(onClick = { onApply(null, null, null, null, "", "") }) { Text("清除") }; TextButton(onClick = onDismiss) { Text("取消") } } },
    )
}

@Composable
private fun ExactNullableSelector(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "全部"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：$selectedLabel", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部") }, onClick = { onSelect(null); expanded = false })
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
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
private fun ExactTransactionRow(tx: TransactionEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>, onClick: () -> Unit) {
    val category = categories.firstOrNull { it.id == tx.categoryId }?.name ?: if (tx.transactionType == "transfer") "转账" else "其他"
    val p = presentTransaction(tx, accounts, category)
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var type by remember(initialTransactionType) {
        mutableStateOf(when (initialTransactionType) { "income" -> TransactionType.INCOME; "transfer" -> TransactionType.TRANSFER; else -> TransactionType.EXPENSE })
    }
    var selectedCategory by remember(type) { mutableStateOf<CategoryEntity?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    val relevant = categories.filter { it.categoryType == type.wire }.take(16)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
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
                        TextButton(onClick = { context.startActivity(Intent(context, BookkeepingManagementActivity::class.java)) }) {
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
            containerColor = MaterialTheme.colorScheme.surface,
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
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key ->
                        Surface(Modifier.weight(1f).height(50.dp).clickable { onKey(key) }, shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                            Box(contentAlignment = Alignment.Center) { Text(key, fontSize = 21.sp, color = BeeText, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("今天", "+", "−").forEach { label ->
                Surface(Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
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
    var period by remember { mutableStateOf("month") }
    var type by remember { mutableStateOf("expense") }
    val confirmed = rows.filter { it.deletedAt == null && it.status == "confirmed" && !it.excludeFromStats }
    val scoped = confirmed.filter { tx -> when (period) {
        "month" -> tx.localDate.startsWith(now.toString().take(7))
        "year" -> tx.localDate.startsWith(now.year.toString())
        else -> true
    } }
    val typedRows = scoped.filter { it.transactionType == type || (type == "income" && it.transactionType == "refund") }
    val total = typedRows.sumOf { it.nativeAmountCents ?: it.amountCents }
    val ranked = typedRows.groupBy { it.categoryId }.map { (id, list) -> (categories.firstOrNull { it.id == id }?.name ?: "其他") to list.sumOf { it.nativeAmountCents ?: it.amountCents } }.sortedByDescending { it.second }
    val monthTrend = (1..12).map { month ->
        val key = YearMonth.of(now.year, month).toString()
        month to confirmed.filter { it.localDate.startsWith(key) && (it.transactionType == type || (type == "income" && it.transactionType == "refund")) }.sumOf { it.nativeAmountCents ?: it.amountCents }
    }
    val trendMax = monthTrend.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            Column(Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().padding(16.dp)) {
                Text("图表分析", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeText)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(BeePrimaryDark.copy(alpha = .3f), RoundedCornerShape(20.dp)).padding(3.dp)) {
                    ExactSegment("月", period == "month", Modifier.weight(1f)) { period = "month" }
                    ExactSegment("年", period == "year", Modifier.weight(1f)) { period = "year" }
                    ExactSegment("全部", period == "all", Modifier.weight(1f)) { period = "all" }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text("支出") })
            FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text("收入") })
        } }
        item { Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) { Text(if (type == "expense") "总支出" else "总收入", fontSize = 12.sp, color = BeeText2); Text(MoneyParser.formatCny(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BeeText) } }
        if (period == "year") {
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${now.year} 年月度趋势", fontWeight = FontWeight.SemiBold, color = BeeText)
                    monthTrend.forEach { (month, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${month}月", Modifier.width(34.dp), fontSize = 11.sp, color = BeeText2)
                            LinearProgressIndicator(progress = { value.toFloat() / trendMax }, modifier = Modifier.weight(1f).height(6.dp), color = if (type == "expense") BeePrimaryDark else BeeGreen, trackColor = BeeBorder)
                            Text(MoneyParser.formatPlain(value), Modifier.width(82.dp), textAlign = TextAlign.End, fontSize = 11.sp, color = BeeText)
                        }
                    }
                }
            }
        }
        item { Text("分类排行", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold, color = BeeText) }
        if (ranked.isEmpty()) item { Text("当前范围暂无数据", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = BeeText2) }
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
private fun ExactSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent) { Text(label, Modifier.padding(vertical = 7.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = BeeText, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun ExactAccounts(vm: FinanceViewModel) {
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val ledgers by vm.ledgers.collectAsState()
    val selected by vm.selectedLedgerId.collectAsState()
    val rows by vm.transactions.collectAsState()
    val monthKey = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) }
    val monthRows = rows.filter { it.deletedAt == null && it.status == "confirmed" && it.localDate.startsWith(monthKey) }
    val income = monthRows.filter { it.transactionType == "income" || it.transactionType == "refund" }.sumOf { it.amountCents }
    val expense = monthRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val netWorthTrend = AccountBalance.netWorthTrend(accounts, rows.filter { it.deletedAt == null && it.status == "confirmed" })
    LazyColumn(Modifier.fillMaxSize().background(BeeBg), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().padding(16.dp)) {
                Text("账本", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeText)
                Text("不同账本的数据和账户彼此独立", fontSize = 12.sp, color = BeeText.copy(alpha = .65f))
            }
        }
        item { Text("我的账本", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 13.sp, color = BeeText2) }
        items(ledgers, key = { it.id }) { ledger ->
            val isSelected = ledger.id == selected
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { vm.selectLedger(ledger.id) },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) BeePrimary.copy(alpha = .18f) else MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(if (isSelected) BeePrimary else BeeBg, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MenuBook, null, tint = BeeText, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ledger.name, color = BeeText, fontWeight = FontWeight.SemiBold)
                        Text("${ledger.currency} · 每月 ${ledger.monthStartDay} 日起算", fontSize = 11.sp, color = BeeText2)
                    }
                    if (isSelected) Text("当前", color = BeePrimaryDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB2B2B2))
                }
            }
        }
        if (ledgers.isEmpty()) item { Text("还没有账本", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, color = BeeText2) }
        item {
            val currentName = ledgers.firstOrNull { it.id == selected }?.name ?: "当前账本"
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$currentName · 本月", fontWeight = FontWeight.SemiBold, color = BeeText)
                    Row(Modifier.fillMaxWidth()) {
                        ExactLedgerMetric("收入", income, Modifier.weight(1f))
                        ExactLedgerMetric("支出", expense, Modifier.weight(1f))
                        ExactLedgerMetric("结余", income - expense, Modifier.weight(1f))
                    }
                    Text("包含 ${accounts.size} 个账户、${rows.count { it.deletedAt == null }} 笔账单", fontSize = 12.sp, color = BeeText2)
                }
            }
        }
        item { ExactNetWorthTrend(netWorthTrend) }
        item {
            Button(
                onClick = { context.startActivity(Intent(context, BookkeepingManagementActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) { Text("管理账本和账户") }
        }
    }
}

@Composable
private fun ExactNetWorthTrend(points: List<AccountBalance.MonthlyPoint>) {
    val primary = BeePrimaryDark
    val grid = BeeBorder
    val values = points.map { it.balanceCents }
    val min = values.minOrNull() ?: 0L
    val max = values.maxOrNull() ?: 0L
    val range = (max - min).coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("净资产趋势", fontWeight = FontWeight.SemiBold, color = BeeText)
                    Text("当前账本全部账户 · 内部转账不计入增减", fontSize = 11.sp, color = BeeText2)
                }
                Text(points.lastOrNull()?.balanceCents?.let(MoneyParser::formatCny) ?: "¥0.00", fontWeight = FontWeight.Bold, color = BeeText)
            }
            Canvas(Modifier.fillMaxWidth().height(105.dp)) {
                repeat(3) { index ->
                    val y = size.height * index / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                if (points.isNotEmpty()) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1f)
                        val y = size.height - ((point.balanceCents - min).toFloat() / range) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1f)
                        val y = size.height - ((point.balanceCents - min).toFloat() / range) * size.height
                        drawCircle(primary, 3.5.dp.toPx(), Offset(x, y))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                points.forEach { Text("${it.month.monthValue}月", fontSize = 9.sp, color = BeeText2) }
            }
        }
    }
}

@Composable
private fun EditExactTransactionDialog(
    vm: FinanceViewModel,
    transaction: TransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember(transaction.id) { mutableStateOf(false) }
    var type by remember(transaction.id) { mutableStateOf(transaction.transactionType) }
    var amount by remember(transaction.id) { mutableStateOf(MoneyParser.formatPlain(transaction.amountCents)) }
    var accountId by remember(transaction.id) { mutableStateOf(transaction.accountId) }
    var toAccountId by remember(transaction.id) { mutableStateOf(transaction.toAccountId) }
    var categoryId by remember(transaction.id) { mutableStateOf(transaction.categoryId) }
    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant ?: transaction.counterparty ?: transaction.item.orEmpty()) }
    var note by remember(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    var date by remember(transaction.id) { mutableStateOf(transaction.localDate) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除这笔账单？") },
            text = { Text("删除后将从本机隐藏，并在下次同步时删除云端记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteTransaction(transaction.id)
                        confirmDelete = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账单") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { ExactNullableSelector("类型", listOf("expense" to "支出", "income" to "收入", "transfer" to "转账", "refund" to "退款", "fee" to "手续费"), type) { type = it ?: "expense" } }
            item { OutlinedTextField(amount, { amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item { ExactNullableSelector(if (type == "transfer") "转出账户" else "账户", accounts.map { it.id to it.name }, accountId) { accountId = it } }
            if (type == "transfer") item { ExactNullableSelector("转入账户", accounts.map { it.id to it.name }, toAccountId) { toAccountId = it } }
            if (type != "transfer") item { ExactNullableSelector("分类", categories.filter { category -> category.categoryType == if (type == "refund") "income" else type }.map { it.id to it.name }, categoryId) { categoryId = it } }
            item { OutlinedTextField(merchant, { merchant = it }, modifier = Modifier.fillMaxWidth(), label = { Text("商户/对方") }, singleLine = true) }
            item { OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("交易日期 YYYY-MM-DD") }, singleLine = true) }
            item { OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2, maxLines = 4) }
        } },
        confirmButton = { Button(onClick = {
            vm.updateTransactionDetails(transaction.id, type, amount, accountId, toAccountId, categoryId, merchant.ifBlank { null }, note.ifBlank { null }, date)
            onDismiss()
        }) { Text("保存") } },
        dismissButton = {
            TextButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("删除账单") }
        },
    )
}

@Composable
private fun ExactLedgerMetric(label: String, cents: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = BeeText2)
        Text(MoneyParser.formatPlain(cents), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BeeText)
    }
}

@Composable
private fun ExactMine(vm: FinanceViewModel) {
    val context = LocalContext.current; val pending by vm.pendingSync.collectAsState(); val conflicts by vm.conflictCount.collectAsState(); val ledgers by vm.ledgers.collectAsState(); val rows by vm.transactions.collectAsState(); val accounts by vm.accounts.collectAsState(); val authenticated by vm.authenticated.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(BeeBg)) {
        item { Column(Modifier.fillMaxWidth().background(BeePrimary).statusBarsPadding().padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .58f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Hive, null, tint = BeeText) }; Spacer(Modifier.width(12.dp)); Column { Text("LifeTrace 记账", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BeeText); Text("本地优先 · 云端同步", fontSize = 11.sp, color = BeeText.copy(alpha = .62f)) } }; Row(Modifier.fillMaxWidth().padding(top = 16.dp)) { ExactMineMetric("${ledgers.size}", "账本", Modifier.weight(1f)); ExactMineMetric("${rows.count { it.deletedAt == null }}", "账单", Modifier.weight(1f)); ExactMineMetric("${accounts.size}", "账户", Modifier.weight(1f)) } } }
        item { Spacer(Modifier.height(10.dp)) }
        item { Column(Modifier.background(MaterialTheme.colorScheme.surface)) { ExactMenu(Icons.Default.Sync, "同步", if (authenticated) "待上传 $pending · 冲突 $conflicts" else "未登录 · 点击设置服务器并登录") { context.startActivity(Intent(context, if (authenticated) SyncStatusActivity::class.java else ServerSettingsActivity::class.java)) }; ExactMenu(Icons.Default.AccountBalanceWallet, "记账管理", "账本、账户、分类、预算") { context.startActivity(Intent(context, BookkeepingManagementActivity::class.java)) }; ExactMenu(Icons.Default.UploadFile, "账单导入", "CSV / XLSX") { context.startActivity(Intent(context, BillImportActivity::class.java)) }; ExactMenu(Icons.Default.SaveAlt, "数据管理", "CSV 导出、完整备份与恢复") { context.startActivity(Intent(context, DataMaintenanceActivity::class.java)) }; ExactMenu(Icons.Default.AutoAwesome, "智能记账", "截图识别与 AI 设置") { context.startActivity(Intent(context, AiSettingsActivity::class.java)) } } }
        item { Spacer(Modifier.height(10.dp)) }
        item { Column(Modifier.background(MaterialTheme.colorScheme.surface)) { ExactMenu(Icons.Default.Palette, "外观与隐私", "主题、提醒和防截屏") { context.startActivity(Intent(context, AppearanceSettingsActivity::class.java)) }; ExactMenu(Icons.Default.AppShortcut, "快捷方式指南", "桌面快捷入口、磁贴和自动化链接") { context.startActivity(Intent(context, ShortcutsGuideActivity::class.java)) }; ExactMenu(Icons.Default.Storage, "存储空间", "查看占用并安全清理缓存") { context.startActivity(Intent(context, StorageManagementActivity::class.java)) }; ExactMenu(Icons.Default.Article, "日志中心", "搜索、筛选和导出脱敏诊断日志") { context.startActivity(Intent(context, LogCenterActivity::class.java)) }; ExactMenu(Icons.Default.Cloud, "服务器与登录", vm.baseUrl()) { context.startActivity(Intent(context, ServerSettingsActivity::class.java)) }; ExactMenu(Icons.Default.Info, "关于", "LifeTrace Finance") { showAbout = true } } }
    }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text("确定") } }, title = { Text("LifeTrace Finance") }, text = { Text("本地优先的 Android 记账应用，支持云端同步、账单导入和智能记账。") })
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
