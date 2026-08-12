package com.lifetrace.finance.core

object LifeTraceContract {
    const val APP_ID = "lifetrace-finance-android"
    const val PLATFORM = "android"
    const val PROTOCOL_VERSION = 1
    const val SCHEMA_VERSION = 1

    val FINANCE_ENTITY_TYPES = setOf(
        "finance.ledger",
        "finance.account",
        "finance.category",
        "finance.transaction",
        "finance.recurring_transaction",
        "finance.tag",
        "finance.transaction_tag",
        "finance.budget",
        "finance.transaction_attachment",
        "finance.transaction_evidence",
    )

    val REQUESTED_SCOPES = listOf(
        "account:read",
        "devices:read",
        "sync:read",
        "sync:write",
        "finance:read",
        "finance:write",
    )
}

enum class TransactionType(val wire: String) {
    EXPENSE("expense"), INCOME("income"), TRANSFER("transfer"), REFUND("refund"), FEE("fee");
    companion object { fun fromWire(value: String) = entries.firstOrNull { it.wire == value } }
}

enum class TransactionStatus(val wire: String) {
    CANDIDATE("candidate"), PROVISIONAL("provisional"), CONFIRMED("confirmed"), IGNORED("ignored");
    companion object { fun fromWire(value: String) = entries.firstOrNull { it.wire == value } }
}

data class Money(val amountCents: Long, val currency: String = "CNY") {
    init { require(currency.matches(Regex("[A-Z]{3}"))) { "currency must be ISO-like 3 uppercase letters" } }
}

data class FinanceTransaction(
    val id: String,
    val localProfileId: String,
    val type: TransactionType,
    val amountCents: Long,
    val currency: String = "CNY",
    val ledgerId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val counterparty: String? = null,
    val merchant: String? = null,
    val item: String? = null,
    val note: String? = null,
    val occurredAt: String,
    val localDate: String,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val sourceType: String = "manual",
    val externalTransactionId: String? = null,
    val recurringTransactionId: String? = null,
    val excludeFromStats: Boolean = false,
    val excludeFromBudget: Boolean = false,
    val nativeAmountCents: Long? = null,
    val nativeCurrency: String? = null,
    val exchangeRate: String? = null,
    val localVersion: Long = 1,
    val serverVersion: String? = null,
    val deletedAt: String? = null,
)

fun validateTransaction(tx: FinanceTransaction) {
    require(tx.id.isNotBlank())
    require(tx.localProfileId.isNotBlank())
    require(tx.amountCents >= 0) { "amount must be non-negative" }
    require(tx.localDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "localDate must be YYYY-MM-DD" }
    require(tx.currency.matches(Regex("[A-Z]{3}")))
    tx.nativeCurrency?.let { require(it.matches(Regex("[A-Z]{3}"))) }
    if (tx.type == TransactionType.TRANSFER) {
        require(!tx.accountId.isNullOrBlank())
        require(!tx.toAccountId.isNullOrBlank())
        require(tx.accountId != tx.toAccountId)
    }
}
