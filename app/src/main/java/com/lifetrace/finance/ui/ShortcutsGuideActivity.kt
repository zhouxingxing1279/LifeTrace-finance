package com.lifetrace.finance.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

class ShortcutsGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { LifeTraceTheme { Content() } } }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Content() {
        var copied by remember { mutableStateOf<String?>(null) }
        val shortcuts = listOf(
            Triple(Icons.Default.RemoveCircleOutline, "记支出", "lifetrace-finance://quick/expense"),
            Triple(Icons.Default.AddCircleOutline, "记收入", "lifetrace-finance://quick/income"),
            Triple(Icons.Default.SwapHoriz, "转账", "lifetrace-finance://quick/transfer"),
            Triple(Icons.Default.Inbox, "待确认", "lifetrace-finance://inbox"),
            Triple(Icons.Default.AccountBalanceWallet, "记账管理", "lifetrace-finance://bookkeeping"),
            Triple(Icons.Default.AutoAwesome, "智能记账设置", "lifetrace-finance://ai-settings"),
        )
        Scaffold(topBar = { TopAppBar(title = { Text("快捷方式指南") }, navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { InfoCard(Icons.Default.Bolt, "更快开始记账", "长按桌面上的 LifeTrace Finance 图标，可直接进入支出、收入、转账和待确认。下列链接还可用于自动化工具。") }
                shortcuts.forEach { (icon, title, link) -> item(key = link) {
                    Card(Modifier.fillMaxWidth().clickable {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, link)); copied = title
                    }) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(link, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ContentCopy, "复制链接")
                    } }
                } }
                item { OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Home, null); Spacer(Modifier.width(8.dp)); Text("打开桌面设置") } }
                item { InfoCard(Icons.Default.TouchApp, "如何添加", "方式一：长按应用图标，再长按某个快捷方式拖到桌面。方式二：下拉快捷设置编辑区，添加“记支出”磁贴。") }
                copied?.let { item { AssistChip(onClick = { copied = null }, label = { Text("已复制“$it”链接") }, leadingIcon = { Icon(Icons.Default.Check, null) }) } }
            }
        }
    }

    @Composable private fun InfoCard(icon: ImageVector, title: String, text: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleMedium) }; Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
