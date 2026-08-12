package com.lifetrace.finance.ai

import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** AI boundary: image understanding only. It never writes transactions itself. */
class AiBookkeeper(
    private val finance: FinanceRepository,
    private val engine: DefaultAiExtractionEngine,
) {
    suspend fun fromImage(profileId: String, image: VisionImage): Pair<AiVisionResult, List<BillInfo>> {
        val accounts = finance.accounts(profileId).first().map { it.name }
        val categories = finance.categories(profileId).first().map { it.name }
        val context = BillingContext(
            accountNames = accounts,
            categoryNames = categories,
            timezoneId = ZoneId.systemDefault().id,
        )
        return engine.extractFromImage(image, context)
    }
}
