package com.lifetrace.finance.ai

import android.content.Context

/** Non-secret AI settings. Secrets are stored separately in [AiSecretStore]. */
class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    var providerId: String
        get() = prefs.getString("vision_provider_id", AiServiceProviderConfig.ZHIPU_DEFAULT.id)
            ?: AiServiceProviderConfig.ZHIPU_DEFAULT.id
        set(value) { prefs.edit().putString("vision_provider_id", value.trim()).apply() }

    var baseUrl: String
        get() = prefs.getString("vision_base_url", AiServiceProviderConfig.ZHIPU_DEFAULT.baseUrl)
            ?: AiServiceProviderConfig.ZHIPU_DEFAULT.baseUrl
        set(value) {
            val normalized = value.trim().trimEnd('/')
            require(normalized.startsWith("https://")) { "AI Base URL 必须使用 HTTPS" }
            prefs.edit().putString("vision_base_url", normalized).apply()
        }

    var visionModel: String
        get() = prefs.getString("vision_model", AiServiceProviderConfig.ZHIPU_DEFAULT.visionModel)
            ?: AiServiceProviderConfig.ZHIPU_DEFAULT.visionModel
        set(value) {
            require(value.isNotBlank()) { "视觉模型不能为空" }
            prefs.edit().putString("vision_model", value.trim()).apply()
        }

    var screenshotMonitorEnabled: Boolean
        get() = prefs.getBoolean("screenshot_monitor_enabled", false)
        set(value) { prefs.edit().putBoolean("screenshot_monitor_enabled", value).apply() }

    fun currentProvider(): AiServiceProviderConfig = AiServiceProviderConfig(
        id = providerId.ifBlank { AiServiceProviderConfig.ZHIPU_DEFAULT.id },
        name = if (providerId == "zhipu_glm") "智谱GLM" else providerId,
        isBuiltIn = providerId == "zhipu_glm",
        baseUrl = baseUrl,
        visionModel = visionModel,
    )
}
