package com.lifetrace.finance.network

import com.lifetrace.finance.BuildConfig
import com.lifetrace.finance.core.LifeTraceContract
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ApiHttpException(val status: Int, val responseBody: String, val retryAfterSeconds: Long? = null) : IOException("HTTP $status")

class LifeTraceApi(
    private val baseUrlProvider: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun login(email: String, password: String, deviceId: String, deviceName: String): JSONObject = post(
        "/api/v1/auth/login",
        JSONObject().put("email", email).put("password", password)
            .put("appId", LifeTraceContract.APP_ID).put("deviceId", deviceId).put("deviceName", deviceName)
            .put("platform", LifeTraceContract.PLATFORM).put("clientVersion", BuildConfig.VERSION_NAME)
            .put("requestedScopes", JSONArray(LifeTraceContract.REQUESTED_SCOPES)).put("publicDevice", false),
    )

    fun refresh(refreshToken: String, deviceId: String): JSONObject = post(
        "/api/v1/auth/refresh",
        JSONObject().put("refreshToken", refreshToken).put("appId", LifeTraceContract.APP_ID).put("deviceId", deviceId),
    )

    fun logout(accessToken: String) = post("/api/v1/auth/logout", JSONObject(), accessToken)
    fun me(accessToken: String): JSONObject = get("/api/v1/auth/me", accessToken)
    fun capabilities(): JSONObject = get("/api/v1/sync/capabilities", null)
    fun syncPush(accessToken: String, body: JSONObject): JSONObject = post("/api/v1/sync/push", body, accessToken)
    fun syncPull(accessToken: String, body: JSONObject): JSONObject = post("/api/v1/sync/pull", body, accessToken)
    fun snapshot(accessToken: String, body: JSONObject): JSONObject = post("/api/v1/sync/snapshot", body, accessToken)

    private fun get(path: String, token: String?): JSONObject {
        val request = requestBuilder(path, token).get().build()
        return execute(request)
    }

    private fun post(path: String, body: JSONObject, token: String? = null): JSONObject {
        val request = requestBuilder(path, token).post(body.toString().toRequestBody(jsonType)).build()
        return execute(request)
    }

    private fun requestBuilder(path: String, token: String?): Request.Builder {
        val base = baseUrlProvider().trimEnd('/')
        require(base.startsWith("https://") || BuildConfig.DEBUG) { "release cloud URL must use HTTPS" }
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder
    }

    private fun execute(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw ApiHttpException(response.code, body.take(2048), response.header("Retry-After")?.toLongOrNull())
        if (body.isBlank()) JSONObject() else JSONObject(body)
    }
}
