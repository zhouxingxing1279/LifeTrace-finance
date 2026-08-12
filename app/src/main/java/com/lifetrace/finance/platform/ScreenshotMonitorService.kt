package com.lifetrace.finance.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.lifetrace.finance.ai.AiSettingsStore
import com.lifetrace.finance.automation.AutoBillingService

/** Process-lifetime monitor matching BeeCount's ScreenshotMonitorService responsibility. */
class ScreenshotMonitorService(
    context: Context,
    private val settings: AiSettingsStore,
    autoBilling: AutoBillingService,
) {
    private val app = context.applicationContext
    private val observer = ScreenshotObserver(app.contentResolver, autoBilling)
    @Volatile private var registered = false

    @Synchronized
    fun restore() {
        if (settings.screenshotMonitorEnabled && hasMediaPermission()) start()
    }

    @Synchronized
    fun start(): Boolean {
        if (registered) return true
        if (!hasMediaPermission()) return false
        app.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        registered = true
        settings.screenshotMonitorEnabled = true
        return true
    }

    @Synchronized
    fun stop() {
        if (registered) runCatching { app.contentResolver.unregisterContentObserver(observer) }
        registered = false
        settings.screenshotMonitorEnabled = false
    }

    fun isRunning(): Boolean = registered
    fun isEnabled(): Boolean = settings.screenshotMonitorEnabled

    fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
}
