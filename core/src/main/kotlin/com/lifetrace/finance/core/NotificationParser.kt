package com.lifetrace.finance.core

import java.security.MessageDigest
import java.util.Locale

data class NotificationSample(
    val packageName: String,
    val postTimeMillis: Long,
    val title: String?,
    val text: String?,
    val bigText: String? = null,
    val subText: String? = null,
    val notificationKey: String? = null,
)

data class CandidateTransaction(
    val sourcePackage: String,
    val amountCents: Long,
    val merchant: String?,
    val accountHint: String?,
    val occurredAtMillis: Long,
    val confidence: Double,
    val parserId: String,
    val parserVersion: Int,
    val status: TransactionStatus,
    val evidenceHash: String,
)

object SupportedPackages {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
    const val UNIONPAY = "com.unionpay"
    val KNOWN = setOf(WECHAT, ALIPAY, UNIONPAY)
}

object NotificationTransactionParser {
    private const val PARSER_ID = "builtin-cn-payment-notification"
    private const val VERSION = 1
    private val amountPatterns = listOf(
        Regex("(?:￥|¥)\\s*([0-9]{1,12}(?:\\.[0-9]{1,2})?)"),
        Regex("([0-9]{1,12}(?:\\.[0-9]{1,2})?)\\s*元"),
    )
    private val paymentWords = setOf("支付", "付款", "消费", "扣款", "收款", "交易成功", "支出")
    private val ignoreWords = setOf("优惠", "活动", "红包封面", "验证码", "登录", "到账提醒设置")

    fun parse(sample: NotificationSample): CandidateTransaction? {
        if (sample.packageName !in SupportedPackages.KNOWN && !sample.packageName.startsWith("com.bank.")) return null
        val normalized = normalize(sample)
        if (ignoreWords.any(normalized::contains)) return null
        if (paymentWords.none(normalized::contains)) return null

        val amount = amountPatterns.asSequence()
            .mapNotNull { pattern -> pattern.find(normalized)?.groupValues?.getOrNull(1) }
            .mapNotNull(MoneyParser::parseCents)
            .firstOrNull() ?: return null
        if (amount <= 0) return null

        val merchant = extractMerchant(normalized)
        var confidence = 0.55
        if (sample.packageName in SupportedPackages.KNOWN) confidence += 0.15
        if (normalized.contains("成功")) confidence += 0.10
        if (merchant != null) confidence += 0.08
        if (sample.notificationKey != null) confidence += 0.05
        confidence = confidence.coerceAtMost(0.93)
        val status = if (confidence >= 0.90) TransactionStatus.PROVISIONAL else TransactionStatus.CANDIDATE

        return CandidateTransaction(
            sourcePackage = sample.packageName,
            amountCents = amount,
            merchant = merchant,
            accountHint = extractAccountHint(normalized),
            occurredAtMillis = sample.postTimeMillis,
            confidence = confidence,
            parserId = PARSER_ID,
            parserVersion = VERSION,
            status = status,
            evidenceHash = sha256("${sample.packageName}|${sample.notificationKey.orEmpty()}|${sample.postTimeMillis}|$normalized"),
        )
    }

    fun dedupKey(candidate: CandidateTransaction): String {
        val bucket = candidate.occurredAtMillis / 120_000L
        return sha256("${candidate.sourcePackage}|${candidate.amountCents}|${normalizeText(candidate.merchant.orEmpty())}|$bucket")
    }

    private fun normalize(sample: NotificationSample): String = listOfNotNull(
        sample.title, sample.text, sample.bigText, sample.subText
    ).joinToString(" ").replace(Regex("\\s+"), " ").trim()

    private fun normalizeText(value: String) = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), "").trim()

    private fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Regex("(?:商户|向|付款给|支付给)[:：\\s]*([^，。,.]{2,30})"),
            Regex("在([^，。,.]{2,30})(?:消费|支付)"),
        )
        return patterns.asSequence().mapNotNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }.firstOrNull()
    }

    private fun extractAccountHint(text: String): String? = Regex("尾号\\s*([0-9]{4})").find(text)?.groupValues?.getOrNull(1)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
