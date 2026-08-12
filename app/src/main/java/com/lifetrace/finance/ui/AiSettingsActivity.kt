package com.lifetrace.finance.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lifetrace.finance.AppGraph
import java.io.File

/** Dedicated settings surface for BeeCount-style Android-direct Vision billing. */
class AiSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(applicationContext)
        val hasPendingImage = graph.pendingShare.peek() != null

        setContent {
            LifeTraceTheme {
                var baseUrl by remember { mutableStateOf(graph.aiSettings.baseUrl) }
                var model by remember { mutableStateOf(graph.aiSettings.visionModel) }
                var apiKey by remember { mutableStateOf("") }
                var monitorEnabled by remember { mutableStateOf(graph.screenshotMonitor.isEnabled()) }
                var message by remember { mutableStateOf<String?>(null) }
                var error by remember { mutableStateOf<String?>(null) }

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted && graph.screenshotMonitor.start()) {
                        monitorEnabled = true
                        message = "图片权限已授予，自动截图监听已开启"
                        error = null
                    } else {
                        monitorEnabled = false
                        error = "未获得图片权限；仍可通过系统分享截图记账"
                    }
                }

                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("智能截图记账", style = MaterialTheme.typography.headlineSmall)
                    Text("截图由 Android 直接发送给 Vision API；LifeTrace Cloud、桌面端和浏览器端不会接收原始图片。", style = MaterialTheme.typography.bodyMedium)
                    if (hasPendingImage) {
                        Text("已收到一张待识别截图，保存配置后会自动继续识别。", color = MaterialTheme.colorScheme.primary)
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Vision 服务", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("视觉模型") }, singleLine = true)
                            OutlinedTextField(
                                apiKey,
                                { apiKey = it },
                                Modifier.fillMaxWidth(),
                                label = { Text(if (graph.aiSecrets.hasApiKey()) "API Key（已配置，留空表示不修改）" else "API Key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Text("默认与 BeeCount 一致：智谱 GLM / glm-4v-flash。API Key 使用 Android Keystore 加密保存在本机。", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("自动监听新截图", style = MaterialTheme.typography.titleMedium)
                                Text("关闭时仍可从系统分享图片到 LifeTrace Finance。", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = monitorEnabled, onCheckedChange = { monitorEnabled = it })
                        }
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val saveError = runCatching {
                                graph.aiSettings.baseUrl = baseUrl
                                graph.aiSettings.visionModel = model
                                if (apiKey.isNotBlank()) graph.aiSecrets.saveApiKey(apiKey)
                                require(graph.aiSecrets.hasApiKey()) { "请填写 API Key" }
                                if (!monitorEnabled) graph.screenshotMonitor.stop()
                            }.exceptionOrNull()
                            if (saveError != null) {
                                error = saveError.message ?: "保存失败"
                            } else {
                                if (monitorEnabled) {
                                    if (graph.screenshotMonitor.hasMediaPermission()) graph.screenshotMonitor.start()
                                    else permissionLauncher.launch(graph.screenshotMonitor.requiredPermission())
                                }
                                val pendingImagePath = graph.pendingShare.consume()
                                pendingImagePath?.let { path ->
                                    graph.autoBilling.submitImage(Uri.fromFile(File(path)), "share_receiver", deleteAfter = true)
                                }
                                message = "Vision 配置已保存"
                                error = null
                                if (pendingImagePath == null) finish()
                                else {
                                    startActivity(android.content.Intent(this@AiSettingsActivity, com.lifetrace.finance.MainActivity::class.java).putExtra("destination", "inbox"))
                                    finish()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存") }

                    OutlinedButton(
                        onClick = {
                            graph.aiSecrets.saveApiKey(null)
                            graph.screenshotMonitor.stop()
                            monitorEnabled = false
                            apiKey = ""
                            message = "API Key 已从本机删除"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除本机 API Key") }
                }
            }
        }
    }
}
