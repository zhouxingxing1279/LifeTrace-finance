package com.lifetrace.finance.ai

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiProviderException(message: String) : Exception(message)

/**
 * BeeCount-style provider factory. LifeTrace deliberately executes it in Android rather than
 * proxying images through LifeTrace Cloud. Callers run this blocking HTTP request on Dispatchers.IO.
 */
class AiProviderFactory(
    private val settings: AiSettingsStore,
    private val secrets: AiSecretStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    fun isVisionConfigured(): Boolean = settings.currentProvider().supportsVision && secrets.hasApiKey()

    suspend fun vision(image: VisionImage, prompt: String): AiVisionResult {
        require(image.bytes.isNotEmpty()) { "图片为空" }
        require(image.mimeType in SUPPORTED_MIME) { "仅支持 PNG/JPEG/WebP" }
        require(image.bytes.size <= MAX_IMAGE_BYTES) { "图片超过 10 MiB 限制" }

        val provider = settings.currentProvider()
        if (!provider.supportsVision) throw AiProviderException("当前 AI 服务商未配置视觉模型")
        val apiKey = secrets.loadApiKey()?.takeIf(String::isNotBlank)
            ?: throw AiProviderException("请先配置 Vision API Key")

        val base64 = Base64.encodeToString(image.bytes, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", base64)))
            .put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", provider.visionModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("temperature", 0.1)
            .put("stream", false)

        val request = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiProviderException("Vision API 请求失败（HTTP ${response.code}）")
            }
            val root = runCatching { JSONObject(responseBody) }
                .getOrElse { throw AiProviderException("Vision API 返回了无效 JSON") }
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            val text = message?.optString("content")?.takeIf(String::isNotBlank)
                ?: throw AiProviderException("Vision API 返回内容为空")
            AiVisionResult(provider.id, provider.visionModel, text)
        }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        val SUPPORTED_MIME = setOf("image/png", "image/jpeg", "image/webp")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
