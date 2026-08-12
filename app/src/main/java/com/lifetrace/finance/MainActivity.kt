package com.lifetrace.finance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetrace.finance.sync.RecurringScheduler
import com.lifetrace.finance.sync.SyncScheduler
import com.lifetrace.finance.ui.FinanceViewModel
import com.lifetrace.finance.ui.LedgerContextBar
import com.lifetrace.finance.ui.LifeTraceFinanceApp
import com.lifetrace.finance.ui.LifeTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncScheduler.ensurePeriodic(this)
        RecurringScheduler.ensurePeriodic(this)
        RecurringScheduler.scheduleNow(this)
        AppGraph.get(this).screenshotMonitor.restore()
        val shortcutHost = intent?.data?.host
        val shortcutType = intent?.data?.pathSegments?.firstOrNull()
        val initial = intent?.getStringExtra("destination") ?: if (shortcutHost == "inbox") "inbox" else "quick"
        val sharedText = intent?.getStringExtra("shared_text")
        val initialTransactionType = intent?.getStringExtra("transaction_type") ?: shortcutType
        setContent {
            LifeTraceTheme {
                val vm: FinanceViewModel = viewModel()
                Column {
                    LedgerContextBar(vm)
                    Box(Modifier.weight(1f)) {
                        LifeTraceFinanceApp(
                            vm,
                            initialDestination = initial,
                            sharedText = sharedText,
                            initialTransactionType = initialTransactionType,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}
