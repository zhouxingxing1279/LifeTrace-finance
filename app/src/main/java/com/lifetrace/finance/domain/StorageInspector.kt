package com.lifetrace.finance.domain

import android.content.Context
import java.io.File

data class StorageSnapshot(
    val databaseBytes: Long,
    val attachmentBytes: Long,
    val cacheBytes: Long,
    val preferenceBytes: Long,
) {
    val totalBytes: Long get() = databaseBytes + attachmentBytes + cacheBytes + preferenceBytes
}

class StorageInspector(private val context: Context) {
    fun snapshot(): StorageSnapshot = StorageSnapshot(
        databaseBytes = context.getDatabasePath("finance.db").let { db -> fileSize(db) + fileSize(File(db.path + "-wal")) + fileSize(File(db.path + "-shm")) },
        attachmentBytes = directorySize(File(context.filesDir, "transaction_attachments")),
        cacheBytes = directorySize(context.cacheDir),
        preferenceBytes = directorySize(File(context.applicationInfo.dataDir, "shared_prefs")),
    )

    fun clearCache(): Long {
        val before = directorySize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach(::deleteTreeContents)
        return before - directorySize(context.cacheDir)
    }

    private fun deleteTreeContents(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTreeContents)
        file.delete()
    }

    private fun directorySize(file: File): Long = if (!file.exists()) 0L else if (file.isFile) file.length() else file.listFiles()?.sumOf(::directorySize) ?: 0L
    private fun fileSize(file: File): Long = file.takeIf(File::isFile)?.length() ?: 0L
}

fun formatStorageSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.2f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.2f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
