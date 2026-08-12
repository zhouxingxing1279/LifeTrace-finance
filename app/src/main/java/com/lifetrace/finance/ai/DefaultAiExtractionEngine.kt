package com.lifetrace.finance.ai

class DefaultAiExtractionEngine(
    private val providerFactory: AiProviderFactory,
) {
    suspend fun extractFromImage(image: VisionImage, context: BillingContext): Pair<AiVisionResult, List<BillInfo>> {
        val result = providerFactory.vision(image, PromptBuilder.billGuardForImage(context))
        return result to JsonResponseParser.parseBills(result.content)
    }
}
