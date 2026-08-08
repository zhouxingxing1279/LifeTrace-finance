package com.lifetrace.finance

import com.lifetrace.finance.core.LifeTraceContract
import com.lifetrace.finance.network.LifeTraceApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifeTraceApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LifeTraceApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        api = LifeTraceApi(baseUrlProvider = { server.url("/").toString().trimEnd('/') })
    }

    @After fun tearDown() = server.shutdown()

    @Test fun loginUsesFinanceAndroidIdentityAndNativeRoute() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        api.login("user@example.com", "secret", "device-1", "Pixel")
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        val json = JSONObject(request.body.readUtf8())
        assertEquals(LifeTraceContract.APP_ID, json.getString("appId"))
        assertEquals(LifeTraceContract.PLATFORM, json.getString("platform"))
        val scopes = json.getJSONArray("requestedScopes")
        assertTrue((0 until scopes.length()).map { scopes.getString(it) }.containsAll(LifeTraceContract.REQUESTED_SCOPES))
    }

    @Test fun pushUsesSyncV1RouteAndBearerHeader() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"results\":[]}"))
        api.syncPush("lt_at_test", JSONObject().put("changes", org.json.JSONArray()))
        val request = server.takeRequest()
        assertEquals("/api/v1/sync/push", request.path)
        assertEquals("Bearer lt_at_test", request.getHeader("Authorization"))
    }

    @Test fun capabilitiesUsesPublicGetRoute() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"protocolVersion\":1}"))
        api.capabilities()
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/sync/capabilities", request.path)
    }
}
