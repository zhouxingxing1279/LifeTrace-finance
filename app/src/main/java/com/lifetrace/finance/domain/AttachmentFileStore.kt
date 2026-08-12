package com.lifetrace.finance.domain

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.security.DigestInputStream
import java.util.UUID

data class ImportedAttachment(
    val fileName: String,
    val originalName: String?,
    val fileSize: Long,
    val width: Int?,
    val height: Int?,
    val sha256: String,
)

class AttachmentFileStore(private val context: Context) {
    private val root get() = File(context.filesDir, "transaction_attachments").apply { mkdirs() }

    fun import(uri: Uri): ImportedAttachment {
        var originalName: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) originalName = cursor.getString(0)
        }
        val extension = originalName?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        val fileName = UUID.randomUUID().toString() + extension?.let { ".$it" }.orEmpty()
        val target = File(root, fileName)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选文件" }
            DigestInputStream(input, messageDigest).use { digestInput -> target.outputStream().use(digestInput::copyTo) }
        }
        val digest = messageDigest.digest().joinToString("") { "%02x".format(it) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.path, bounds)
        return ImportedAttachment(fileName, originalName, target.length(), bounds.outWidth.takeIf { it > 0 }, bounds.outHeight.takeIf { it > 0 }, digest)
    }

    fun contentUri(fileName: String): Uri? {
        val file = File(root, fileName)
        if (!file.isFile || !file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun delete(fileName: String) {
        val file = File(root, fileName)
        if (file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && file.isFile) file.delete()
    }
}
