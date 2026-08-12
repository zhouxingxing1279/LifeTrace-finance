package com.lifetrace.finance.ai

import com.lifetrace.finance.core.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** Tolerant model-output parser modeled after BeeCount's JsonResponseParser responsibilities. */
object JsonResponseParser {
    fun parseBills(raw: String): List<BillInfo> {
        val payload = extractJson(raw.trim()) ?: return emptyList()
        val array = runCatching {
            when {
                payload.startsWith("[") -> JSONArray(cleanTrailingCommas(payload))
                payload.startsWith("{") -> JSONArray().put(JSONObject(cleanTrailingCommas(payload)))
                else -> JSONArray()
            }
        }.getOrElse { return emptyList() }

        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                parseBill(obj)?.let(::add)
            }
        }
    }

    private fun parseBill(obj: JSONObject): BillInfo? {
        val amount = obj.optLong("amountCents", -1L)
        if (amount <= 0L) return null
        val type = when (obj.optString("type")) {
            TransactionType.EXPENSE.wire -> TransactionType.EXPENSE
            TransactionType.INCOME.wire -> TransactionType.INCOME
            TransactionType.TRANSFER.wire -> TransactionType.TRANSFER
            TransactionType.REFUND.wire -> TransactionType.REFUND
            TransactionType.FEE.wire -> TransactionType.FEE
            else -> return null
        }
        val currency = obj.optString("currency", "CNY").uppercase().takeIf { it.matches(Regex("[A-Z]{3}")) } ?: "CNY"
        val confidence = if (obj.has("confidence") && !obj.isNull("confidence")) {
            obj.optDouble("confidence").takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        } else null
        return BillInfo(
            amountCents = amount,
            currency = currency,
            type = type,
            merchant = clean(obj.optNullableString("merchant"), 120),
            item = clean(obj.optNullableString("item"), 160),
            occurredAt = parseInstant(obj.optNullableString("occurredAt")),
            account = clean(obj.optNullableString("account"), 120),
            fromAccount = clean(obj.optNullableString("fromAccount"), 120),
            toAccount = clean(obj.optNullableString("toAccount"), 120),
            category = clean(obj.optNullableString("category"), 80),
            externalTransactionId = clean(obj.optNullableString("externalTransactionId"), 160),
            confidence = confidence,
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        }
    }

    private fun clean(value: String?, maxChars: Int): String? = value?.trim()?.takeIf(String::isNotEmpty)?.take(maxChars)

    private fun extractJson(raw: String): String? {
        val unfenced = raw
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val array = balanced(unfenced, '[', ']')
        if (array != null) return array
        return balanced(unfenced, '{', '}')
    }

    private fun balanced(text: String, open: Char, close: Char): String? {
        val start = text.indexOf(open)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (escaped) { escaped = false; continue }
            if (inString && ch == '\\') { escaped = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            if (ch == open) depth++
            if (ch == close) {
                depth--
                if (depth == 0) return text.substring(start, index + 1)
            }
        }
        return null
    }

    private fun cleanTrailingCommas(input: String): String =
        input.replace(Regex(",\\s*([}\\]])"), "$1")
}
