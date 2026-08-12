package com.lifetrace.finance.ui

import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.TransactionEntity

internal data class TransactionPresentation(
    val title: String,
    val accountLine: String?,
)

internal fun presentTransaction(
    transaction: TransactionEntity,
    accounts: List<AccountEntity>,
    fallbackTitle: String = "未分类账单",
): TransactionPresentation {
    val title = firstNonBlank(
        transaction.merchant,
        transaction.counterparty,
        transaction.item,
        transaction.note,
    ) ?: fallbackTitle

    val fromAccount = transaction.accountId
        ?.let { id -> accounts.firstOrNull { it.id == id }?.name }
    val toAccount = transaction.toAccountId
        ?.let { id -> accounts.firstOrNull { it.id == id }?.name }

    val accountLine = when (transaction.transactionType) {
        "transfer" -> when {
            fromAccount != null && toAccount != null -> "$fromAccount → $toAccount"
            fromAccount != null -> "$fromAccount → 转入账户未同步"
            toAccount != null -> "转出账户未同步 → $toAccount"
            transaction.accountId != null || transaction.toAccountId != null -> "关联账户未同步"
            else -> null
        }
        else -> when {
            fromAccount != null -> fromAccount
            transaction.accountId != null -> "付款账户未同步"
            else -> null
        }
    }

    return TransactionPresentation(title = title, accountLine = accountLine)
}

internal fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }?.trim()
