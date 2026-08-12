package com.lifetrace.finance.automation

import com.lifetrace.finance.ai.BillInfo
import com.lifetrace.finance.core.CategoryClassifier
import com.lifetrace.finance.core.ClassificationCategory
import com.lifetrace.finance.core.TransactionStatus
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.CategoryEntity
import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.Locale

data class BillCreationResult(
    val transactionIds: List<String>,
    val skipped: Int,
)

/** Maps AI-readable names to durable LifeTrace entities and owns transaction creation. */
class BillCreationService(
    private val finance: FinanceRepository,
) {
    suspend fun createBills(profileId: String, bills: List<BillInfo>, sourceType: String): BillCreationResult {
        val accounts = finance.accounts(profileId).first()
        val categories = finance.categories(profileId).first()
        val classifierCategories = categories.map { ClassificationCategory(it.id, it.name, it.categoryType) }
        val created = mutableListOf<String>()
        var skipped = 0

        for (bill in bills) {
            val sourceAccount = resolveAccount(
                if (bill.type == TransactionType.TRANSFER) bill.fromAccount else bill.account,
                accounts,
            )
            val destinationAccount = if (bill.type == TransactionType.TRANSFER) resolveAccount(bill.toAccount, accounts) else null
            if (bill.type == TransactionType.TRANSFER && (sourceAccount == null || destinationAccount == null || sourceAccount.id == destinationAccount.id)) {
                skipped++
                continue
            }
            val category = if (bill.type == TransactionType.TRANSFER) null else resolveCategory(bill, categories, classifierCategories)
            val status = if ((bill.confidence ?: 0.0) >= 0.90) TransactionStatus.PROVISIONAL else TransactionStatus.CANDIDATE
            val merchant = bill.merchant ?: bill.item
            val note = buildString {
                if (bill.item != null && bill.item != merchant) append(bill.item)
                if (bill.account != null && sourceAccount == null) {
                    if (isNotEmpty()) append(" · ")
                    append("账户提示：${bill.account}")
                }
            }.ifBlank { null }

            val id = runCatching {
                finance.createTransaction(
                    profileId = profileId,
                    type = bill.type,
                    amountCents = bill.amountCents,
                    accountId = sourceAccount?.id,
                    toAccountId = destinationAccount?.id,
                    categoryId = category?.id,
                    merchant = merchant,
                    note = note,
                    status = status,
                    sourceType = sourceType,
                    externalTransactionId = bill.externalTransactionId,
                    occurredAt = bill.occurredAt ?: Instant.now(),
                )
            }.getOrNull()
            if (id == null) skipped++ else created += id
        }
        return BillCreationResult(created, skipped)
    }

    private fun resolveCategory(
        bill: BillInfo,
        categories: List<CategoryEntity>,
        classifierCategories: List<ClassificationCategory>,
    ): CategoryEntity? {
        val allowedType = when (bill.type) {
            TransactionType.REFUND -> TransactionType.INCOME.wire
            else -> bill.type.wire
        }
        val allowed = categories.filter { it.categoryType == allowedType }
        val hint = bill.category?.let(::normalize)
        if (!hint.isNullOrBlank()) {
            allowed.firstOrNull { normalize(it.name) == hint }?.let { return it }
            allowed.firstOrNull { normalize(it.name).contains(hint) || hint.contains(normalize(it.name)) }?.let { return it }
        }
        val suggestion = CategoryClassifier.suggest(
            transactionType = allowedType,
            merchant = bill.merchant,
            counterparty = null,
            item = bill.item,
            note = null,
            categories = classifierCategories,
        ) ?: return null
        return allowed.firstOrNull { it.id == suggestion.categoryId }
    }

    private fun resolveAccount(hint: String?, accounts: List<AccountEntity>): AccountEntity? {
        if (hint.isNullOrBlank()) return null
        val normalizedHint = normalize(hint)
        accounts.firstOrNull { normalize(it.name) == normalizedHint }?.let { return it }
        accounts.firstOrNull {
            val name = normalize(it.name)
            name.contains(normalizedHint) || normalizedHint.contains(name)
        }?.let { return it }
        val last4 = Regex("(?<!\\d)(\\d{4})(?!\\d)").find(hint)?.groupValues?.getOrNull(1)
        if (last4 != null) accounts.firstOrNull { it.last4 == last4 }?.let { return it }
        return when {
            hint.contains("微信") -> accounts.firstOrNull { it.accountType == "wechat" }
            hint.contains("支付宝") -> accounts.firstOrNull { it.accountType == "alipay" }
            else -> null
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("(有限责任公司|股份有限公司|有限公司|支付|收款|付款|储蓄卡|信用卡)"), "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
