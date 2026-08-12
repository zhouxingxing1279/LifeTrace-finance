package com.lifetrace.finance.automation

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.lifetrace.finance.ai.AiBookkeeper
import com.lifetrace.finance.ai.AiProviderFactory
import com.lifetrace.finance.ai.VisionImage
import com.lifetrace.finance.data.Diagnostics
import com.lifetrace.finance.domain.FinanceRepository
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

data class SmartCaptureState(
    val running: Boolean = false,
    val lastMessage: String? = null,
    val lastError: String? = null,
    val lastBillCount: Int = 0,
)

class AutoBillingService(
    context: Context,
    private val finance: FinanceRepository,
    private val aiBookkeeper: AiBookkeeper,
    private val providerFactory: AiProviderFactory,
    private val billCreation: BillCreationService,
    private val processedImages: ProcessedImageStore,
    private val diagnostics: Diagnostics,
) {
    private val app = context.applicationContext
    private val resolver: ContentResolver = app.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SmartCaptureState())
    val state = _state.asStateFlow()

    fun submitImage(uri: Uri, source: String, deleteAfter: Boolean = false): Job = scope.launch {
        processImage(uri, source, deleteAfter)
    }

    suspend fun processImage(uri: Uri, source: String, deleteAfter: Boolean = false) {
        _state.value = SmartCaptureState(running = true, lastMessage = "正在识别账单截图")
        try {
            if (!providerFactory.isVisionConfigured()) error("请先配置 Vision API Key")
            val bytes = waitAndRead(uri) ?: error("图片尚未写入完成或无法读取")
            val hash = sha256(bytes)
            if (processedImages.contains(hash)) {
                _state.value = SmartCaptureState(lastMessage = "这张截图已经处理过")
                return
            }
            val mime = detectMime(bytes) ?: error("仅支持 PNG/JPEG/WebP 图片")
            val profile = finance.ensureProfile()
            val (visionResult, bills) = aiBookkeeper.fromImage(profile.id, VisionImage(bytes, mime))
            if (bills.isEmpty()) {
                processedImages.remember(hash)
                diagnostics.event("SMART_CAPTURE", "NOT_A_BILL", "image rejected by bill guard source=$source")
                _state.value = SmartCaptureState(lastMessage = "图片未识别为账单")
                return
            }

            val creation = billCreation.createBills(
                profileId = profile.id,
                bills = bills,
                sourceType = "vision_screenshot:${visionResult.providerId}:${visionResult.model}",
            )
            if (creation.transactionIds.isEmpty()) {
                error("识别到 ${bills.size} 笔交易，但没有可创建的账单")
            }
            processedImages.remember(hash)
            diagnostics.event(
                "SMART_CAPTURE",
                "BILLS_CREATED",
                "created=${creation.transactionIds.size} skipped=${creation.skipped} source=$source provider=${visionResult.providerId} model=${visionResult.model}",
            )
            SyncScheduler.scheduleNow(app)
            _state.value = SmartCaptureState(
                lastMessage = "已识别 ${creation.transactionIds.size} 笔账单，请在待确认中检查",
                lastBillCount = creation.transactionIds.size,
            )
        } catch (error: Throwable) {
            val message = error.message?.take(180) ?: "截图识别失败"
            diagnostics.event("SMART_CAPTURE", "FAILED", message, level = "WARN")
            _state.value = SmartCaptureState(lastError = message)
        } finally {
            if (deleteAfter && uri.scheme == "file") runCatching { uri.path?.let(::File)?.delete() }
            if (_state.value.running) _state.value = _state.value.copy(running = false)
        }
    }

    private suspend fun waitAndRead(uri: Uri): ByteArray? {
        val deadline = System.currentTimeMillis() + AutoBillingConfig.FILE_READY_TIMEOUT_MS
        while (System.currentTimeMillis() <= deadline) {
            val bytes = runCatching { readBytes(uri) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) return bytes
            delay(AutoBillingConfig.FILE_READY_POLL_MS)
        }
        return null
    }

    private fun readBytes(uri: Uri): ByteArray = when (uri.scheme) {
        "file" -> File(requireNotNull(uri.path)).readBytes()
        else -> resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法打开图片")
    }

    private fun detectMime(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
