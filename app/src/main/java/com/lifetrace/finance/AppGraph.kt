package com.lifetrace.finance

import android.content.Context
import com.lifetrace.finance.auth.AuthManager
import com.lifetrace.finance.auth.SecureTokenStore
import com.lifetrace.finance.data.Diagnostics
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.domain.FinanceRepository
import com.lifetrace.finance.network.LifeTraceApi
import com.lifetrace.finance.network.SettingsStore
import com.lifetrace.finance.sync.SyncEngine

class AppGraph private constructor(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsStore(app)
    val db = FinanceDatabase.get(app)
    val diagnostics = Diagnostics(db.diagnosticDao())
    val api = LifeTraceApi(baseUrlProvider = { settings.baseUrl })
    val tokenStore = SecureTokenStore(app)
    val auth = AuthManager(app, api, tokenStore)
    val finance = FinanceRepository(db, auth.deviceId)
    val syncEngine = SyncEngine(db, api, auth, diagnostics)

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context).also { instance = it }
        }
    }
}
