package com.lifetrace.finance.importer

import androidx.room.withTransaction
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.data.OutboxEntity
import com.lifetrace.finance.data.TransactionEntity
import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class BillImportCommitResult(
    val created: Int,
    val duplicates: Int,
    val reconciledCandidates: Int,
    val transactionIds: List<String>,
)

/**
 * Commits parsed statement rows through the same finance.transaction wire contract used by
 * FinanceRepository. No import-only cloud entity or backend is introduced.
 */
class BillImportService(
    private val db: FinanceDatabase,
    private val financeRepository: FinanceRepository,
    private val deviceId: String,
) {
    private val finance = db.financeDao()
    private val sync = db.syncDao()

    suspend fun commit(
        profileId: String,
        ledgerId: String,
        accountId: String?,
        preview: BillImportPreview,
    ): BillImportCommitResult {
        val existing = financeRepository.transactions(profileId).first().toMutableList()
        val createdIds = mutableListOf<String>()
        val candidateIds = mutableSetOf<String>()
        var duplicates = 0

        preview.bills.forEach { bill ->
            if (findDuplicate(bill, existing) != null) {
                duplicates++
                return@forEach
            }

            val candidate = findCandidate(bill, existing, candidateIds)
            val now = Instant.now().toString()
            val id = UUID.randomUUID().toString()
            val entity = TransactionEntity(
                id = id,
                localProfileId = profileId,
                ledgerId = ledgerId,
                transactionType = bill.type.wire,
                amountCents = bill.amountCents,
                currency = accountId?.let { finance.accountById(it)?.currency } ?: "CNY",
                accountId = accountId,
                categoryId = candidate?.categoryId,
                merchant = bill.merchant ?: candidate?.merchant,
                item = bill.item ?: candidate?.item,
                note = candidate?.note ?: bill.note,
                occurredAt = bill.occurredAt.toString(),
                localDate = bill.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                status = bill.status.wire,
                sourceType = bill.sourceType,
                externalTransactionId = bill.externalTransactionId,
                createdAt = now,
                updatedAt = now,
                modifiedByDevice = deviceId,
            )

            db.withTransaction {
                finance.upsertTransaction(entity)
                sync.enqueue(outboxFor(entity))
            }
            createdIds += id
            existing += entity

            if (candidate != null) {
                financeRepository.ignoreCandidate(candidate.id)
                candidateIds += candidate.id
            }
        }

        return BillImportCommitResult(
            created = createdIds.size,
            duplicates = duplicates,
            reconciledCandidates = candidateIds.size,
            transactionIds = createdIds,
        )
    }

    private fun findDuplicate(bill: ImportedBill, existing: List<TransactionEntity>): TransactionEntity? {
        val externalId = bill.externalTransactionId?.trim()?.takeIf { it.isNotEmpty() }
        if (externalId != null) {
            existing.firstOrNull {
                it.deletedAt == null && it.status != "ignored" && it.externalTransactionId == externalId
            }?.let { return it }
        }
        val fingerprint = importFingerprint(bill)
        return existing.firstOrNull { transaction ->
            if (transaction.deletedAt != null || transaction.status == "ignored") return@firstOrNull false
            if (!transaction.sourceType.endsWith("_import") && transaction.sourceType != "file_import") return@firstOrNull false
            val existingFingerprint = importFingerprint(
                sourceType = transaction.sourceType,
                amountCents = transaction.amountCents,
                occurredAt = runCatching { Instant.parse(transaction.occurredAt) }.getOrNull() ?: return@firstOrNull false,
                merchant = transaction.merchant ?: transaction.counterparty ?: transaction.item,
            )
            fingerprint == existingFingerprint
        }
    }

    private fun findCandidate(
        bill: ImportedBill,
        existing: List<TransactionEntity>,
        consumed: Set<String>,
    ): TransactionEntity? {
        return existing.asSequence()
            .filter { it.id !in consumed && it.deletedAt == null }
            .filter { it.status == "candidate" || it.status == "provisional" }
            .filter { it.sourceType == "notification" || it.sourceType.startsWith("vision_screenshot:") }
            .filter { it.amountCents == bill.amountCents }
            .mapNotNull { tx -> runCatching { Instant.parse(tx.occurredAt) }.getOrNull()?.let { tx to it } }
            .filter { (_, time) -> abs(Duration.between(time, bill.occurredAt).seconds) <= 5 * 60 }
            .filter { (tx, _) -> merchantCompatible(tx.merchant ?: tx.counterparty ?: tx.item, bill.merchant ?: bill.item) }
            .minByOrNull { (_, time) -> abs(Duration.between(time, bill.occurredAt).seconds) }
            ?.first
    }

    private fun merchantCompatible(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return true
        val a = normalizeMerchant(left)
        val b = normalizeMerchant(right)
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun normalizeMerchant(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("(有限责任公司|股份有限公司|有限公司|支付|收款|付款)"), "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun importFingerprint(bill: ImportedBill): String = importFingerprint(
        bill.sourceType,
        bill.amountCents,
        bill.occurredAt,
        bill.merchant ?: bill.item,
    )

    private fun importFingerprint(sourceType: String, amountCents: Long, occurredAt: Instant, merchant: String?): String {
        val canonical = listOf(
            sourceType,
            amountCents.toString(),
            occurredAt.epochSecond.toString(),
            normalizeMerchant(merchant.orEmpty()),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun outboxFor(entity: TransactionEntity): OutboxEntity {
        val meta = JSONObject()
            .put("id", entity.id)
            .put("userId", entity.localProfileId)
            .put("createdAt", entity.createdAt)
            .put("updatedAt", entity.updatedAt)
            .put("deletedAt", entity.deletedAt ?: JSONObject.NULL)
            .put("localVersion", entity.localVersion)
            .put("serverVersion", entity.serverVersion ?: JSONObject.NULL)
            .put("modifiedByDevice", entity.modifiedByDevice ?: JSONObject.NULL)
        val payload = JSONObject()
            .put("meta", meta)
            .put("ledgerId", entity.ledgerId ?: JSONObject.NULL)
            .put("transactionType", entity.transactionType)
            .put("amountCents", entity.amountCents)
            .put("currency", entity.currency)
            .put("accountId", entity.accountId ?: JSONObject.NULL)
            .put("toAccountId", entity.toAccountId ?: JSONObject.NULL)
            .put("categoryId", entity.categoryId ?: JSONObject.NULL)
            .put("counterparty", entity.counterparty ?: JSONObject.NULL)
            .put("merchant", entity.merchant ?: JSONObject.NULL)
            .put("item", entity.item ?: JSONObject.NULL)
            .put("note", entity.note ?: JSONObject.NULL)
            .put("occurredAt", entity.occurredAt)
            .put("localDate", entity.localDate)
            .put("status", entity.status)
            .put("sourceType", entity.sourceType)
            .put("externalTransactionId", entity.externalTransactionId ?: JSONObject.NULL)
            .put("recurringTransactionId", entity.recurringTransactionId ?: JSONObject.NULL)
            .put("excludeFromStats", entity.excludeFromStats)
            .put("excludeFromBudget", entity.excludeFromBudget)
            .put("nativeAmountCents", entity.nativeAmountCents ?: JSONObject.NULL)
            .put("nativeCurrency", entity.nativeCurrency ?: JSONObject.NULL)
            .put("exchangeRate", entity.exchangeRate ?: JSONObject.NULL)
        return OutboxEntity(
            changeId = UUID.randomUUID().toString(),
            entityType = "finance.transaction",
            entityId = entity.id,
            operation = "upsert",
            baseServerVersion = entity.serverVersion ?: "0",
            clientModifiedAt = entity.updatedAt,
            payloadJson = payload.toString(),
        )
    }
}
