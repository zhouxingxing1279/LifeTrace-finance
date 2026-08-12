package com.lifetrace.finance

import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.domain.StorageInspector
import com.lifetrace.finance.domain.formatStorageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StorageInspectorTest {
    @Test fun scansAndClearsOnlyCacheFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cache = File(context.cacheDir, "temporary.bin").apply { writeBytes(ByteArray(2048)) }
        val attachment = File(context.filesDir, "transaction_attachments/keep.bin").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(1024)) }
        val inspector = StorageInspector(context)
        val before = inspector.snapshot()
        assertTrue(before.cacheBytes >= 2048)
        assertTrue(before.attachmentBytes >= 1024)
        assertTrue(inspector.clearCache() >= 2048)
        assertTrue(!cache.exists())
        assertTrue(attachment.exists())
    }

    @Test fun formatsStorageUnits() {
        assertEquals("0 B", formatStorageSize(0))
        assertEquals("1.00 KB", formatStorageSize(1024))
        assertEquals("1.00 MB", formatStorageSize(1024L * 1024))
    }
}
