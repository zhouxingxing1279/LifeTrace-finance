package com.lifetrace.finance.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.MainActivity
import com.lifetrace.finance.importer.BillImportActivity
import com.lifetrace.finance.ui.AiSettingsActivity
import java.io.File
import java.util.UUID

class ShareReceiverActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val type = intent.type.orEmpty()
        var destination = "quick"
        var sharedText: String? = null

        when {
            type.startsWith("image/") -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    val cached = cacheSharedFile(uri)
                    val graph = AppGraph.get(applicationContext)
                    if (!graph.aiProviderFactory.isVisionConfigured()) {
                        graph.pendingShare.save(cached.absolutePath)
                        startActivity(Intent(this, AiSettingsActivity::class.java))
                        finish()
                        return
                    }
                    graph.autoBilling.submitImage(Uri.fromFile(cached), source = "share_receiver", deleteAfter = true)
                    destination = "inbox"
                }
            }
            type.startsWith("text/") && type != "text/csv" -> sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            isBillFileMime(type) || type == "text/csv" -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    val cached = cacheSharedFile(uri)
                    startActivity(
                        Intent(this, BillImportActivity::class.java)
                            .putExtra(BillImportActivity.EXTRA_FILE_URI, Uri.fromFile(cached).toString()),
                    )
                    finish()
                    return
                }
            }
            else -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                if (uri != null && looksLikeBillFile(uri)) {
                    val cached = cacheSharedFile(uri)
                    startActivity(
                        Intent(this, BillImportActivity::class.java)
                            .putExtra(BillImportActivity.EXTRA_FILE_URI, Uri.fromFile(cached).toString()),
                    )
                    finish()
                    return
                }
                val display = uri?.let(::displayName)?.takeLast(80) ?: "共享内容"
                sharedText = "已接收共享内容：$display"
            }
        }

        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("destination", destination)
                .putExtra("shared_text", sharedText)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    private fun isBillFileMime(type: String): Boolean = type in setOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/comma-separated-values",
    )

    private fun looksLikeBillFile(uri: Uri): Boolean {
        val name = displayName(uri).lowercase()
        return name.endsWith(".csv") || name.endsWith(".xlsx") || name.endsWith(".xls")
    }

    private fun cacheSharedFile(uri: Uri): File {
        val directory = File(cacheDir, "shared").apply { mkdirs() }
        val original = displayName(uri).replace(Regex("[^\\p{L}\\p{N}._-]"), "_").takeLast(120)
        val target = File(directory, "${UUID.randomUUID()}_$original")
        contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
            ?: error("无法读取共享文件")
        return target
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
}
