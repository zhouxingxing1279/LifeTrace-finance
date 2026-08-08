package com.lifetrace.finance.network

import android.content.Context
import com.lifetrace.finance.BuildConfig

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = prefs.getString("base_url", BuildConfig.LIFETRACE_DEFAULT_BASE_URL) ?: BuildConfig.LIFETRACE_DEFAULT_BASE_URL
        set(value) {
            val normalized = value.trim().trimEnd('/')
            require(normalized.startsWith("https://") || (BuildConfig.DEBUG && normalized.startsWith("http://"))) {
                "服务器地址必须使用 HTTPS（Debug 可使用 HTTP）"
            }
            prefs.edit().putString("base_url", normalized).apply()
        }
}
