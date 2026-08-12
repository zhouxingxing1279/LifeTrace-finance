package com.lifetrace.finance.ai

import com.lifetrace.finance.core.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

class SmartCaptureAiTests {
    @Test
    fun billGuardContainsAccountsCategoriesAndNonBillRule() {
        val prompt = PromptBuilder.billGuardForImage(
            BillingContext(
                accountNames = listOf("微信零钱", "招商银行储蓄卡"),
                categoryNames = listOf("餐饮", "交通"),
                currentTime = Instant.parse("2026-08-12T02:30:00Z"),
                timezoneId = "Asia/Shanghai",
            ),
        )
        assertTrue(prompt.contains("若不是账单，只返回 []"))
        assertTrue(prompt.contains("招商银行储蓄卡"))
        assertTrue(prompt.contains("餐饮"))
        assertTrue(prompt.contains("多笔"))
    }

    @Test
    fun parserAcceptsFencedArrayAndDropsInvalidRows() {
        val bills = JsonResponseParser.parseBills(
            """
                ```json
                [
                  {"amountCents":2850,"currency":"CNY","type":"expense","merchant":"瑞幸咖啡","occurredAt":"2026-08-12T09:30:00+08:00","account":"支付宝","category":"餐饮","confidence":1.2,},
                  {"amountCents":0,"currency":"CNY","type":"expense"},
                  {"amountCents":100,"currency":"CNY","type":"unknown"}
                ]
                ```
            """.trimIndent(),
        )
        assertEquals(1, bills.size)
        assertEquals(2850L, bills.single().amountCents)
        assertEquals(TransactionType.EXPENSE, bills.single().type)
        assertEquals("瑞幸咖啡", bills.single().merchant)
        assertEquals(1.0, bills.single().confidence)
        assertEquals(Instant.parse("2026-08-12T01:30:00Z"), bills.single().occurredAt)
    }

    @Test
    fun parserAcceptsSingleObjectAndTransferAccounts() {
        val bill = JsonResponseParser.parseBills(
            "模型结果：{\"amountCents\":50000,\"currency\":\"CNY\",\"type\":\"transfer\",\"fromAccount\":\"招商银行\",\"toAccount\":\"支付宝\",\"category\":null}",
        ).single()
        assertEquals(TransactionType.TRANSFER, bill.type)
        assertEquals("招商银行", bill.fromAccount)
        assertEquals("支付宝", bill.toAccount)
        assertNull(bill.category)
    }

    @Test
    fun parserTreatsEmptyArrayAsNonBill() {
        assertTrue(JsonResponseParser.parseBills("[]").isEmpty())
    }
}
