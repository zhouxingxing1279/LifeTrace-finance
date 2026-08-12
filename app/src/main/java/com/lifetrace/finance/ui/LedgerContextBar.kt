package com.lifetrace.finance.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.BuildConfig
import com.lifetrace.finance.importer.BillImportActivity

/**
 * Persistent bookkeeping command surface for normal Home / Detail / Quick Entry / Reports screens.
 * Advanced features must stay directly reachable even while ledger initialization is still running.
 */
@Composable
fun LedgerContextBar(vm: FinanceViewModel) {
    val ledgers by vm.ledgers.collectAsState()
    val selectedId by vm.selectedLedgerId.collectAsState()
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (ledgers.isNotEmpty()) open = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ledgers.isNotEmpty(),
                    ) {
                        val current = ledgers.firstOrNull { it.id == selectedId } ?: ledgers.firstOrNull()
                        Text(
                            if (current == null) "账本初始化中…" else "账本：${current.name} · ${current.currency}",
                            maxLines = 1,
                        )
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        ledgers.forEach { ledger ->
                            DropdownMenuItem(
                                text = { Text("${ledger.name} · ${ledger.currency}") },
                                onClick = { vm.selectLedger(ledger.id); open = false },
                            )
                        }
                    }
                }
                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    onClick = { context.startActivity(Intent(context, BookkeepingManagementActivity::class.java)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("记账管理", maxLines = 1) }
                FilledTonalButton(
                    onClick = { context.startActivity(Intent(context, BillImportActivity::class.java)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("账单导入", maxLines = 1) }
                FilledTonalButton(
                    onClick = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("AI 设置", maxLines = 1) }
            }
        }
    }
}
