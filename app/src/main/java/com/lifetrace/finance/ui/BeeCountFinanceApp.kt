package com.lifetrace.finance.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.CategoryEntity
import com.lifetrace.finance.data.TransactionEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BeeYellow = Color(0xFFFFC928)
private val BeeYellowDeep = Color(0xFFF6BC00)
private val BeeInk = Color(0xFF17191F)
private val BeeMuted = Color(0xFF777A82)
private val BeePage = Color(0xFFF6F6F6)
private val BeeLine = Color(0xFFECECEC)
private val BeeIncome = Color(0xFF2E8B68)

private enum class BeeTab(val label: String, val icon: ImageVector) {
    BILLS("明细", Icons.Default.ReceiptLong),
    REPORT("图表", Icons.Default.PieChart),
    ACCOUNT("账本", Icons.Default.AccountBalanceWallet),
    MINE("我的", Icons.Default.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeeCountFinanceApp(
    vm: FinanceViewModel,
    initialDestination: String,
    sharedText: String?,
    initialTransactionType: String? = null,
) {
    var tab by remember(initialDestination) {
        mutableStateOf(
            when (initialDestination) {
                "reports" -> BeeTab.REPORT
                "accounts", "settings" -> BeeTab.MINE
                else -> BeeTab.BILLS
            },
        )
    }
    var showEntry by remember(initialDestination) { mutableStateOf(initialDestination == "quick") }
    val message by vm.message.collectAsState()

    Scaffold(
        containerColor = BeePage,
        bottomBar = {
            if (!showEntry) {
                Box(Modifier.fillMaxWidth().height(74.dp)) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        containerColor = Color.White,
                        tonalElevation = 4.dp,
                    ) {
                        BeeTab.entries.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                                label = { Text(item.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BeeYellowDeep,
                                    selectedTextColor = BeeInk,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = Color(0xFF777777),
                                    unselectedTextColor = Color(0xFF777777),
                                ),
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = { showEntry = true },
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).size(58.dp),
                        shape = CircleShape,
                        containerColor = BeeYellow,
                        contentColor = BeeInk,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    ) { Icon(Icons.Default.Add, contentDescription = "记一笔", modifier = Modifier.size(30.dp)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (showEntry) {
                BeeEntryScreen(
                    vm = vm,
                    sharedText = sharedText,
                    initialTransactionType = initialTransactionType,
                    onClose = { showEntry = false },
                )
            } else {
                when (tab) {
                    BeeTab.BILLS -> BeeBillsScreen(vm, onAdd = { showEntry = true })
                    BeeTab.REPORT -> BeeReportScreen(vm)
                    BeeTab.ACCOUNT -> BeeAccountScreen(vm)
                    BeeTab.MINE -> BeeMineScreen(vm)
                }
            }

            message?.let { msg ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 18.dp, end = 18.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (msg.error) MaterialTheme.colorScheme.errorContainer else Color(0xFF222222),
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
private fun BeeBillsScreen(vm: FinanceViewModel, onAdd: () -> Unit) {
    val rows by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    val now = LocalDate.now()
    val monthKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val monthRows = rows.filter { it.deletedAt == null && it.localDate.startsWith(monthKey) }
    val income = monthRows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
    val expense = monthRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
    val filtered = remember(rows, query, accounts) {
        val q = query.trim()
        if (q.isEmpty()) rows.filter { it.deletedAt == null }
        else rows.filter {
            it.deletedAt == null && listOfNotNull(
                presentTransaction(it, accounts).title,
                presentTransaction(it, accounts).accountLine,
                it.note,
                it.localDate,
            ).any { value -> value.contains(q, true) }
        }
    }
    val grouped = filtered.groupBy { it.localDate }

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Column(Modifier.fillMaxWidth().background(BeeYellow).statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hive, contentDescription = null, tint = BeeInk, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("蜜蜂账本", color = BeeInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { }) { Icon(Icons.Default.Search, "搜索", tint = BeeInk) }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.width(82.dp)) {
                        Text("${now.year}年", fontSize = 12.sp, color = BeeInk.copy(alpha = 0.72f))
                        Text("${now.monthValue}月⌄", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeInk)
                    }
                    BeeHeaderMetric("收入", MoneyParser.formatPlain(income), Modifier.weight(1f))
                    BeeHeaderMetric("支出", MoneyParser.formatPlain(expense), Modifier.weight(1f))
                    BeeHeaderMetric("结余", MoneyParser.formatPlain(income - expense), Modifier.weight(1f))
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).testTag("transaction_search"),
                placeholder = { Text("搜索商户、账户、备注") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空") } },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BeeYellowDeep,
                    unfocusedBorderColor = BeeLine,
                    focusedContainerColor = Color(0xFFF7F7F7),
                    unfocusedContainerColor = Color(0xFFF7F7F7),
                ),
            )
        }
        if (grouped.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有账单", color = BeeMuted)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeInk)) { Text("记第一笔") }
                }
            }
        } else {
            grouped.forEach { (date, dayRows) ->
                item(key = "header-$date") {
                    val dayExpense = dayRows.filter { it.transactionType == "expense" }.sumOf { it.amountCents }
                    val dayIncome = dayRows.filter { it.transactionType == "income" }.sumOf { it.amountCents }
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFF2F2F2)).padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(date, fontSize = 12.sp, color = BeeMuted, modifier = Modifier.weight(1f))
                        Text("支出 ${MoneyParser.formatPlain(dayExpense)}   收入 ${MoneyParser.formatPlain(dayIncome)}", fontSize = 12.sp, color = BeeMuted)
                    }
                }
                items(dayRows, key = { it.id }) { item ->
                    BeeTransactionRow(item, accounts, categories)
                    HorizontalDivider(color = BeeLine, modifier = Modifier.padding(start = 66.dp))
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun BeeHeaderMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = BeeInk.copy(alpha = 0.65f))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = BeeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BeeTransactionRow(item: TransactionEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>) {
    val category = categories.firstOrNull { it.id == item.categoryId }?.name ?: if (item.transactionType == "transfer") "转账" else "其他"
    val p = presentTransaction(item, accounts, category)
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(Color(0xFFF3F0FB), CircleShape), contentAlignment = Alignment.Center) {
            Icon(categoryIcon(category, item.transactionType), null, tint = Color(0xFF645A78), modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = BeeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(p.accountLine, category).joinToString(" · "), fontSize = 12.sp, color = BeeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when (item.transactionType) {
                "income" -> "+${MoneyParser.formatPlain(item.amountCents)}"
                "expense" -> "-${MoneyParser.formatPlain(item.amountCents)}"
                else -> MoneyParser.formatPlain(item.amountCents)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (item.transactionType == "income") BeeIncome else BeeInk,
        )
    }
}

private fun categoryIcon(name: String, type: String): ImageVector = when {
    type == "transfer" -> Icons.Default.SwapHoriz
    name.contains("餐") || name.contains("食") -> Icons.Default.Restaurant
    name.contains("交通") || name.contains("车") -> Icons.Default.DirectionsCar
    name.contains("购") -> Icons.Default.ShoppingBag
    name.contains("居家") || name.contains("住房") -> Icons.Default.Home
    name.contains("医") -> Icons.Default.MedicalServices
    name.contains("教育") || name.contains("学习") -> Icons.Default.School
    name.contains("宠物") -> Icons.Default.Pets
    name.contains("运动") -> Icons.Default.SportsBasketball
    name.contains("工资") || name.contains("收入") -> Icons.Default.Payments
    else -> Icons.Default.Circle
}

@Composable
private fun BeeEntryScreen(vm: FinanceViewModel, sharedText: String?, initialTransactionType: String?, onClose: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var type by remember(initialTransactionType) {
        mutableStateOf(
            when (initialTransactionType) {
                "income" -> TransactionType.INCOME
                "transfer" -> TransactionType.TRANSFER
                else -> TransactionType.EXPENSE
            },
        )
    }
    var amount by remember(sharedText) { mutableStateOf(extractBeeAmount(sharedText.orEmpty())) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember(type, categories) { mutableStateOf(categories.firstOrNull { it.categoryType == type.wire }?.id) }
    var note by remember(sharedText) { mutableStateOf(sharedText?.take(100).orEmpty()) }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("取消", color = BeeInk) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                BeeTypeTab("支出", type == TransactionType.EXPENSE) { type = TransactionType.EXPENSE }
                BeeTypeTab("收入", type == TransactionType.INCOME) { type = TransactionType.INCOME }
                BeeTypeTab("转账", type == TransactionType.TRANSFER) { type = TransactionType.TRANSFER }
            }
            Spacer(Modifier.width(58.dp))
        }
        HorizontalDivider(color = BeeLine)

        Text(
            if (amount.isBlank()) "¥0.00" else "¥$amount",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 24.dp),
            textAlign = TextAlign.End,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = BeeInk,
        )

        val visibleCategories = categories.filter { it.categoryType == type.wire }.take(12)
        if (type != TransactionType.TRANSFER) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(visibleCategories) { category ->
                    val selected = categoryId == category.id
                    Column(
                        modifier = Modifier.width(54.dp).clickable { categoryId = category.id },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(46.dp).background(if (selected) BeeYellow else Color(0xFFF1F1F1), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(categoryIcon(category.name, type.wire), null, tint = BeeInk, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.height(5.dp))
                        Text(category.name, fontSize = 11.sp, color = BeeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BeeAccountSelector("转出", accounts, accountId, Modifier.weight(1f)) { accountId = it }
                BeeAccountSelector("转入", accounts.filter { it.id != accountId }, toAccountId, Modifier.weight(1f)) { toAccountId = it }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BeeAccountSelector("账户", accounts, accountId, Modifier.weight(1f)) { accountId = it }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F4F4), modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(17.dp), tint = BeeMuted)
                    Spacer(Modifier.width(6.dp))
                    Text(LocalDate.now().toString(), fontSize = 12.sp, color = BeeInk)
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("备注…") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFFF5F5F5),
                unfocusedContainerColor = Color(0xFFF5F5F5),
            ),
        )

