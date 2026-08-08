package com.lifetrace.finance.core

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyParser {
    fun parseCents(raw: String): Long? {
        val normalized = raw.trim()
            .replace("￥", "")
            .replace("¥", "")
            .replace("元", "")
            .replace(",", "")
            .trim()
        if (!normalized.matches(Regex("\\d{1,12}(?:\\.\\d{1,2})?"))) return null
        return runCatching {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    fun formatCny(cents: Long): String = (if (cents < 0) "-¥" else "¥") + formatPlain(kotlin.math.abs(cents))

    fun formatPlain(cents: Long): String {
        val absolute = kotlin.math.abs(cents)
        return "${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }
}
