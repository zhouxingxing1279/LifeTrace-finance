package com.lifetrace.finance

import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.TransactionEntity
import com.lifetrace.finance.ui.presentTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionPresentationTest {
    @Test fun blankMerchantFallsBackToImportedItemAndResolvesPaymentAccount() {
        val account = account("account-bank", "招商银行")
        val transaction = transaction(
            merchant = "   ",
            counterparty = "",
            item = "麦当劳",
            accountId = account.id,
        )

        val display = presentTransaction(transaction, listOf(account))

        assertEquals("麦当劳", display.title)
        assertEquals("招商银行", display.accountLine)
    }

    @Test fun transferShowsBothSyncedAccounts() {
        val from = account("account-a", "微信零钱")
        val to = account("account-b", "招商银行")
        val transaction = transaction(
            transactionType = "transfer",
            merchant = null,
            counterparty = null,
            item = "资金划转",
            accountId = from.id,
            toAccountId = to.id,
        )

        val display = presentTransaction(transaction, listOf(from, to))

        assertEquals("资金划转", display.title)
        assertEquals("微信零钱 → 招商银行", display.accountLine)
    }

    @Test fun knownAccountIdIsNeverSilentlyHiddenWhenAccountHasNotArrivedYet() {
        val transaction = transaction(merchant = "便利店", accountId = "account-late")

        val display = presentTransaction(transaction, emptyList())

        assertEquals("便利店", display.title)
        assertEquals("付款账户未同步", display.accountLine)
    }

    private fun account(id: String, name: String) = AccountEntity(
        id = id,
        localProfileId = "profile",
        name = name,
        accountType = "bank",
        createdAt = "2026-08-12T00:00:00Z",
        updatedAt = "2026-08-12T00:00:00Z",
    )

    private fun transaction(
        transactionType: String = "expense",
        merchant: String? = null,
        counterparty: String? = null,
        item: String? = null,
        accountId: String? = null,
        toAccountId: String? = null,
    ) = TransactionEntity(
        id = "tx-1",
        localProfileId = "profile",
        transactionType = transactionType,
        amountCents = 2580,
        accountId = accountId,
        toAccountId = toAccountId,
        merchant = merchant,
        counterparty = counterparty,
        item = item,
        occurredAt = "2026-08-12T00:00:00Z",
        localDate = "2026-08-12",
        status = "confirmed",
        sourceType = "bill_import",
        createdAt = "2026-08-12T00:00:00Z",
        updatedAt = "2026-08-12T00:00:00Z",
    )
}
