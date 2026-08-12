package com.lifetrace.finance.ui

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.MainActivity
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.launch

class ServerSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(applicationContext)
        val settings = graph.settings
        setContent {
            LifeTraceTheme {
                var baseUrl by remember { mutableStateOf(settings.baseUrl) }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                var message by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                var loggedIn by remember { mutableStateOf(graph.auth.currentUserId != null) }
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("服务器与登录", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "手机连接电脑本地后端时填写 http://127.0.0.1:8787，并保持 USB 调试和 adb reverse 映射。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("LifeTrace Cloud 地址") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                    )
                    Button(
                        onClick = {
                            val saveError = runCatching { settings.baseUrl = baseUrl }.exceptionOrNull()
                            if (saveError == null) finish() else error = saveError.message ?: "服务器地址无效"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存服务器地址") }
                    OutlinedButton(
                        onClick = { baseUrl = "http://127.0.0.1:8787"; error = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("使用电脑本地后端") }
                    Spacer(Modifier.height(8.dp))
                    Text("云端账户", style = MaterialTheme.typography.titleMedium)
                    if (loggedIn) {
                        Text("已登录，可以同步云端账单。", color = MaterialTheme.colorScheme.primary)
                        Button(
                            onClick = { SyncScheduler.scheduleNow(this@ServerSettingsActivity); finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("立即同步并返回") }
                    } else {
                        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true, enabled = !busy)
                        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation())
                        Button(
                            onClick = {
                                error = null
                                message = null
                                val saveError = runCatching { settings.baseUrl = baseUrl }.exceptionOrNull()
                                if (saveError != null) {
                                    error = saveError.message ?: "服务器地址无效"
                                } else if (email.isBlank() || password.isBlank()) {
                                    error = "请输入邮箱和密码"
                                } else {
                                    busy = true
                                    lifecycleScope.launch {
                                        runCatching {
                                            graph.auth.login(email.trim(), password)
                                            val userId = requireNotNull(graph.auth.currentUserId)
                                            val profile = graph.finance.activateCloudProfile(userId)
                                            graph.finance.ensureDefaultLedger(profile.id)
                                            graph.finance.ensureStandardCategories(profile.id)
                                        }.onSuccess {
                                            loggedIn = true
                                            busy = false
                                            message = "登录成功，正在同步云端账单"
                                            SyncScheduler.scheduleNow(this@ServerSettingsActivity)
                                            startActivity(Intent(this@ServerSettingsActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                            finish()
                                        }.onFailure {
                                            busy = false
                                            error = "登录失败：${it.message ?: "未知错误"}"
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "登录中…" else "登录并同步") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(4.dp))
                    Text("生产环境请使用 HTTPS 地址。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
