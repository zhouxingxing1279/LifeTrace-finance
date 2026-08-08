package com.lifetrace.finance.platform

import android.content.Intent
import android.service.quicksettings.TileService
import com.lifetrace.finance.MainActivity

class QuickEntryTileService : TileService() {
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("destination", "quick")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivityAndCollapse(intent)
    }
}
