package com.lifetrace.finance.auth

import android.content.Context
import android.os.Build
import com.lifetrace.finance.network.ApiHttpException
import com.lifetrace.finance.network.LifeTraceApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthManager(
    context: Context,
    private val api: LifeTraceApi,
    private val tokenStore: SecureTokenStore,
) {
    private val prefs = context.getSharedPreferences("auth_state", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()
    @Volatile private var accessToken: String? = null
    @Volatile var currentUserId: String? = prefs.getString("user_id", null); private set

    val deviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }
    private val deviceName = "Android ${Build.MODEL ?: "device"}"

    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val response = api.login(email, password, deviceId, deviceName)
        acceptTokenResponse(response)
    }

    suspend fun restore(): Boolean = refreshMutex.withLock {
        if (accessToken != null) return true
        val refresh = tokenStore.loadRefreshToken() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { acceptTokenResponse(api.refresh(refresh, deviceId)); true }
                .getOrElse { error ->
                    if (error is ApiHttpException && error.status in listOf(400, 401, 403)) clearSession()
                    false
                }
        }
    }

    suspend fun accessToken(): String? {
        accessToken?.let { return it }
        return if (restore()) accessToken else null
    }

    suspend fun refreshAfterUnauthorized(): String? = refreshMutex.withLock {
        val refresh = tokenStore.loadRefreshToken() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { api.refresh(refresh, deviceId) }
                .onSuccess(::acceptTokenResponse)
                .onFailure { if (it is ApiHttpException && it.status in listOf(400, 401, 403)) clearSession() }
                .getOrNull()
            accessToken
        }
    }

    suspend fun logout() {
        val token = accessToken
        if (token != null) withContext(Dispatchers.IO) { runCatching { api.logout(token) } }
        clearSession()
    }

    private fun acceptTokenResponse(response: org.json.JSONObject) {
        accessToken = response.getString("accessToken")
        if (!response.isNull("refreshToken")) tokenStore.saveRefreshToken(response.getString("refreshToken"))
        val user = response.getJSONObject("user")
        currentUserId = user.getString("id")
        prefs.edit().putString("user_id", currentUserId).apply()
    }

    private fun clearSession() {
        accessToken = null
        currentUserId = null
        tokenStore.clear()
        prefs.edit().remove("user_id").apply()
    }
}
