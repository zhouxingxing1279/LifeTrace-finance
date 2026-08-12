package com.lifetrace.finance.domain

import android.content.Context

class ReminderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var hour: Int
        get() = prefs.getInt("hour", 21)
        set(value) { require(value in 0..23); prefs.edit().putInt("hour", value).apply() }

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(value) { require(value in 0..59); prefs.edit().putInt("minute", value).apply() }
}
