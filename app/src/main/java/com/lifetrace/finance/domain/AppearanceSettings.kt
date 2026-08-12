package com.lifetrace.finance.domain

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppearanceSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            if (prefs.edit().putString(KEY_MODE, value.name).commit()) notifyChanged()
        }

    var accent: String
        get() = prefs.getString(KEY_ACCENT, "honey") ?: "honey"
        set(value) {
            require(value in ACCENTS)
            if (prefs.edit().putString(KEY_ACCENT, value).commit()) notifyChanged()
        }

    var secureScreen: Boolean
        get() = prefs.getBoolean(KEY_SECURE, false)
        set(value) {
            if (prefs.edit().putBoolean(KEY_SECURE, value).commit()) notifyChanged()
        }

    fun applyPrivacy(activity: Activity) {
        if (secureScreen) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    companion object {
        const val PREFS = "appearance_settings"
        const val KEY_MODE = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_SECURE = "secure_screen"
        val ACCENTS = setOf("honey", "green", "blue", "rose")
        private val _changes = MutableStateFlow(0L)
        val changes = _changes.asStateFlow()
        private fun notifyChanged() { _changes.value += 1 }
    }
}
