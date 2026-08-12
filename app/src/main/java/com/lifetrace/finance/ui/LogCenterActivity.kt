package com.lifetrace.finance.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lifetrace.finance.AppGraph
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class LogCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppGraph.get(this).db.diagnosticDao()
        setContent { LifeTraceTheme {
            val rows by dao.recent(1000).collectAsState(initial = emptyList())
            var query by remember { mutableStateOf("") }
            var level by remember { mutableStateOf<String?>(null) }
            val filtered = rows.filter { (level == null || it.level.equals(level, true)) && (query.isBlank() || listOf(it.component, it.eventCode, it.message).any { text -> text.contains(query.trim(), true) }) }
            Scaffold(topBar = { TopAppBar(title = { Text("日志中心") }, navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.Default.ArrowBack, "返回") } }, actions = {
                IconButton(onClick = { share(filtered.joinToString("\n") { "${it.timestamp} ${it.level} ${it.component}/${it.eventCode} ${it.message}" }) }) { Icon(Icons.Default.Share, "导出") }
                IconButton(onClick = { lifecycleScope.launch { dao.clearAll() } }) { Icon(Icons.Default.DeleteOutline, "清空") }
            }) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "清空搜索") } }, label = { Text("搜索组件、事件或内容") }, singleLine = true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(null to "全部", "info" to "信息", "warn" to "警告", "error" to "错误").forEach { (wire, label) -> FilterChip(level == wire, { level = wire }, { Text(label) }) } } }
                item { Text("共 ${rows.size} 条 · 当前显示 ${filtered.size} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (filtered.isEmpty()) item { Text("暂无符合条件的诊断日志", Modifier.fillMaxWidth().padding(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(filtered, key = { it.id }) { event -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("${event.component} · ${event.eventCode}", style = MaterialTheme.typography.titleSmall); Text("${event.level.uppercase()} · ${event.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(event.message, style = MaterialTheme.typography.bodySmall) } } }
            } }
        } }
    }
    private fun share(body: String) { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "LifeTrace Finance diagnostics").putExtra(Intent.EXTRA_TEXT, body.ifBlank { "No diagnostic events." }), "导出脱敏诊断日志")) }
}
