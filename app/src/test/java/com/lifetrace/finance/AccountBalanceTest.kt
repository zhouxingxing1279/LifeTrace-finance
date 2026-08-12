package com.lifetrace.finance

import com.lifetrace.finance.data.TransactionEntity
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.domain.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AccountBalanceTest {
    private fun account(id: String, opening: Long) = AccountEntity(
        id = id, localProfileId = "p", ledgerId = "l", name = id, accountType = "cash",
        openingBalanceCents = opening, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
    )
    private fun tx(id: String, type: String, amount: Long, account: String?, to: String? = null) = TransactionEntity(
        id = id, localProfileId = "p", transactionType = type, amountCents = amount,
        accountId = account, toAccountId = to, occurredAt = "2026-08-12T00:00:00Z",
        localDate = "2026-08-12", status = "confirmed", sourceType = "manual",
        createdAt = "2026-08-12T00:00:00Z", updatedAt = "2026-08-12T00:00:00Z",
    )

    @Test fun calculatesIncomeExpenseAndBothSidesOfTransfer() {
        val rows = listOf(tx("i", "income", 10000, "a"), tx("e", "expense", 2500, "a"), tx("t1", "transfer", 1000, "a", "b"), tx("t2", "transfer", 400, "b", "a"))
        assertEquals(6900, AccountBalance.transactionDelta("a", rows))
        assertEquals(600, AccountBalance.transactionDelta("b", rows))
        assertEquals(11900, AccountBalance.current(5000, "a", rows))
    }

    @Test fun balanceCorrectionDerivesOpeningBalanceWithoutCreatingTransaction() {
        val rows = listOf(tx("e", "expense", 2500, "a"))
        assertEquals(12500, AccountBalance.openingForDesiredBalance(10000, "a", rows))
        assertEquals(10000, AccountBalance.current(12500, "a", rows))
    }

    @Test fun monthlyTrendProducesEndOfMonthBalancesAndClampsCurrentMonthToToday() {
        val rows = listOf(
            tx("jan", "income", 1000, "a").copy(localDate = "2026-01-10"),
            tx("feb", "expense", 200, "a").copy(localDate = "2026-02-20"),
            tx("mar", "income", 500, "a").copy(localDate = "2026-03-12"),
            tx("future", "expense", 900, "a").copy(localDate = "2026-03-30"),
        )

        val trend = AccountBalance.monthlyTrend(5000, "a", rows, months = 3, asOf = LocalDate.parse("2026-03-15"))

        assertEquals(listOf("2026-01", "2026-02", "2026-03"), trend.map { it.month.toString() })
        assertEquals(listOf(6000L, 5800L, 6300L), trend.map { it.balanceCents })
    }

    @Test fun monthlyTrendAccountsForBothTransferDirections() {
        val rows = listOf(
            tx("out", "transfer", 1000, "a", "b").copy(localDate = "2026-01-05"),
            tx("in", "transfer", 400, "b", "a").copy(localDate = "2026-02-05"),
        )
        val trend = AccountBalance.monthlyTrend(5000, "a", rows, months = 2, asOf = LocalDate.parse("2026-02-28"))
        assertEquals(listOf(4000L, 4400L), trend.map { it.balanceCents })
    }

    @Test fun netWorthTrendCancelsTransfersBetweenIncludedAccounts() {
        val accounts = listOf(account("a", 5000), account("b", 2000))
        val rows = listOf(
            tx("income", "income", 1000, "a").copy(localDate = "2026-01-05"),
            tx("internal", "transfer", 1500, "a", "b").copy(localDate = "2026-01-20"),
            tx("expense", "expense", 400, "b").copy(localDate = "2026-02-05"),
            tx("external", "transfer", 300, "a", "outside").copy(localDate = "2026-02-10"),
        )
        val trend = AccountBalance.netWorthTrend(accounts, rows, months = 2, asOf = LocalDate.parse("2026-02-28"))
        assertEquals(listOf(8000L, 7300L), trend.map { it.balanceCents })
    }
}