        Spacer(Modifier.weight(1f))
        BeeKeypad(
            onKey = { key ->
                amount = when (key) {
                    "⌫" -> amount.dropLast(1)
                    "." -> if (amount.contains('.')) amount else if (amount.isBlank()) "0." else amount + "."
                    else -> {
                        val decimals = amount.substringAfter('.', "")
                        if (amount.contains('.') && decimals.length >= 2) amount else (amount + key).take(12)
                    }
                }
            },
            onDone = {
                vm.save(type, amount, accountId, toAccountId, categoryId, null, note.ifBlank { null })
                amount = ""
                note = ""
                onClose()
            },
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && accounts.isNotEmpty(),
        )
    }
}

@Composable
private fun BeeTypeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = BeeInk, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(22.dp).height(3.dp).background(if (selected) BeeYellowDeep else Color.Transparent, CircleShape))
    }
}

@Composable
private fun BeeAccountSelector(label: String, accounts: List<AccountEntity>, selectedId: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F4F4), modifier = Modifier.fillMaxWidth().clickable { open = true }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(17.dp), tint = BeeMuted)
                Spacer(Modifier.width(6.dp))
                Text(accounts.firstOrNull { it.id == selectedId }?.name ?: label, fontSize = 12.sp, color = BeeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account.id); open = false }) }
        }
    }
}

