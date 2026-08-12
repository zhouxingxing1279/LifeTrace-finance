package com.lifetrace.finance.platform

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.lifetrace.finance.automation.AutoBillingConfig
import com.lifetrace.finance.automation.AutoBillingService

/** Android-native equivalent of BeeCount's ScreenshotObserver. */
class ScreenshotObserver(
    private val resolver: ContentResolver,
    private val autoBilling: AutoBillingService,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private var lastDispatchAt = 0L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val now = System.currentTimeMillis()
        if (now - lastDispatchAt < AutoBillingConfig.OBSERVER_DEBOUNCE_MS) return
        val candidate = queryCandidate(uri) ?: return
        if (!isScreenshot(candidate.name, candidate.path)) return
        if (now - candidate.createdAtMillis > AutoBillingConfig.SCREENSHOT_MAX_AGE_MS) return
        lastDispatchAt = now
        autoBilling.submitImage(candidate.uri, "screenshot_monitor")
    }

    private fun queryCandidate(changedUri: Uri?): Candidate? {
        val target = changedUri?.takeIf { it != MediaStore.Images.Media.EXTERNAL_CONTENT_URI }
            ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
            else add(MediaStore.Images.Media.DATA)
        }.toTypedArray()
        val sort = if (target == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1" else null
        return runCatching {
            resolver.query(target, projection, null, null, sort)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)).orEmpty()
                val seconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
                val pathIndex = cursor.getColumnIndex(pathColumn)
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                val itemUri = if (target == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else target
                Candidate(itemUri, name, path, seconds * 1_000L)
            }
        }.getOrNull()
    }

    private data class Candidate(val uri: Uri, val name: String, val path: String, val createdAtMillis: Long)

    companion object {
        val SCREENSHOT_KEYWORDS = listOf("screenshot", "截屏", "截图", "screen_shot", "screen shot")

        fun isScreenshot(name: String, path: String): Boolean {
            val value = "$path/$name".lowercase()
            return SCREENSHOT_KEYWORDS.any(value::contains)
        }
    }
}
