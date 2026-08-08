package com.lifetrace.finance

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifetrace.finance.auth.SecureTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureTokenStoreTest {
    @Test fun refreshTokenRoundTripsThroughAndroidKeystore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SecureTokenStore(context)
        store.clear()
        store.saveRefreshToken("lt_rt_test.secret")
        assertEquals("lt_rt_test.secret", store.loadRefreshToken())
        store.clear()
        assertNull(store.loadRefreshToken())
    }
}
