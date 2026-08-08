package com.lifetrace.finance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun appLaunchesIntoQuickEntry() {
        rule.onNodeWithText("LifeTrace Finance").assertIsDisplayed()
        rule.onNodeWithText("快速记账").assertIsDisplayed()
    }
}
