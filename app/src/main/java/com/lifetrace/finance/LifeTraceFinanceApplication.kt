package com.lifetrace.finance

import android.app.Application

class LifeTraceFinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.get(this).screenshotMonitor.restore()
    }
}
