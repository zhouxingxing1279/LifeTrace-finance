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
