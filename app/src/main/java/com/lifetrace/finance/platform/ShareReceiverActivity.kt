package com.lifetrace.finance.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.lifetrace.finance.MainActivity
import java.io.File
import java.util.UUID

class ShareReceiverActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = when {
            intent?.action != Intent.ACTION_SEND -> null
            intent.type?.startsWith("text/") == true -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                uri?.let(::cacheSharedFile)
            }
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("destination", "quick")
                .putExtra("shared_text", sharedText)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun cacheSharedFile(uri: Uri): String {
        val directory = File(cacheDir, "shared").apply { mkdirs() }
        val target = File(directory, UUID.randomUUID().toString())
        contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
        val display = uri.lastPathSegment?.takeLast(80) ?: "共享文件"
        return "已接收共享文件：$display。正式账单文件解析属于 EPIC-06，当前仅保留本地临时副本供后续确认。"
    }
}
