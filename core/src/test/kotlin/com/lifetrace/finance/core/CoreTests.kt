package com.lifetrace.finance.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTests {
    @Test fun moneyUsesIntegerCents() {
        assertEquals(2580L, MoneyParser.parseCents("￥25.80"))
        assertEquals(3600L, MoneyParser.parseCents("36元"))
        assertNull(MoneyParser.parseCents("12.345"))
    }

    @Test fun parsesWechatPaymentFixture() {
        val result = NotificationTransactionParser.parse(NotificationSample(
            packageName = SupportedPackages.WECHAT,
            postTimeMillis = 1_700_000_000_000,
            title = "微信支付",
            text = "支付成功 ￥25.80 向 星巴克",
            notificationKey = "wechat-1",
        ))
        assertNotNull(result)
        assertEquals(2580L, result.amountCents)
        assertTrue(result.confidence >= 0.8)
    }

    @Test fun ignoresChatAndMarketing() {
        assertNull(NotificationTransactionParser.parse(NotificationSample(
            packageName = SupportedPackages.WECHAT,
            postTimeMillis = 1,
            title = "张三",
            text = "晚上一起吃饭吗",
        )))
        assertNull(NotificationTransactionParser.parse(NotificationSample(
            packageName = SupportedPackages.ALIPAY,
            postTimeMillis = 1,
            title = "优惠活动",
            text = "支付立减优惠 20 元",
        )))
    }

    @Test fun retryPolicyHonorsRetryAfterAnd413() {
        assertEquals(7_000L, SyncPolicy.retryDelayMillis(4, 7))
        assertEquals(25, SyncPolicy.nextBatchSize(50, 413))
    }

    @Test fun categoryClassifierUsesKeywordsAndExistingCategories() {
        val categories = listOf(
            ClassificationCategory("food", "餐饮", "expense"),
            ClassificationCategory("travel", "交通", "expense"),
        )
        val coffee = CategoryClassifier.suggest("expense", "星巴克咖啡有限公司", null, null, null, categories)
        assertNotNull(coffee)
        assertEquals("food", coffee.categoryId)
        assertTrue(coffee.reason.contains("星巴克"))

        val taxi = CategoryClassifier.suggest("expense", "滴滴出行", null, null, null, categories)
        assertEquals("travel", assertNotNull(taxi).categoryId)
    }

    @Test fun categoryClassifierLearnsConfirmedMerchantAndDoesNotGuessWithoutEvidence() {
        val categories = listOf(ClassificationCategory("custom", "宠物", "expense"))
        val history = listOf(ClassificationHistory(merchant = "某某宠物生活馆", categoryId = "custom"))
        val learned = CategoryClassifier.suggest("expense", "某某宠物生活馆（旗舰店）", null, null, null, categories, history)
        assertEquals("custom", assertNotNull(learned).categoryId)
        assertEquals(0.98, learned.confidence)

        assertNull(CategoryClassifier.suggest("expense", null, null, null, null, categories, history))
        assertNull(CategoryClassifier.suggest("transfer", "滴滴", null, null, null, categories, history))
    }
}

class NotificationFixtureTests {
    @Test fun syntheticFixtureMatrix() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("notification-fixtures.tsv"))
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEachIndexed { index, line ->
                val parts = line.split('|')
                val result = NotificationTransactionParser.parse(
                    NotificationSample(
                        packageName = parts[0], postTimeMillis = 1_700_000_000_000L + index * 1_000L,
                        title = parts[1], text = parts[2], notificationKey = "fixture-$index",
                    )
                )
                if (parts[3] == "-") assertNull(result, "fixture $index should not produce a transaction")
                else assertEquals(parts[3].toLong(), assertNotNull(result, "fixture $index should parse").amountCents)
            }
        }
    }
}
