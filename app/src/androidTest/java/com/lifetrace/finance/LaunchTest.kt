package com.lifetrace.finance

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lifetrace.finance.data.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class LaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun appLaunchesIntoQuickEntry() {
        rule.onNodeWithText("LifeTrace Finance").assertIsDisplayed()
        rule.onNodeWithText("快速记账").assertIsDisplayed()
    }

    @Test fun quickExpenseCommitsWithinThreeSecondsOnceReady() {
        rule.waitUntil(10_000) {
            runCatching {
                rule.onNodeWithTag("quick_save").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        val started = SystemClock.elapsedRealtime()
        rule.onNodeWithTag("quick_amount").performTextInput("12.34")
        rule.onNodeWithTag("quick_save").performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText("已保存，本地立即生效").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("quick entry exceeded 3 seconds", SystemClock.elapsedRealtime() - started <= 3_000)
    }

    @Test fun searchAndAccountTypeControlsAreReachable() {
        rule.onNodeWithText("账单").performClick()
        rule.onNodeWithTag("transaction_search").assertIsDisplayed()
        rule.onNodeWithText("账户").performClick()
        rule.onNodeWithText("账户类型：其他").assertIsDisplayed()
    }

    @Test fun syncedBillShowsImportedItemAndPaymentAccount() {
        val graph = AppGraph.get(rule.activity.application)
        runBlocking {
            val profile = graph.finance.ensureProfile()
            val accountId = graph.finance.createAccount(profile.id, "回归测试招商银行", "bank")
            val now = Instant.now().toString()
            graph.db.financeDao().upsertTransaction(
                TransactionEntity(
                    id = "instrumentation-synced-details",
                    localProfileId = profile.id,
                    transactionType = "expense",
                    amountCents = 2580,
                    accountId = accountId,
                    merchant = "   ",
                    counterparty = "",
                    item = "回归测试麦当劳",
                    occurredAt = now,
                    localDate = now.take(10),
                    status = "confirmed",
                    sourceType = "bill_import",
                    createdAt = now,
                    updatedAt = now,
                    serverVersion = "42",
                ),
            )
        }

        rule.onNodeWithText("账单").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("回归测试麦当劳").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("回归测试招商银行", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("回归测试麦当劳").assertIsDisplayed()
        rule.onNodeWithText("回归测试招商银行", substring = true).assertIsDisplayed()
    }
}
