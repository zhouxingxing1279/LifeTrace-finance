package com.lifetrace.finance.domain

import androidx.room.withTransaction
import com.lifetrace.finance.core.CandidateTransaction
import com.lifetrace.finance.core.TransactionStatus
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class FinanceRepository(private val db: FinanceDatabase, private val deviceId: String) {
    private val finance = db.financeDao()
    private val sync = db.syncDao()
    private val profiles = db.profileDao()
    private val notifications = db.notificationDao()

    suspend fun ensureProfile(): LocalProfileEntity {
        profiles.active()?.let { return it }
        val existing = profiles.first()
        if (existing != null) {
            profiles.setActive(ActiveProfileEntity(profileId = existing.id))
            return existing
        }
        return createProfile(null, "Local")
    }

    suspend fun activateCloudProfile(cloudUserId: String, displayName: String? = null): LocalProfileEntity {
        profiles.byCloudUserId(cloudUserId)?.let {
            profiles.setActive(ActiveProfileEntity(profileId = it.id))
            return it
        }
        val unbound = profiles.firstUnbound()
        if (unbound != null) {
            val now = Instant.now().toString()
            val bound = unbound.copy(
                cloudUserId = cloudUserId,
                displayName = displayName?.takeIf(String::isNotBlank) ?: unbound.displayName,
                updatedAt = now,
            )
            db.withTransaction {
                profiles.upsert(bound)
                profiles.setActive(ActiveProfileEntity(profileId = bound.id))
                val state = sync.state() ?: SyncStateEntity()
                sync.saveState(state.copy(snapshotRequired = true))
            }
            return bound
        }
        return createProfile(cloudUserId, displayName ?: "Cloud")
    }

    private suspend fun createProfile(cloudUserId: String?, displayName: String): LocalProfileEntity {
        val now = Instant.now().toString()
        val created = LocalProfileEntity(
            id = UUID.randomUUID().toString(),
            cloudUserId = cloudUserId,
            displayName = displayName,
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            profiles.upsert(created)
            profiles.setActive(ActiveProfileEntity(profileId = created.id))
            if (cloudUserId != null) {
                val state = sync.state() ?: SyncStateEntity()
                sync.saveState(state.copy(snapshotRequired = true))
            }
        }
        seedDefaults(created.id)
        return created
    }

    fun transactions(profileId: String): Flow<List<TransactionEntity>> = finance.transactions(profileId)
    fun inbox(profileId: String): Flow<List<TransactionEntity>> = finance.inbox(profileId)
    fun accounts(profileId: String): Flow<List<AccountEntity>> = finance.accounts(profileId)
    fun categories(profileId: String): Flow<List<CategoryEntity>> = finance.categories(profileId)
    fun expenseTotal(profileId: String, from: LocalDate, to: LocalDate) = finance.expenseTotal(profileId, from.toString(), to.toString())
    fun incomeTotal(profileId: String, from: LocalDate, to: LocalDate) = finance.incomeTotal(profileId, from.toString(), to.toString())

    suspend fun createTransaction(
        profileId: String,
        type: TransactionType,
        amountCents: Long,
        accountId: String?,
        toAccountId: String? = null,
        categoryId: String? = null,
        merchant: String? = null,
        note: String? = null,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        sourceType: String = "manual",
        externalTransactionId: String? = null,
        occurredAt: Instant = Instant.now(),
    ): String {
        require(amountCents > 0) { "amount must be positive" }
        if (type == TransactionType.TRANSFER) {
            require(!accountId.isNullOrBlank() && !toAccountId.isNullOrBlank() && accountId != toAccountId) {
                "transfer requires different source and destination accounts"
            }
        }
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val entity = TransactionEntity(
            id = id,
            localProfileId = profileId,
            transactionType = type.wire,
            amountCents = amountCents,
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            merchant = merchant,
            note = note,
            occurredAt = occurredAt.toString(),
            localDate = occurredAt.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            status = status.wire,
            sourceType = sourceType,
            externalTransactionId = externalTransactionId,
            createdAt = now,
            updatedAt = now,
            modifiedByDevice = deviceId,
        )
        db.withTransaction {
            finance.upsertTransaction(entity)
            sync.enqueue(outboxFor(entity))
        }
        return id
    }

    /** Persist one notification candidate exactly once. Raw notification text is never stored. */
    suspend fun captureNotificationCandidate(profileId: String, candidate: CandidateTransaction, dedupKey: String): String? {
        val now = Instant.now().toString()
        val txId = UUID.randomUUID().toString()
        val transaction = TransactionEntity(
            id = txId,
            localProfileId = profileId,
            transactionType = TransactionType.EXPENSE.wire,
            amountCents = candidate.amountCents,
            accountId = null,
            categoryId = null,
            merchant = candidate.merchant,
            occurredAt = Instant.ofEpochMilli(candidate.occurredAtMillis).toString(),
            localDate = Instant.ofEpochMilli(candidate.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            status = candidate.status.wire,
            sourceType = "notification",
            createdAt = now,
            updatedAt = now,
            modifiedByDevice = deviceId,
        )
        val evidence = TransactionEvidenceEntity(
            id = UUID.randomUUID().toString(),
            localProfileId = profileId,
            transactionId = txId,
            sourceType = "notification:${candidate.sourcePackage}",
            sourceId = candidate.evidenceHash,
            confidence = candidate.confidence,
            createdAt = now,
            updatedAt = now,
        )
        val event = NotificationEventEntity(
            id = UUID.randomUUID().toString(),
            sourcePackage = candidate.sourcePackage,
            dedupKey = dedupKey,
            evidenceHash = candidate.evidenceHash,
            amountCents = candidate.amountCents,
            merchant = candidate.merchant,
            accountHint = candidate.accountHint,
            confidence = candidate.confidence,
            parserId = candidate.parserId,
            parserVersion = candidate.parserVersion,
            capturedAt = now,
            transactionId = txId,
        )
        var inserted = false
        db.withTransaction {
            if (notifications.insert(event) != -1L) {
                inserted = true
                finance.upsertTransaction(transaction)
                finance.upsertEvidence(evidence)
                sync.enqueue(outboxFor(transaction))
                sync.enqueue(outboxFor(evidence))
            }
        }
        return txId.takeIf { inserted }
    }

    suspend fun confirmCandidate(id: String) = changeStatus(id, TransactionStatus.CONFIRMED)
    suspend fun ignoreCandidate(id: String) = changeStatus(id, TransactionStatus.IGNORED)

    private suspend fun changeStatus(id: String, status: TransactionStatus) {
        val current = finance.transactionById(id) ?: return
        val now = Instant.now().toString()
        val changed = current.copy(status = status.wire, updatedAt = now, localVersion = current.localVersion + 1, modifiedByDevice = deviceId)
        db.withTransaction {
            finance.upsertTransaction(changed)
            sync.enqueue(outboxFor(changed))
        }
    }

    suspend fun updateTransaction(
        id: String,
        amountCents: Long,
        categoryId: String?,
        merchant: String?,
        note: String?,
    ) {
        require(amountCents > 0)
        val current = finance.transactionById(id) ?: return
        val updated = current.copy(
            amountCents = amountCents,
            categoryId = categoryId,
            merchant = merchant,
            note = note,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction {
            finance.upsertTransaction(updated)
            sync.enqueue(outboxFor(updated))
        }
    }

    suspend fun deleteTransaction(id: String) {
        val current = finance.transactionById(id) ?: return
        val now = Instant.now().toString()
        val tombstone = current.copy(
            deletedAt = now,
            updatedAt = now,
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction {
            finance.upsertTransaction(tombstone)
            sync.enqueue(deleteOutbox("finance.transaction", tombstone.id, tombstone.serverVersion ?: "0", now))
        }
    }

    suspend fun createAccount(profileId: String, name: String, type: String): String {
        require(name.isNotBlank())
        val now = Instant.now().toString()
        val entity = AccountEntity(UUID.randomUUID().toString(), profileId, name.trim(), type, createdAt = now, updatedAt = now)
        db.withTransaction { finance.upsertAccount(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun createCategory(profileId: String, name: String, type: TransactionType): String {
        require(name.isNotBlank())
        val now = Instant.now().toString()
        val entity = CategoryEntity(UUID.randomUUID().toString(), profileId, name.trim(), type.wire, createdAt = now, updatedAt = now)
        db.withTransaction { finance.upsertCategory(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    private suspend fun seedDefaults(profileId: String) {
        val now = Instant.now().toString()
        val account = AccountEntity(UUID.randomUUID().toString(), profileId, "默认账户", "other", createdAt = now, updatedAt = now)
        val food = CategoryEntity(UUID.randomUUID().toString(), profileId, "餐饮", TransactionType.EXPENSE.wire, isSystem = true, createdAt = now, updatedAt = now)
        val salary = CategoryEntity(UUID.randomUUID().toString(), profileId, "工资", TransactionType.INCOME.wire, isSystem = true, createdAt = now, updatedAt = now)
        db.withTransaction {
            finance.upsertAccount(account); sync.enqueue(outboxFor(account))
            finance.upsertCategory(food); sync.enqueue(outboxFor(food))
            finance.upsertCategory(salary); sync.enqueue(outboxFor(salary))
        }
    }

    private fun meta(id: String, profileId: String, createdAt: String, updatedAt: String, deletedAt: String?, localVersion: Long, serverVersion: String?) = JSONObject()
        .put("id", id).put("userId", profileId).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt ?: JSONObject.NULL).put("localVersion", localVersion)
        .put("serverVersion", serverVersion ?: JSONObject.NULL).put("modifiedByDevice", deviceId)

    private fun outboxFor(entity: TransactionEntity): OutboxEntity {
        val payload = JSONObject()
            .put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("transactionType", entity.transactionType).put("amountCents", entity.amountCents).put("currency", entity.currency)
            .put("accountId", entity.accountId ?: JSONObject.NULL).put("toAccountId", entity.toAccountId ?: JSONObject.NULL)
            .put("categoryId", entity.categoryId ?: JSONObject.NULL).put("counterparty", entity.counterparty ?: JSONObject.NULL)
            .put("merchant", entity.merchant ?: JSONObject.NULL).put("item", entity.item ?: JSONObject.NULL).put("note", entity.note ?: JSONObject.NULL)
            .put("occurredAt", entity.occurredAt).put("localDate", entity.localDate).put("status", entity.status)
            .put("sourceType", entity.sourceType).put("externalTransactionId", entity.externalTransactionId ?: JSONObject.NULL)
        return outbox("finance.transaction", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: AccountEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("name", entity.name).put("accountType", entity.accountType).put("openingBalanceCents", entity.openingBalanceCents ?: JSONObject.NULL)
            .put("balanceAt", entity.balanceAt ?: JSONObject.NULL).put("last4", entity.last4 ?: JSONObject.NULL).put("color", entity.color)
            .put("icon", entity.icon).put("isArchived", entity.isArchived).put("currency", entity.currency)
        return outbox("finance.account", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: CategoryEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("name", entity.name).put("categoryType", entity.categoryType).put("parentId", entity.parentId ?: JSONObject.NULL)
            .put("icon", entity.icon ?: JSONObject.NULL).put("color", entity.color ?: JSONObject.NULL).put("isSystem", entity.isSystem).put("isArchived", entity.isArchived)
        return outbox("finance.category", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: TransactionEvidenceEntity): OutboxEntity {
        val payload = JSONObject()
            .put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("transactionId", entity.transactionId)
            .put("sourceType", entity.sourceType)
            .put("sourceId", entity.sourceId ?: JSONObject.NULL)
            .put("externalTransactionId", entity.externalTransactionId ?: JSONObject.NULL)
            .put("confidence", entity.confidence ?: JSONObject.NULL)
        val dependency = JSONArray().put(JSONObject().put("entityType", "finance.transaction").put("entityId", entity.transactionId))
        return outbox(
            entityType = "finance.transaction_evidence",
            entityId = entity.id,
            base = entity.serverVersion ?: "0",
            modifiedAt = entity.updatedAt,
            payload = payload.toString(),
            dependenciesJson = dependency.toString(),
        )
    }

    private fun outbox(
        entityType: String,
        entityId: String,
        base: String,
        modifiedAt: String,
        payload: String,
        atomicGroupId: String? = null,
        dependenciesJson: String = "[]",
    ) = OutboxEntity(
        changeId = UUID.randomUUID().toString(), entityType = entityType, entityId = entityId, operation = "upsert",
        baseServerVersion = base, clientModifiedAt = modifiedAt, payloadJson = payload,
        atomicGroupId = atomicGroupId, dependenciesJson = dependenciesJson,
    )

    private fun deleteOutbox(entityType: String, entityId: String, base: String, modifiedAt: String) = OutboxEntity(
        changeId = UUID.randomUUID().toString(), entityType = entityType, entityId = entityId, operation = "delete",
        baseServerVersion = base, clientModifiedAt = modifiedAt, payloadJson = null,
    )
}