@Composable
private fun BeeKeypad(onKey: (String) -> Unit, onDone: () -> Unit, enabled: Boolean) {
    val rows = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf(".", "0", "⌫"))
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        TextButton(onClick = { onKey(key) }, modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(key, fontSize = 24.sp, color = BeeInk, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F4F4), modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(LocalDate.now().dayOfMonth.toString(), fontSize = 14.sp, color = BeeInk) }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F4F4), modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("+", fontSize = 24.sp, color = BeeInk) }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F4F4), modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("−", fontSize = 24.sp, color = BeeInk) }
            }
            Button(
                onClick = onDone,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("quick_save"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeInk, disabledContainerColor = Color(0xFFE5E5E5)),
            ) { Text("完成", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun BeeReportScreen(vm: FinanceViewModel) {
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
            Column(Modifier.fillMaxWidth().background(BeeYellow).statusBarsPadding().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, null, tint = BeeInk)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("图表分析", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = BeeInk)
                        Text("${now.year}-${now.monthValue.toString().padStart(2, '0')} · 支出", fontSize = 12.sp, color = BeeInk.copy(alpha = .68f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    BeeSegment("月", true, Modifier.weight(1f))
                    BeeSegment("年", false, Modifier.weight(1f))
                    BeeSegment("全部", false, Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.padding(20.dp)) {
                Text("总支出： ${MoneyParser.formatCny(total)}", fontSize = 18.sp, color = BeeMuted)
                Text("日均： ${MoneyParser.formatCny(if (now.dayOfMonth > 0) total / now.dayOfMonth else 0)}", fontSize = 16.sp, color = BeeMuted)
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = BeeLine)
            }
        }
        itemsIndexed(byCategory) { index, (name, value) ->
            val fraction = if (total == 0L) 0f else value.toFloat() / total.toFloat()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(BeeYellow, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text("${index + 1}. $name", modifier = Modifier.weight(1f), color = BeeInk)
                    Text("${(fraction * 100).toInt()}%   ${MoneyParser.formatPlain(value)}", color = BeeMuted)
                }
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(4.dp), color = BeeYellowDeep, trackColor = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
private fun BeeSegment(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(horizontal = 3.dp), shape = RoundedCornerShape(24.dp), color = if (selected) BeeInk else BeeYellowDeep) {
        Text(label, Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, color = if (selected) Color.White else BeeInk, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BeeAccountScreen(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(BeePage), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(BeeYellow).statusBarsPadding().padding(18.dp)) {
                Text("账户总览", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = BeeInk)
                Text("管理支付账户与资金来源", fontSize = 12.sp, color = BeeInk.copy(alpha = .65f))
            }
        }
        item { Text("账户", Modifier.padding(18.dp, 18.dp, 18.dp, 8.dp), fontWeight = FontWeight.Bold, color = BeeInk) }
        items(accounts) { account ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(BeeYellow.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = BeeInk)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.Medium, color = BeeInk)
                        Text(account.accountType, fontSize = 12.sp, color = BeeMuted)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = BeeMuted)
                }
            }
        }
    }
}

