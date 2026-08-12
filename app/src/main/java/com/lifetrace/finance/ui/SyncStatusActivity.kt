package com.lifetrace.finance.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lifetrace.finance.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SyncStatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(applicationContext)
        setContent {
            LifeTraceTheme {
                var state by remember { mutableStateOf<com.lifetrace.finance.data.SyncStateEntity?>(null) }
                var pending by remember { mutableStateOf(0) }
                var conflicts by remember { mutableStateOf(0) }
                var running by remember { mutableStateOf(false) }
                var resultMessage by remember { mutableStateOf<String?>(null) }

                fun refresh() {
                    lifecycleScope.launch {
                        state = graph.db.syncDao().state()
                        pending = graph.db.syncDao().pendingCount().first()
                        conflicts = graph.db.syncDao().conflictCount().first()
                    }
                }
                fun runSync(snapshot: Boolean) {
                    running = true
                    resultMessage = null
                    lifecycleScope.launch {
                        val result = if (snapshot) graph.syncEngine.snapshot() else graph.syncEngine.runOnce()
                        running = false
                        resultMessage = result.fold({ if (snapshot) "云端快照已恢复" else "同步完成" }, { "同步失败：${it.message ?: "未知错误"}" })
                        refresh()
                    }
                }

                androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("同步状态", style = MaterialTheme.typography.headlineSmall)
                    if (graph.auth.currentUserId == null) {
                        Text("尚未登录，无法同步云端账单。", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { startActivity(Intent(this@SyncStatusActivity, ServerSettingsActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("设置服务器并登录") }
                    } else {
                        Text("已登录 LifeTrace Cloud", color = MaterialTheme.colorScheme.primary)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusLine("待上传", "$pending 项")
                            StatusLine("待处理冲突", "$conflicts 项")
                            StatusLine("最近上传", formatSyncTime(state?.lastPushAt))
                            StatusLine("最近下载", formatSyncTime(state?.lastPullAt))
                            StatusLine("游标", state?.cursor ?: "尚未建立")
                            StatusLine("快照恢复", if (state?.snapshotRequired == true) "需要" else "正常")
                        }
                    }
                    state?.lastError?.let {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("最近错误", style = MaterialTheme.typography.titleMedium); Text(it, color = MaterialTheme.colorScheme.error) } }
                    }
                    resultMessage?.let { Text(it, color = if (it.startsWith("同步失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                    if (running) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text("正在与云端同步…") }
                    Button(onClick = { runSync(false) }, enabled = !running && graph.auth.currentUserId != null, modifier = Modifier.fillMaxWidth()) { Text("立即同步") }
                    OutlinedButton(onClick = { runSync(true) }, enabled = !running && graph.auth.currentUserId != null, modifier = Modifier.fillMaxWidth()) { Text("重新下载云端快照") }
                    OutlinedButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("返回") }
                    Spacer(Modifier.height(4.dp))
                    Text("快照恢复会重新读取云端完整数据，不会删除尚未上传的本地修改。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun formatSyncTime(value: String?): String = value?.let {
    runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss")) }.getOrDefault(it)
} ?: "从未"
