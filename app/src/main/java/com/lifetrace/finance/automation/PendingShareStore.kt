package com.lifetrace.finance.automation

import android.content.Context
import java.io.File

/**
 * App-private handoff used when a shared screenshot arrives before Vision is configured.
 * The file path never travels through an exported activity Intent.
 */
class PendingShareStore(context: Context) {
    private val prefs = context.getSharedPreferences("smart_capture_pending_share", Context.MODE_PRIVATE)

    @Synchronized
    fun save(path: String) {
        peek()?.takeIf { it != path }?.let { old -> runCatching { File(old).delete() } }
        prefs.edit().putString(KEY_PATH, path).apply()
    }

    fun peek(): String? = prefs.getString(KEY_PATH, null)?.takeIf(String::isNotBlank)

    @Synchronized
    fun consume(): String? {
        val value = peek()
        prefs.edit().remove(KEY_PATH).apply()
        return value
    }

    @Synchronized
    fun clear() {
        peek()?.let { path -> runCatching { File(path).delete() } }
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PATH = "path"
    }
}
