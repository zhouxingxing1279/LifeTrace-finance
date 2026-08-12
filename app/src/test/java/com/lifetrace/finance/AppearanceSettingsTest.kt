package com.lifetrace.finance

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.domain.AppearanceSettings
import com.lifetrace.finance.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() = context.getSharedPreferences(AppearanceSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit().let { Unit }

    @Test fun persistsModeAccentAndPrivacy() {
        val settings = AppearanceSettings(context)
        settings.themeMode = ThemeMode.DARK
        settings.accent = "blue"
        settings.secureScreen = true

        val restored = AppearanceSettings(context)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals("blue", restored.accent)
        assertTrue(restored.secureScreen)
    }

    @Test fun appliesAndClearsSecureWindowFlag() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings = AppearanceSettings(context)
        settings.secureScreen = true
        settings.applyPrivacy(activity)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        settings.secureScreen = false
        settings.applyPrivacy(activity)
        assertFalse(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
}
