package com.lifetrace.finance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.domain.AppearanceSettings
import com.lifetrace.finance.domain.ThemeMode
import com.lifetrace.finance.domain.ReminderSettings
import com.lifetrace.finance.sync.DailyReminderScheduler

class AppearanceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LifeTraceTheme { SettingsScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen() {
        val settings = remember { AppearanceSettings(this) }
        var mode by remember { mutableStateOf(settings.themeMode) }
        var accent by remember { mutableStateOf(settings.accent) }
        var secure by remember { mutableStateOf(settings.secureScreen) }
        val reminderSettings = remember { ReminderSettings(this) }
        var reminder by remember { mutableStateOf(reminderSettings.enabled) }
        var reminderTime by remember { mutableStateOf("%02d:%02d".format(reminderSettings.hour, reminderSettings.minute)) }
        val validReminderTime = remember(reminderTime) { parseReminderTime(reminderTime) != null }
        Scaffold(topBar = { TopAppBar(title = { Text("外观与隐私") }, navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsCard(Icons.Default.Palette, "显示模式") {
                    ThemeMode.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().clickable { mode = option; settings.themeMode = option }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(mode == option, onClick = null)
                            Text(when (option) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
                        }
                    }
                }
                SettingsCard(Icons.Default.Palette, "主题色") {
                    val colors = listOf("honey" to Color(0xFFFFC928), "green" to Color(0xFF55A86B), "blue" to Color(0xFF4F9DDE), "rose" to Color(0xFFEC7180))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        colors.forEach { (key, color) ->
                            Surface(
                                modifier = Modifier.size(if (accent == key) 52.dp else 44.dp).clickable { accent = key; settings.accent = key },
                                shape = CircleShape,
                                color = color,
                                border = if (accent == key) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            ) {}
                        }
                    }
                }
                SettingsCard(Icons.Default.Security, "隐私保护") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("禁止系统截图和录屏", style = MaterialTheme.typography.titleSmall)
                            Text("开启后，账单页面不会出现在系统截图、录屏和最近任务预览中。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(secure, onCheckedChange = { secure = it; settings.secureScreen = it; settings.applyPrivacy(this@AppearanceSettingsActivity) })
                    }
                }
                SettingsCard(Icons.Default.Notifications, "每日记账提醒") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("每天提醒我记账", style = MaterialTheme.typography.titleSmall)
                            Text("通知可直接进入支出记账页。系统省电策略可能让提醒略有延迟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(reminder, onCheckedChange = {
                            reminder = it; reminderSettings.enabled = it; DailyReminderScheduler.update(this@AppearanceSettingsActivity)
                        })
                    }
                    OutlinedTextField(
                        reminderTime,
                        onValueChange = { reminderTime = it.take(5) },
                        label = { Text("提醒时间（HH:mm）") },
                        singleLine = true,
                        isError = !validReminderTime,
                        enabled = reminder,
                        trailingIcon = { TextButton(onClick = {
                            parseReminderTime(reminderTime)?.let { (hour, minute) ->
                                reminderSettings.hour = hour; reminderSettings.minute = minute; DailyReminderScheduler.update(this@AppearanceSettingsActivity)
                            }
                        }, enabled = reminder && validReminderTime) { Text("保存") } },
                    )
                }
                Text("应用仅使用人民币（CNY），不提供多币种和汇率换算。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    private fun parseReminderTime(value: String): Pair<Int, Int>? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    @Composable
    private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleMedium) }
                content()
            }
        }
    }
}