@Composable
private fun BeeMineScreen(vm: FinanceViewModel) {
    val context = LocalContext.current
    val pending by vm.pendingSync.collectAsState()
    val conflicts by vm.conflictCount.collectAsState()
    val ledgers by vm.ledgers.collectAsState()
    val rows by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(BeePage)) {
        item {
            Column(Modifier.fillMaxWidth().background(BeeYellow).statusBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(Color.White.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Hive, null, tint = BeeInk)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LifeTrace 记账", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BeeInk)
                        Text("本地优先 · 云端同步", fontSize = 12.sp, color = BeeInk.copy(alpha = .65f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    BeeProfileMetric("${ledgers.size}", "账本", Modifier.weight(1f))
                    BeeProfileMetric("${rows.count { it.deletedAt == null }}", "总笔数", Modifier.weight(1f))
                    BeeProfileMetric("${accounts.size}", "账户", Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BeeMenuRow(Icons.Default.Sync, "同步", "待上传 $pending · 冲突 $conflicts") { vm.syncNow() }
                BeeMenuRow(Icons.Default.AccountBalanceWallet, "记账管理", "账本、账户、分类") {
                    context.startActivity(Intent(context, BookkeepingManagementActivity::class.java))
                }
                BeeMenuRow(Icons.Default.AutoAwesome, "智能记账", "截图与 AI 设置") {
                    context.startActivity(Intent(context, AiSettingsActivity::class.java))
                }
                BeeMenuRow(Icons.Default.Cloud, "服务器", vm.baseUrl()) { }
            }
        }
    }
}

@Composable
private fun BeeProfileMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BeeInk)
        Text(label, fontSize = 12.sp, color = BeeInk.copy(alpha = .62f))
    }
}

@Composable
private fun BeeMenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.White) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(BeeYellow.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = BeeYellowDeep, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = BeeInk, fontWeight = FontWeight.Medium)
                Text(subtitle, color = BeeMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFAAAAAA))
        }
    }
    HorizontalDivider(color = BeeLine, modifier = Modifier.padding(start = 66.dp))
}

private fun extractBeeAmount(text: String): String = Regex("(?:￥|¥)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*元?").find(text)?.groupValues?.getOrNull(1).orEmpty()
