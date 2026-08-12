package com.lifetrace.finance

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifetrace.finance.ai.AiSecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSecretStoreTest {
    @Test
    fun visionApiKeyRoundTripsThroughAndroidKeystore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AiSecretStore(context)
        store.saveApiKey(null)
        assertFalse(store.hasApiKey())
        assertNull(store.loadApiKey())

        store.saveApiKey("test-vision-key")
        assertTrue(store.hasApiKey())
        assertEquals("test-vision-key", store.loadApiKey())

        store.saveApiKey(null)
        assertFalse(store.hasApiKey())
        assertNull(store.loadApiKey())
    }
}
