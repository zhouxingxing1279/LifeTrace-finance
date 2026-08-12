package com.lifetrace.finance

import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.domain.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderSettingsTest {
    @Test fun persistsEnabledStateAndTime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("daily_reminder", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val settings = ReminderSettings(context)
        assertFalse(settings.enabled)
        assertEquals(21, settings.hour)
        assertEquals(0, settings.minute)
        settings.enabled = true
        settings.hour = 8
        settings.minute = 35
        val restored = ReminderSettings(context)
        assertTrue(restored.enabled)
        assertEquals(8, restored.hour)
        assertEquals(35, restored.minute)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidHour() {
        ReminderSettings(ApplicationProvider.getApplicationContext()).hour = 24
    }
}
