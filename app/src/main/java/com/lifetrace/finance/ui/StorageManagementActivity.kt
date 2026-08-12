package com.lifetrace.finance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.domain.StorageInspector
import com.lifetrace.finance.domain.formatStorageSize

class StorageManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { LifeTraceTheme { Content() } } }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Content() {
        val inspector = remember { StorageInspector(this) }
        var revision by remember { mutableIntStateOf(0) }
        val snapshot = remember(revision) { inspector.snapshot() }
        var confirmCache by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        Scaffold(topBar = { TopAppBar(title = { Text("存储空间管理") }, navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.Default.ArrowBack, "返回") } }, actions = { IconButton(onClick = { revision++ }) { Icon(Icons.Default.Refresh, "重新扫描") } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(18.dp)) { Text("本机占用", style = MaterialTheme.typography.titleMedium); Text(formatStorageSize(snapshot.totalBytes), style = MaterialTheme.typography.headlineMedium) } }
                StorageRow(Icons.Default.Storage, "账本数据库", "交易、账户、分类及同步队列", snapshot.databaseBytes)
                StorageRow(Icons.Default.AttachFile, "账单附件", "图片和 PDF，仅随账单手动删除", snapshot.attachmentBytes)
                StorageRow(Icons.Default.Settings, "应用设置", "主题、服务地址及本地状态", snapshot.preferenceBytes)
                Card(onClick = { if (snapshot.cacheBytes > 0) confirmCache = true }, Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp)) { Icon(Icons.Default.CleaningServices, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("临时缓存"); Text("可安全清理，不会删除账本和附件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(formatStorageSize(snapshot.cacheBytes)) } }
                OutlinedButton(onClick = { AppGraph.get(this@StorageManagementActivity).processedImages.clear(); status = "截图识别去重缓存已清空" }, Modifier.fillMaxWidth()) { Text("清空截图识别历史") }
                status?.let { AssistChip(onClick = { status = null }, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }) }
                Spacer(Modifier.weight(1f)); Text("账本数据库、附件和登录凭据不会被“清理缓存”删除。如需迁移或恢复，请使用数据管理中的完整备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (confirmCache) AlertDialog(onDismissRequest = { confirmCache = false }, title = { Text("清理临时缓存？") }, text = { Text("将释放 ${formatStorageSize(snapshot.cacheBytes)}，不会影响账本、附件和设置。") }, confirmButton = { Button(onClick = { val freed = inspector.clearCache(); status = "已释放 ${formatStorageSize(freed)}"; revision++; confirmCache = false }) { Text("清理") } }, dismissButton = { TextButton(onClick = { confirmCache = false }) { Text("取消") } })
    }
    @Composable private fun StorageRow(icon: ImageVector, title: String, subtitle: String, bytes: Long) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(formatStorageSize(bytes)) } } }
}
