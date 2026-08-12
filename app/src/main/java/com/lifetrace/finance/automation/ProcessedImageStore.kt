package com.lifetrace.finance.automation

import android.content.Context
import org.json.JSONArray

/** BeeCount-style bounded processed-image memory, keyed by image SHA-256. */
class ProcessedImageStore(context: Context) {
    private val prefs = context.getSharedPreferences("smart_capture_processed", Context.MODE_PRIVATE)

    @Synchronized
    fun contains(hash: String): Boolean = load().contains(hash)

    @Synchronized
    fun remember(hash: String) {
        val current = load().filterNot { it == hash }.toMutableList()
        current += hash
        while (current.size > AutoBillingConfig.MAX_PROCESSED_IMAGES) current.removeAt(0)
        prefs.edit().putString("hashes", JSONArray(current).toString()).apply()
    }

    @Synchronized
    fun clear() = prefs.edit().clear().apply()

    private fun load(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("hashes", "[]"))
        buildList { for (i in 0 until array.length()) array.optString(i)?.takeIf(String::isNotBlank)?.let(::add) }
    }.getOrDefault(emptyList())
}
