package com.lifetrace.finance.core

import kotlin.math.min
import kotlin.math.pow

object SyncPolicy {
    fun retryDelayMillis(attempt: Int, retryAfterSeconds: Long? = null): Long {
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) return retryAfterSeconds * 1000L
        val boundedAttempt = attempt.coerceIn(0, 10)
        return min(15 * 60_000L, (2.0.pow(boundedAttempt.toDouble()) * 1000L).toLong())
    }

    fun nextBatchSize(current: Int, httpStatus: Int): Int = when {
        httpStatus == 413 && current > 1 -> maxOf(1, current / 2)
        else -> current
    }
}
