package com.lifetrace.finance

import android.app.Application
import com.lifetrace.finance.sync.SyncScheduler

class LifeTraceFinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Do not eagerly initialize AppGraph here. AppGraph owns AndroidKeyStore-backed
        // credentials and should only be constructed when an app feature actually needs it.
        // WorkManager can register its periodic schedule without opening the database or keystore.
        SyncScheduler.ensurePeriodic(this)
    }
}
