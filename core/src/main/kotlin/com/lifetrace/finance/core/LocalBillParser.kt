package com.lifetrace.finance.core

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Structured result produced entirely from on-device OCR text. */
data class LocalBillCandidate(
    val amountCents: Long,
    val transactionType: TransactionType,
    val merchant: String?,
    val item: String?,
    val accountHint: String?,
    val occurredAtMillis: Long,
    val confidence: Double,
    val parserId: String,
    val parserVersion: Int,
    val status: TransactionStatus,
    val evidenceHash: String,
)

/**
 * Deterministic parser for OCR text from Chinese payment screenshots.
 *
 * The OCR engine is intentionally outside this module so the financial parsing
 * logic can be unit tested without Android or ML Kit. Unknown fields remain
 * null instead of being guessed.
 */
object LocalBillParser {
    const val PARSER_ID = "local-ocr-cn-payment"
    const val VERSION = 1

    private val strongBillWords = setOf(
        "支付成功", "付款成功", "交易成功", "交易详情", "账单详情", "支付详情",
        "已支付", "已付款", "收款成功", "退款成功", "退款到账", "转账成功",
    )
    private val weakBillWords = setOf(
        "支付金额", "付款金额", "实付", "收款金额", "交易金额", "退款金额",
        "支付方式", "付款方式", "商户", "收款方", "订单号", "交易单号",
    )
    private val rejectWords = setOf(
        "验证码", "登录确认", "红包封面", "优惠券", "活动规则", "立即领取",
    )

    private val labeledAmountPatterns = listOf(
        Regex("(?:支付金额|付款金额|实付金额|实付款|实付|交易金额|订单金额|收款金额|退款金额|到账金额|金额)\\s*[:：]?\\s*(?:￥|¥)?\\s*([0-9]{1,10}(?:\\.[0-9]{1,2})?)\\s*(?:元)?", RegexOption.IGNORE_CASE),
        Regex("(?:￥|¥)\\s*([0-9]{1,10}(?:\\.[0-9]{1,2})?)"),
        Regex("([0-9]{1,10}(?:\\.[0-9]{1,2})?)\\s*元"),
    )

    private val merchantPatterns = listOf(
        Regex("(?:商户名称|商户|收款方|收款人|付款给|支付给|向)\\s*[:：]?\\s*([^\\n，。]{2,40})"),
        Regex("(?:商品说明|商品|订单名称)\\s*[:：]?\\s*([^\\n，。]{2,40})"),
    )

    private val itemPatterns = listOf(
        Regex("(?:商品说明|商品名称|商品|订单名称)\\s*[:：]?\\s*([^\\n]{2,60})"),
    )

    private val accountPatterns = listOf(
        Regex("(?:支付方式|付款方式|扣款方式|收款方式)\\s*[:：]?\\s*([^\\n]{2,50})"),
        Regex("((?:微信)?零钱(?:通)?|余额宝|花呗|支付宝余额|银行卡[^\\n]{0,30}|[^\\n]{0,24}尾号\\s*[0-9]{4})"),
    )

    private val datePatterns = listOf(
        Regex("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"),
        Regex("(20\\d{2})年(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"),
    )

