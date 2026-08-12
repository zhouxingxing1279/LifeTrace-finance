package com.lifetrace.finance.importer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.LedgerEntity
import com.lifetrace.finance.ui.LifeTraceTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BillImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUri = intent?.getStringExtra(EXTRA_FILE_URI)?.let(Uri::parse)
        setContent {
            LifeTraceTheme {
                val vm: BillImportViewModel = viewModel()
                LaunchedEffect(initialUri) { initialUri?.let(vm::load) }
                BillImportScreen(vm, ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_FILE_URI = "bill_import_file_uri"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillImportScreen(vm: BillImportViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val ledgers by vm.ledgers.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val ledgerId by vm.ledgerId.collectAsState()
    val accountId by vm.accountId.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::load) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单导入") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("复用 LifeTrace EPIC-13 导入规则，支持微信/支付宝/银行 CSV 与 XLSX。解析和落库都在 Android 本机完成。", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(
                    onClick = { picker.launch(arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.fileName == null) "选择账单文件" else "重新选择文件") }
            }
            state.fileName?.let { fileName -> item { Text("文件：$fileName", fontWeight = FontWeight.SemiBold) } }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }

            state.preview?.let { preview ->
                item { LedgerDropdown(ledgers, ledgerId, vm::selectLedger) }
                item { AccountDropdown(accounts, accountId, vm::selectAccount) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("解析预览", fontWeight = FontWeight.SemiBold)
                            Text("来源：${sourceLabel(preview.sourceType)}")
                            Text("有效 ${preview.bills.size} / 数据行 ${preview.totalDataRows}")
                            Text("警告 ${preview.warnings.size} 条")
                        }
                    }
                }
                if (preview.warnings.isNotEmpty()) {
                    item { Text("解析警告", fontWeight = FontWeight.SemiBold) }
                    items(preview.warnings.take(20)) { warning -> Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (preview.warnings.size > 20) item { Text("另有 ${preview.warnings.size - 20} 条警告未展开", style = MaterialTheme.typography.bodySmall) }
                }
                item { Text("账单预览", fontWeight = FontWeight.SemiBold) }
                items(preview.bills.take(100)) { bill -> BillPreviewRow(bill) }
                if (preview.bills.size > 100) item { Text("仅展示前 100 笔，确认后会处理全部 ${preview.bills.size} 笔。", style = MaterialTheme.typography.bodySmall) }
                item {
                    Button(
                        onClick = vm::commit,
                        enabled = !state.loading && preview.bills.isNotEmpty() && ledgerId != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("确认导入 ${preview.bills.size} 笔") }
                }
            }

            state.result?.let { result ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("导入完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("新增：${result.created}")
                            Text("重复跳过：${result.duplicates}")
                            Text("与通知/截图候选对账：${result.reconciledCandidates}")
                            Text("新增账单已进入现有 LifeTrace Outbox，同步到同一个 LifeTrace Cloud。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerDropdown(ledgers: List<LedgerEntity>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text("导入到账本：${ledgers.firstOrNull { it.id == selected }?.name ?: "请选择"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ledgers.forEach { ledger -> DropdownMenuItem(text = { Text("${ledger.name} · ${ledger.currency}") }, onClick = { onSelect(ledger.id); open = false }) }
        }
    }
}

@Composable
private fun AccountDropdown(accounts: List<AccountEntity>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text("统一关联账户：${accounts.firstOrNull { it.id == selected }?.name ?: "不指定"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("不指定") }, onClick = { onSelect(null); open = false })
            accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account.id); open = false }) }
        }
    }
}

@Composable
private fun BillPreviewRow(bill: ImportedBill) {
    val localTime = remember(bill.occurredAt) {
        bill.occurredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bill.merchant ?: bill.item ?: when (bill.type.wire) { "income" -> "收入"; "refund" -> "退款"; else -> "支出" }, fontWeight = FontWeight.Medium)
                Text("$localTime · ${bill.status.wire}${bill.externalTransactionId?.let { " · ${it.takeLast(12)}" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            }
            Text(MoneyParser.formatCny(bill.amountCents), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "wechat_import" -> "微信"
    "alipay_import" -> "支付宝"
    "bank_import" -> "银行"
    else -> "通用账单文件"
}
