package com.lifetrace.finance.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Persistent ledger context for the normal Home / Detail / Quick Entry / Reports surfaces.
 * FinanceViewModel already scopes their data flows to this selection.
 */
@Composable
fun LedgerContextBar(vm: FinanceViewModel) {
    val ledgers by vm.ledgers.collectAsState()
    val selectedId by vm.selectedLedgerId.collectAsState()
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    if (ledgers.isEmpty()) return

    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    val current = ledgers.firstOrNull { it.id == selectedId } ?: ledgers.first()
                    Text("账本：${current.name} · ${current.currency}", maxLines = 1)
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
            IconButton(
                onClick = {
                    context.startActivity(Intent(context, BookkeepingManagementActivity::class.java))
                },
            ) {
                Icon(Icons.Default.ManageAccounts, contentDescription = "记账管理")
            }
        }
    }
}