    fun parse(ocrText: String, capturedAtMillis: Long = System.currentTimeMillis()): LocalBillCandidate? {
        val text = normalize(ocrText)
        if (text.isBlank()) return null

        val strongHits = strongBillWords.count(text::contains)
        val weakHits = weakBillWords.count(text::contains)
        if (strongHits == 0 && weakHits < 2) return null
        if (strongHits == 0 && rejectWords.any(text::contains)) return null

        val amount = extractAmount(text) ?: return null
        if (amount <= 0L) return null

        val type = detectType(text)
        val merchant = extractFirst(merchantPatterns, text)
            ?.let(::cleanMerchant)
            ?.takeIf { it.length >= 2 }
        val item = extractFirst(itemPatterns, text)?.trim()?.take(60)?.takeIf(String::isNotBlank)
        val accountHint = extractFirst(accountPatterns, text)?.trim()?.take(50)?.takeIf(String::isNotBlank)
        val occurredAtMillis = extractOccurredAt(text) ?: capturedAtMillis

        var confidence = 0.55
        confidence += (strongHits.coerceAtMost(2) * 0.10)
        confidence += (weakHits.coerceAtMost(3) * 0.04)
        if (merchant != null) confidence += 0.07
        if (accountHint != null) confidence += 0.05
        if (extractOccurredAt(text) != null) confidence += 0.05
        confidence = confidence.coerceAtMost(0.96)

        val status = if (confidence >= 0.88) TransactionStatus.PROVISIONAL else TransactionStatus.CANDIDATE
        return LocalBillCandidate(
            amountCents = amount,
            transactionType = type,
            merchant = merchant,
            item = item,
            accountHint = accountHint,
            occurredAtMillis = occurredAtMillis,
            confidence = confidence,
            parserId = PARSER_ID,
            parserVersion = VERSION,
            status = status,
            evidenceHash = sha256(text),
        )
    }

    private fun detectType(text: String): TransactionType = when {
        listOf("退款成功", "退款到账", "已退款", "退款金额", "退回").any(text::contains) -> TransactionType.REFUND
        listOf("转账成功", "转账给", "转出").any(text::contains) -> TransactionType.TRANSFER
        listOf("手续费", "服务费").any(text::contains) && !listOf("支付成功", "付款成功").any(text::contains) -> TransactionType.FEE
        listOf("收款成功", "收款金额", "已收款", "到账金额", "收入").any(text::contains) -> TransactionType.INCOME
        else -> TransactionType.EXPENSE
    }

    private fun extractAmount(text: String): Long? = labeledAmountPatterns.asSequence()
        .mapNotNull { pattern -> pattern.find(text)?.groupValues?.getOrNull(1) }
        .mapNotNull(MoneyParser::parseCents)
        .firstOrNull { it in 1..999_999_999_99L }

    private fun extractFirst(patterns: List<Regex>, text: String): String? = patterns.asSequence()
        .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
        .map(String::trim)
        .firstOrNull(String::isNotBlank)

    private fun cleanMerchant(value: String): String = value
        .replace(Regex("(?:支付成功|付款成功|交易成功).*$"), "")
        .trim(' ', ':', '：', '-', '—')
        .take(40)

    private fun extractOccurredAt(text: String): Long? {
        datePatterns.forEach { pattern ->
            val match = pattern.find(text) ?: return@forEach
            val g = match.groupValues
            val year = g.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val month = g.getOrNull(2)?.toIntOrNull() ?: return@forEach
            val day = g.getOrNull(3)?.toIntOrNull() ?: return@forEach
            val hour = g.getOrNull(4)?.toIntOrNull() ?: return@forEach
            val minute = g.getOrNull(5)?.toIntOrNull() ?: return@forEach
            val second = g.getOrNull(6)?.takeIf(String::isNotBlank)?.toIntOrNull() ?: 0
            val time = runCatching { LocalDateTime.of(year, month, day, hour, minute, second) }.getOrNull()
                ?: return@forEach
            return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    private fun normalize(value: String): String = value
        .replace('\r', '\n')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(Locale.ROOT, it) }
}

/** Pure path/name guard for MediaStore screenshot discovery. */
object ScreenshotPathDetector {
    private val markers = listOf(
        "screenshot", "screen_shot", "screen shot", "screenshots",
        "截屏", "截图", "屏幕截图", "屏幕快照",
    )

    fun isScreenshot(displayName: String?, relativePath: String?): Boolean {
        val value = listOfNotNull(displayName, relativePath)
            .joinToString("/")
            .lowercase(Locale.ROOT)
        return markers.any(value::contains)
    }
}
