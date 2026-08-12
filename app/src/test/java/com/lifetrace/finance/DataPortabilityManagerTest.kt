package com.lifetrace.finance

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.domain.DataPortabilityManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataPortabilityManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun validatesCompatibleBackupBeforeRestore() = runBlocking {
        val backup = File(context.cacheDir, "valid-finance-backup.zip")
        ZipOutputStream(backup.outputStream()).use { zip ->
            entry(zip, "manifest.json", JSONObject().put("format", "lifetrace-finance-backup").put("databaseVersion", 2).put("transactionCount", 7).put("exportedAt", "2026-08-13T00:00:00Z").toString().toByteArray())
            entry(zip, "database/lifetrace-finance.db", "SQLite format 3\u0000rest".toByteArray(Charsets.US_ASCII))
            entry(zip, "attachments/a.jpg", byteArrayOf(1, 2, 3))
        }

        val preview = DataPortabilityManager(context).stageRestore(Uri.fromFile(backup))

        assertEquals(7, preview.transactionCount)
        assertEquals(1, preview.attachmentCount)
    }

    @Test fun rejectsZipPathTraversal() = runBlocking {
        val backup = File(context.cacheDir, "unsafe-finance-backup.zip")
        ZipOutputStream(backup.outputStream()).use { zip -> entry(zip, "../outside.txt", "bad".toByteArray()) }

        val failure = runCatching { DataPortabilityManager(context).stageRestore(Uri.fromFile(backup)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun entry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
    }
}
