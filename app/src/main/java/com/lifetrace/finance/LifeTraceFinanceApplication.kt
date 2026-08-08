package com.lifetrace.finance

import android.app.Application
import com.lifetrace.finance.sync.SyncScheduler

class LifeTraceFinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.get(this)
        SyncScheduler.ensurePeriodic(this)
    }
}
