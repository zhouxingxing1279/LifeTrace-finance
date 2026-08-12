package com.lifetrace.finance.domain

import com.lifetrace.finance.data.TransactionEntity
import com.lifetrace.finance.data.AccountEntity
import java.time.LocalDate
import java.time.YearMonth

object AccountBalance {
    data class MonthlyPoint(val month: YearMonth, val balanceCents: Long)

    fun transactionDelta(accountId: String, rows: List<TransactionEntity>): Long = rows.sumOf { row ->
        when (row.transactionType) {
            "income", "refund" -> if (row.accountId == accountId) row.amountCents else 0L
            "expense" -> if (row.accountId == accountId) -row.amountCents else 0L
            "transfer" -> when (accountId) {
                row.accountId -> -row.amountCents
                row.toAccountId -> row.amountCents
                else -> 0L
            }
            else -> 0L
        }
    }

    fun current(openingBalanceCents: Long?, accountId: String, rows: List<TransactionEntity>): Long =
        (openingBalanceCents ?: 0L) + transactionDelta(accountId, rows)

    fun openingForDesiredBalance(desiredBalanceCents: Long, accountId: String, rows: List<TransactionEntity>): Long =
        desiredBalanceCents - transactionDelta(accountId, rows)

    fun monthlyTrend(
        openingBalanceCents: Long?,
        accountId: String,
        rows: List<TransactionEntity>,
        months: Int = 6,
        asOf: LocalDate = LocalDate.now(),
    ): List<MonthlyPoint> {
        require(months > 0)
        val currentMonth = YearMonth.from(asOf)
        return (months - 1 downTo 0).map { monthsAgo ->
            val month = currentMonth.minusMonths(monthsAgo.toLong())
            val endDate = minOf(month.atEndOfMonth(), asOf)
            val included = rows.filter { row ->
                runCatching { LocalDate.parse(row.localDate) }.getOrNull()?.let { !it.isAfter(endDate) } == true
            }
            MonthlyPoint(month, current(openingBalanceCents, accountId, included))
        }
    }

    fun netWorthTrend(
        accounts: List<AccountEntity>,
        rows: List<TransactionEntity>,
        months: Int = 6,
        asOf: LocalDate = LocalDate.now(),
    ): List<MonthlyPoint> {
        require(months > 0)
        val accountIds = accounts.mapTo(mutableSetOf()) { it.id }
        val opening = accounts.sumOf { it.openingBalanceCents ?: 0L }
        val currentMonth = YearMonth.from(asOf)
        return (months - 1 downTo 0).map { monthsAgo ->
            val month = currentMonth.minusMonths(monthsAgo.toLong())
            val endDate = minOf(month.atEndOfMonth(), asOf)
            val delta = rows.asSequence().filter { row ->
                runCatching { LocalDate.parse(row.localDate) }.getOrNull()?.let { !it.isAfter(endDate) } == true
            }.sumOf { row ->
                when (row.transactionType) {
                    "income", "refund" -> if (row.accountId in accountIds) row.amountCents else 0L
                    "expense" -> if (row.accountId in accountIds) -row.amountCents else 0L
                    "transfer" -> (if (row.toAccountId in accountIds) row.amountCents else 0L) -
                        (if (row.accountId in accountIds) row.amountCents else 0L)
                    else -> 0L
                }
            }
            MonthlyPoint(month, opening + delta)
        }
    }
}
