package com.lifetrace.finance.automation

import android.content.Context
import java.io.File
import org.json.JSONArray

/**
 * App-private handoff used when a shared screenshot arrives before Vision is configured.
 * The file path never travels through an exported activity Intent.
 */
class PendingShareStore(context: Context) {
    private val prefs = context.getSharedPreferences("smart_capture_pending_share", Context.MODE_PRIVATE)

    @Synchronized
    fun save(path: String) = saveAll(listOf(path))

    @Synchronized
    fun saveAll(paths: List<String>) {
        val normalized = paths.filter(String::isNotBlank).distinct()
        val retained = normalized.toSet()
        peekAll().filterNot { it in retained }.forEach { old -> runCatching { File(old).delete() } }
        prefs.edit()
            .remove(KEY_PATH)
            .putString(KEY_PATHS, JSONArray(normalized).toString())
            .apply()
    }

    fun peek(): String? = peekAll().firstOrNull()

    fun peekAll(): List<String> {
        val encoded = prefs.getString(KEY_PATHS, null)
        if (!encoded.isNullOrBlank()) {
            return runCatching {
                val array = JSONArray(encoded)
                buildList { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
            }.getOrDefault(emptyList())
        }
        return prefs.getString(KEY_PATH, null)?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
    }

    @Synchronized
    fun consume(): String? {
        val values = consumeAll()
        return values.firstOrNull().also {
            values.drop(1).forEach { path -> runCatching { File(path).delete() } }
        }
    }

    @Synchronized
    fun consumeAll(): List<String> {
        val values = peekAll()
        prefs.edit().remove(KEY_PATH).remove(KEY_PATHS).apply()
        return values
    }

    @Synchronized
    fun clear() {
        peekAll().forEach { path -> runCatching { File(path).delete() } }
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PATH = "path"
        const val KEY_PATHS = "paths"
    }
}
