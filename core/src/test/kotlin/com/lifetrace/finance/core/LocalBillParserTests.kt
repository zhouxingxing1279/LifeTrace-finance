package com.lifetrace.finance.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalBillParserTests {
    @Test fun parsesWechatPaymentDetail() {
        val candidate = assertNotNull(LocalBillParser.parse(
            """
            微信支付
            支付成功
            ￥25.80
            商户名称：星巴克咖啡
            支付方式：零钱
            交易时间：2026-08-12 09:30:12
            """.trimIndent(),
            capturedAtMillis = 1L,
        ))
        assertEquals(2580L, candidate.amountCents)
        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals("星巴克咖啡", candidate.merchant)
        assertEquals("零钱", candidate.accountHint)
        assertTrue(candidate.occurredAtMillis > 1L)
        assertTrue(candidate.confidence >= 0.8)
    }

    @Test fun parsesAlipayRefund() {
        val candidate = assertNotNull(LocalBillParser.parse(
            """
            支付宝
            退款成功
            退款金额 36.50元
            商户：某某便利店
            退款到账：余额宝
            """.trimIndent(),
        ))
        assertEquals(3650L, candidate.amountCents)
        assertEquals(TransactionType.REFUND, candidate.transactionType)
        assertEquals("某某便利店", candidate.merchant)
    }

    @Test fun parsesIncomingPayment() {
        val candidate = assertNotNull(LocalBillParser.parse(
            """
            微信支付
            收款成功
            收款金额：¥88.00
            收款方：周星星
            """.trimIndent(),
        ))
        assertEquals(8800L, candidate.amountCents)
        assertEquals(TransactionType.INCOME, candidate.transactionType)
    }

    @Test fun rejectsMarketingAndNormalScreens() {
        assertNull(LocalBillParser.parse("支付宝优惠券\n立即领取\n满100减20"))
        assertNull(LocalBillParser.parse("微信聊天\n今天晚上吃什么\n25.80元"))
        assertNull(LocalBillParser.parse("商品详情\n原价 99 元\n优惠价 69 元"))
    }

    @Test fun rejectsBillWithoutARealAmount() {
        assertNull(LocalBillParser.parse("微信支付\n支付成功\n订单号 20260812093012"))
    }

    @Test fun screenshotPathDetectorSupportsChineseAndCommonAndroidNames() {
        assertTrue(ScreenshotPathDetector.isScreenshot("Screenshot_20260812_093000.png", "Pictures/Screenshots/"))
        assertTrue(ScreenshotPathDetector.isScreenshot("截屏_20260812_093000.jpg", "DCIM/"))
        assertTrue(ScreenshotPathDetector.isScreenshot("IMG_1.png", "Pictures/截图/"))
        assertTrue(!ScreenshotPathDetector.isScreenshot("IMG_20260812.jpg", "DCIM/Camera/"))
    }
}
