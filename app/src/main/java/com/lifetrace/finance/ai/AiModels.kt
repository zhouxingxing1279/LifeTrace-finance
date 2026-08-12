package com.lifetrace.finance.ai

import com.lifetrace.finance.core.TransactionType
import java.time.Instant

/** Mirrors BeeCount's provider + capability split while keeping only the Vision capability LifeTrace needs. */
data class AiServiceProviderConfig(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val baseUrl: String,
    val visionModel: String,
) {
    val supportsVision: Boolean get() = visionModel.isNotBlank()

    companion object {
        val ZHIPU_DEFAULT = AiServiceProviderConfig(
            id = "zhipu_glm",
            name = "智谱GLM",
            isBuiltIn = true,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            visionModel = "glm-4v-flash",
        )
    }
}

data class VisionImage(
    val bytes: ByteArray,
    val mimeType: String,
)

data class BillingContext(
    val accountNames: List<String>,
    val categoryNames: List<String>,
    val currentTime: Instant = Instant.now(),
    val timezoneId: String,
)

data class BillInfo(
    val amountCents: Long,
    val currency: String = "CNY",
    val type: TransactionType,
    val merchant: String? = null,
    val item: String? = null,
    val occurredAt: Instant? = null,
    val account: String? = null,
    val category: String? = null,
    val externalTransactionId: String? = null,
    val confidence: Double? = null,
)

data class AiVisionResult(
    val providerId: String,
    val model: String,
    val content: String,
)
