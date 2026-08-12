package com.lifetrace.finance.ui

import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lifetrace.finance.domain.DataPortabilityManager
import com.lifetrace.finance.domain.RestorePreview
import kotlinx.coroutines.launch
import java.time.LocalDate

class DataMaintenanceActivity : ComponentActivity() {
    private val manager by lazy { DataPortabilityManager(this) }
    private var status by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var restorePreview by mutableStateOf<RestorePreview?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LifeTraceTheme { DataMaintenanceScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DataMaintenanceScreen() {
        val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { runTask { "已导出 ${manager.exportCurrentLedgerCsv(it)} 笔账单" } }
        }
        val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri?.let { runTask { val p = manager.exportBackup(it); "备份完成：${p.transactionCount} 笔账单，${p.attachmentCount} 个附件" } }
        }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { runTask(result = { manager.stageRestore(it) }) { restorePreview = it; "备份校验通过" } }
        }
        Scaffold(
            topBar = { TopAppBar(title = { Text("数据管理") }, navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.Default.ArrowBack, "返回") } }) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("导出和备份", style = MaterialTheme.typography.titleMedium)
                DataAction(Icons.Default.TableView, "导出当前账本 CSV", "包含收支、分类、账户、标签和附件数量", busy) {
                    csvLauncher.launch("LifeTrace-${LocalDate.now()}.csv")
                }
                DataAction(Icons.Default.Inventory2, "创建完整数据备份", "备份数据库及本地账单附件，可用于整机迁移", busy) {
                    backupLauncher.launch("LifeTrace-backup-${LocalDate.now()}.zip")
                }
                HorizontalDivider()
                Text("恢复", style = MaterialTheme.typography.titleMedium)
                DataAction(Icons.Default.Restore, "从备份恢复", "先隔离校验，再确认替换；当前数据将被覆盖", busy) {
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                HorizontalDivider()
                Text("维护", style = MaterialTheme.typography.titleMedium)
                DataAction(Icons.Default.Sync, "立即同步", "恢复前建议先将当前数据同步到 LifeTrace Cloud", busy) {
                    com.lifetrace.finance.sync.SyncScheduler.scheduleNow(this@DataMaintenanceActivity); status = "已提交同步任务"
                }
                status?.let { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) }) }
                Spacer(Modifier.weight(1f))
                Text("恢复只接受 LifeTrace Finance 生成且数据库版本兼容的备份。服务器地址和登录凭据不会写入导出文件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        restorePreview?.let { preview ->
            AlertDialog(
                onDismissRequest = { restorePreview = null },
                icon = { Icon(Icons.Default.WarningAmber, null) },
                title = { Text("确认覆盖当前数据？") },
                text = { Text("备份时间：${preview.exportedAt}\n账单：${preview.transactionCount} 笔\n附件：${preview.attachmentCount} 个\n\n确认后应用会重启并替换当前本地数据库。") },
                confirmButton = { Button(onClick = {
                    manager.confirmRestore()
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (launchIntent != null) {
                        val restart = PendingIntent.getActivity(this@DataMaintenanceActivity, 7331, launchIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        getSystemService(AlarmManager::class.java).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 700, restart)
                    }
                    finishAffinity()
                    Process.killProcess(Process.myPid())
                }) { Text("覆盖并重启") } },
                dismissButton = { TextButton(onClick = { restorePreview = null }) { Text("取消") } },
            )
        }
    }

    @Composable
    private fun DataAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, disabled: Boolean, onClick: () -> Unit) {
        Card(onClick = onClick, enabled = !disabled, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp)) {
                Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }

    private fun runTask(block: suspend () -> String) {
        busy = true; status = "处理中…"
        lifecycleScope.launch { runCatching { block() }.onSuccess { status = it }.onFailure { status = "操作失败：${it.message}" }; busy = false }
    }

    private fun <T> runTask(result: suspend () -> T, onSuccess: (T) -> String) {
        busy = true; status = "正在校验…"
        lifecycleScope.launch { runCatching { result() }.onSuccess { status = onSuccess(it) }.onFailure { status = "校验失败：${it.message}" }; busy = false }
    }
}
