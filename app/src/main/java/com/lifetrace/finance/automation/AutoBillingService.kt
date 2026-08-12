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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

data class SmartCaptureState(
    val running: Boolean = false,
    val lastMessage: String? = null,
    val lastError: String? = null,
    val lastBillCount: Int = 0,
)

private data class ImageProcessingResult(
    val createdBills: Int = 0,
    val skipped: Boolean = false,
    val error: String? = null,
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
    private val processingMutex = Mutex()
    private val _state = MutableStateFlow(SmartCaptureState())
    val state = _state.asStateFlow()

    fun submitImage(uri: Uri, source: String, deleteAfter: Boolean = false): Job = scope.launch {
        processingMutex.withLock { processImageInternal(uri, source, deleteAfter, publishState = true) }
    }

    fun submitImages(uris: List<Uri>, source: String, deleteAfter: Boolean = false): Job = scope.launch {
        processingMutex.withLock {
            val images = uris.distinctBy(Uri::toString)
            if (images.isEmpty()) return@withLock
            _state.value = SmartCaptureState(running = true, lastMessage = "正在识别 0/${images.size} 张图片")
            var createdBills = 0
            var succeededImages = 0
            var skippedImages = 0
            var failedImages = 0
            images.forEachIndexed { index, uri ->
                _state.value = SmartCaptureState(
                    running = true,
                    lastMessage = "正在识别 ${index + 1}/${images.size} 张图片",
                    lastBillCount = createdBills,
                )
                val result = processImageInternal(uri, source, deleteAfter, publishState = false)
                createdBills += result.createdBills
                when {
                    result.error != null -> failedImages++
                    result.skipped -> skippedImages++
                    else -> succeededImages++
                }
            }
            val summary = "批量识别完成：成功 $succeededImages 张，跳过 $skippedImages 张，失败 $failedImages 张，生成 $createdBills 笔待确认账单"
            diagnostics.event("SMART_CAPTURE", "BATCH_COMPLETED", summary)
            _state.value = if (failedImages == images.size) {
                SmartCaptureState(lastError = summary, lastBillCount = createdBills)
            } else {
                SmartCaptureState(lastMessage = summary, lastBillCount = createdBills)
            }
        }
    }

    suspend fun processImage(uri: Uri, source: String, deleteAfter: Boolean = false) {
        processingMutex.withLock { processImageInternal(uri, source, deleteAfter, publishState = true) }
    }

    private suspend fun processImageInternal(uri: Uri, source: String, deleteAfter: Boolean, publishState: Boolean): ImageProcessingResult {
        if (publishState) _state.value = SmartCaptureState(running = true, lastMessage = "正在识别账单截图")
        try {
            if (!providerFactory.isVisionConfigured()) error("请先配置 Vision API Key")
            val bytes = waitAndRead(uri) ?: error("图片尚未写入完成或无法读取")
            val hash = sha256(bytes)
            if (processedImages.contains(hash)) {
                if (publishState) _state.value = SmartCaptureState(lastMessage = "这张截图已经处理过")
                return ImageProcessingResult(skipped = true)
            }
            val mime = detectMime(bytes) ?: error("仅支持 PNG/JPEG/WebP 图片")
            val profile = finance.ensureProfile()
            val (visionResult, bills) = aiBookkeeper.fromImage(profile.id, VisionImage(bytes, mime))
            if (bills.isEmpty()) {
                processedImages.remember(hash)
                diagnostics.event("SMART_CAPTURE", "NOT_A_BILL", "image rejected by bill guard source=$source")
                if (publishState) _state.value = SmartCaptureState(lastMessage = "图片未识别为账单")
                return ImageProcessingResult(skipped = true)
            }

            val creation = billCreation.createBills(
                profileId = profile.id,
                bills = bills,
                sourceType = "vision_screenshot:${visionResult.providerId}:${visionResult.model}",
            )
            if (creation.transactionIds.isEmpty()) {
                processedImages.remember(hash)
                diagnostics.event(
                    "SMART_CAPTURE",
                    "BILLS_SKIPPED",
                    "recognized=${bills.size} skipped=${creation.skipped} source=$source provider=${visionResult.providerId} model=${visionResult.model}",
                )
                if (publishState) _state.value = SmartCaptureState(lastMessage = "识别到的账单已存在或信息不足，未重复记入")
                return ImageProcessingResult(skipped = true)
            }
            processedImages.remember(hash)
            diagnostics.event(
                "SMART_CAPTURE",
                "BILLS_CREATED",
                "created=${creation.transactionIds.size} skipped=${creation.skipped} source=$source provider=${visionResult.providerId} model=${visionResult.model}",
            )
            SyncScheduler.scheduleNow(app)
            if (publishState) _state.value = SmartCaptureState(lastMessage = "已识别 ${creation.transactionIds.size} 笔账单，请确认后入账", lastBillCount = creation.transactionIds.size)
            return ImageProcessingResult(createdBills = creation.transactionIds.size)
        } catch (error: Throwable) {
            val message = error.message?.take(180) ?: "截图识别失败"
            diagnostics.event("SMART_CAPTURE", "FAILED", message, level = "WARN")
            if (publishState) _state.value = SmartCaptureState(lastError = message)
            return ImageProcessingResult(error = message)
        } finally {
            if (deleteAfter && uri.scheme == "file") runCatching { uri.path?.let(::File)?.delete() }
            if (publishState && _state.value.running) _state.value = _state.value.copy(running = false)
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
