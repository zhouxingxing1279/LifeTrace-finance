package com.lifetrace.finance

import android.content.Context
import com.lifetrace.finance.ai.AiBookkeeper
import com.lifetrace.finance.ai.AiProviderFactory
import com.lifetrace.finance.ai.AiSecretStore
import com.lifetrace.finance.ai.AiSettingsStore
import com.lifetrace.finance.ai.DefaultAiExtractionEngine
import com.lifetrace.finance.auth.AuthManager
import com.lifetrace.finance.auth.SecureTokenStore
import com.lifetrace.finance.automation.AutoBillingService
import com.lifetrace.finance.automation.BillCreationService
import com.lifetrace.finance.automation.PendingShareStore
import com.lifetrace.finance.automation.ProcessedImageStore
import com.lifetrace.finance.data.Diagnostics
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.domain.BookkeepingManager
import com.lifetrace.finance.domain.FinanceRepository
import com.lifetrace.finance.domain.LedgerSelectionStore
import com.lifetrace.finance.importer.BillImportService
import com.lifetrace.finance.network.LifeTraceApi
import com.lifetrace.finance.network.SettingsStore
import com.lifetrace.finance.platform.ScreenshotMonitorService
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
    val ledgerSelection = LedgerSelectionStore(app)
    val bookkeeping = BookkeepingManager(db, finance, auth.deviceId)
    val billImport = BillImportService(db, finance, auth.deviceId)
    val syncEngine = SyncEngine(db, api, auth, diagnostics)

    // BeeCount-style image billing stack. The Vision request is made directly by Android.
    val aiSettings = AiSettingsStore(app)
    val aiSecrets = AiSecretStore(app)
    val aiProviderFactory = AiProviderFactory(aiSettings, aiSecrets)
    val aiExtractionEngine = DefaultAiExtractionEngine(aiProviderFactory)
    val aiBookkeeper = AiBookkeeper(finance, aiExtractionEngine)
    val billCreation = BillCreationService(finance)
    val processedImages = ProcessedImageStore(app)
    val pendingShare = PendingShareStore(app)
    val autoBilling = AutoBillingService(
        context = app,
        finance = finance,
        aiBookkeeper = aiBookkeeper,
        providerFactory = aiProviderFactory,
        billCreation = billCreation,
        processedImages = processedImages,
        diagnostics = diagnostics,
    )
    val screenshotMonitor = ScreenshotMonitorService(app, aiSettings, autoBilling)

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context).also { instance = it }
        }
    }
}
