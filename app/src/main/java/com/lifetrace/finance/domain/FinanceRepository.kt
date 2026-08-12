package com.lifetrace.finance.domain

import androidx.room.withTransaction
import com.lifetrace.finance.core.CandidateTransaction
import com.lifetrace.finance.core.StandardCategories
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
    private val bookkeeping = db.bookkeepingDao()
    private val sync = db.syncDao()
    private val profiles = db.profileDao()
    private val notifications = db.notificationDao()

    suspend fun ensureProfile(): LocalProfileEntity {
        profiles.active()?.let {
            ensureDefaultLedger(it.id)
            return it
        }
        val existing = profiles.first()
        if (existing != null) {
            profiles.setActive(ActiveProfileEntity(profileId = existing.id))
            ensureDefaultLedger(existing.id)
            return existing
        }
        return createProfile(null, "Local")
    }

    suspend fun activateCloudProfile(cloudUserId: String, displayName: String? = null): LocalProfileEntity {
        profiles.byCloudUserId(cloudUserId)?.let {
            profiles.setActive(ActiveProfileEntity(profileId = it.id))
            ensureDefaultLedger(it.id)
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
                sync.saveState((sync.state() ?: SyncStateEntity()).copy(snapshotRequired = true))
            }
            ensureDefaultLedger(bound.id)
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
                sync.saveState((sync.state() ?: SyncStateEntity()).copy(snapshotRequired = true))
            }
        }
        seedDefaults(created.id)
        return created
    }

    fun transactions(profileId: String): Flow<List<TransactionEntity>> = finance.transactions(profileId)
    fun inbox(profileId: String): Flow<List<TransactionEntity>> = finance.inbox(profileId)
    fun accounts(profileId: String): Flow<List<AccountEntity>> = finance.accounts(profileId)
    fun categories(profileId: String): Flow<List<CategoryEntity>> = finance.categories(profileId)
    fun ledgers(profileId: String): Flow<List<LedgerEntity>> = bookkeeping.ledgers(profileId)
    fun tags(profileId: String, ledgerId: String): Flow<List<TagEntity>> = bookkeeping.tags(profileId, ledgerId)
    fun tagsForTransaction(transactionId: String): Flow<List<TagEntity>> = bookkeeping.tagsForTransaction(transactionId)
    fun budgets(profileId: String, ledgerId: String): Flow<List<BudgetEntity>> = bookkeeping.budgets(profileId, ledgerId)
    fun recurringTransactions(profileId: String, ledgerId: String): Flow<List<RecurringTransactionEntity>> = bookkeeping.recurringTransactions(profileId, ledgerId)
    fun expenseTotal(profileId: String, from: LocalDate, to: LocalDate) = finance.expenseTotal(profileId, from.toString(), to.toString())
    fun incomeTotal(profileId: String, from: LocalDate, to: LocalDate) = finance.incomeTotal(profileId, from.toString(), to.toString())
    fun totalBudgetUsage(profileId: String, ledgerId: String, from: LocalDate, to: LocalDate) = finance.totalBudgetUsage(profileId, ledgerId, from.toString(), to.toString())
    fun categoryBudgetUsage(profileId: String, ledgerId: String, categoryId: String, from: LocalDate, to: LocalDate) = finance.categoryBudgetUsage(profileId, ledgerId, categoryId, from.toString(), to.toString())

    suspend fun ensureDefaultLedger(profileId: String): LedgerEntity {
        bookkeeping.firstLedger(profileId)?.let { return it }
        val now = Instant.now().toString()
        val ledger = LedgerEntity(
            id = "default-ledger-$profileId",
            localProfileId = profileId,
            name = "默认账本",
            currency = "CNY",
            createdAt = now,
            updatedAt = now,
            modifiedByDevice = deviceId,
        )
        db.withTransaction {
            bookkeeping.upsertLedger(ledger)
            sync.enqueue(outboxFor(ledger))
        }
        return ledger
    }

    suspend fun createLedger(profileId: String, name: String, currency: String = "CNY", monthStartDay: Int = 1): String {
        require(name.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}")))
        require(monthStartDay in 1..28)
        val now = Instant.now().toString()
        val entity = LedgerEntity(
            id = UUID.randomUUID().toString(),
            localProfileId = profileId,
            name = name.trim(),
            currency = currency,
            monthStartDay = monthStartDay,
            createdAt = now,
            updatedAt = now,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertLedger(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

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
        ledgerId: String? = null,
        excludeFromStats: Boolean = false,
        excludeFromBudget: Boolean = false,
    ): String {
        require(amountCents > 0) { "amount must be positive" }
        if (type == TransactionType.TRANSFER) {
            require(!accountId.isNullOrBlank() && !toAccountId.isNullOrBlank() && accountId != toAccountId) {
                "transfer requires different source and destination accounts"
            }
        }
        val account = accountId?.let { finance.accountById(it) }
        val ledger = ledgerId ?: account?.ledgerId ?: ensureDefaultLedger(profileId).id
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val entity = TransactionEntity(
            id = id,
            localProfileId = profileId,
            ledgerId = ledger,
            transactionType = type.wire,
            amountCents = amountCents,
            currency = account?.currency ?: "CNY",
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
            excludeFromStats = excludeFromStats,
            excludeFromBudget = excludeFromBudget,
            createdAt = now,
            updatedAt = now,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { finance.upsertTransaction(entity); sync.enqueue(outboxFor(entity)) }
        return id
    }

    /** Persist one notification candidate exactly once. Raw notification text is never stored. */
    suspend fun captureNotificationCandidate(profileId: String, candidate: CandidateTransaction, dedupKey: String): String? {
        val now = Instant.now().toString()
        val txId = UUID.randomUUID().toString()
        val ledgerId = ensureDefaultLedger(profileId).id
        val transaction = TransactionEntity(
            id = txId,
            localProfileId = profileId,
            ledgerId = ledgerId,
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
            id = UUID.randomUUID().toString(), sourcePackage = candidate.sourcePackage, dedupKey = dedupKey,
            evidenceHash = candidate.evidenceHash, amountCents = candidate.amountCents,
            merchant = candidate.merchant, accountHint = candidate.accountHint, confidence = candidate.confidence,
            parserId = candidate.parserId, parserVersion = candidate.parserVersion, capturedAt = now, transactionId = txId,
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

    suspend fun confirmCandidate(id: String, categoryId: String? = null) = changeStatus(id, TransactionStatus.CONFIRMED, categoryId)
    suspend fun ignoreCandidate(id: String) = changeStatus(id, TransactionStatus.IGNORED)

    private suspend fun changeStatus(id: String, status: TransactionStatus, categoryId: String? = null) {
        val current = finance.transactionById(id) ?: return
        val changed = current.copy(
            status = status.wire,
            categoryId = categoryId ?: current.categoryId,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { finance.upsertTransaction(changed); sync.enqueue(outboxFor(changed)) }
    }

    suspend fun updateTransaction(id: String, amountCents: Long, categoryId: String?, merchant: String?, note: String?) {
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
        db.withTransaction { finance.upsertTransaction(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun setTransactionFlags(id: String, excludeFromStats: Boolean, excludeFromBudget: Boolean) {
        val current = finance.transactionById(id) ?: return
        val updated = current.copy(
            excludeFromStats = excludeFromStats,
            excludeFromBudget = excludeFromBudget,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { finance.upsertTransaction(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun deleteTransaction(id: String) {
        val current = finance.transactionById(id) ?: return
        val now = Instant.now().toString()
        val tombstone = current.copy(deletedAt = now, updatedAt = now, localVersion = current.localVersion + 1, modifiedByDevice = deviceId)
        db.withTransaction {
            finance.upsertTransaction(tombstone)
            sync.enqueue(deleteOutbox("finance.transaction", tombstone.id, tombstone.serverVersion ?: "0", now))
        }
    }

    suspend fun createAccount(profileId: String, name: String, type: String): String {
        require(name.isNotBlank())
        val ledger = ensureDefaultLedger(profileId)
        val now = Instant.now().toString()
        val entity = AccountEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledger.id,
            name = name.trim(), accountType = type, createdAt = now, updatedAt = now,
        )
        db.withTransaction { finance.upsertAccount(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun createCategory(profileId: String, name: String, type: TransactionType, parentId: String? = null): String {
        require(name.isNotBlank())
        val ledger = ensureDefaultLedger(profileId)
        val parent = parentId?.let { finance.categoryById(it) }
        val now = Instant.now().toString()
        val entity = CategoryEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledger.id,
            name = name.trim(), categoryType = type.wire, parentId = parentId,
            level = if (parent == null) 1 else (parent.level + 1).coerceAtMost(2),
            createdAt = now, updatedAt = now,
        )
        db.withTransaction { finance.upsertCategory(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun createTag(profileId: String, ledgerId: String, name: String, color: String? = null): String {
        require(name.isNotBlank())
        val now = Instant.now().toString()
        val entity = TagEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledgerId,
            name = name.trim(), color = color, createdAt = now, updatedAt = now, modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertTag(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun addTagToTransaction(profileId: String, transactionId: String, tagId: String): String {
        bookkeeping.transactionTag(transactionId, tagId)?.let { return it.id }
        require(finance.transactionById(transactionId) != null) { "transaction not found" }
        require(bookkeeping.tagById(tagId) != null) { "tag not found" }
        val now = Instant.now().toString()
        val entity = TransactionTagEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId,
            transactionId = transactionId, tagId = tagId, createdAt = now, updatedAt = now, modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertTransactionTag(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun createBudget(
        profileId: String,
        ledgerId: String,
        amountCents: Long,
        categoryId: String? = null,
        period: String = "monthly",
        startDay: Int = 1,
    ): String {
        require(amountCents > 0)
        require(period in setOf("monthly", "weekly", "yearly"))
        val now = Instant.now().toString()
        val ledger = bookkeeping.ledgerById(ledgerId) ?: error("ledger not found")
        val entity = BudgetEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledgerId,
            budgetType = if (categoryId == null) "total" else "category", categoryId = categoryId,
            amountCents = amountCents, currency = ledger.currency, period = period, startDay = startDay,
            createdAt = now, updatedAt = now, modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertBudget(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun createRecurringTransaction(
        profileId: String,
        ledgerId: String,
        type: TransactionType,
        amountCents: Long,
        accountId: String?,
        toAccountId: String? = null,
        categoryId: String? = null,
        note: String? = null,
        frequency: String = "monthly",
        interval: Int = 1,
        startDate: LocalDate = LocalDate.now(),
        dayOfMonth: Int? = null,
        dayOfWeek: Int? = null,
        monthOfYear: Int? = null,
        endDate: LocalDate? = null,
    ): String {
        require(amountCents > 0 && interval > 0)
        require(frequency in setOf("daily", "weekly", "monthly", "yearly"))
        if (type == TransactionType.TRANSFER) require(!accountId.isNullOrBlank() && !toAccountId.isNullOrBlank() && accountId != toAccountId)
        val ledger = bookkeeping.ledgerById(ledgerId) ?: error("ledger not found")
        val now = Instant.now().toString()
        val entity = RecurringTransactionEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledgerId,
            transactionType = type.wire, amountCents = amountCents, currency = ledger.currency,
            categoryId = categoryId, accountId = accountId, toAccountId = toAccountId, note = note,
            frequency = frequency, interval = interval, dayOfMonth = dayOfMonth, dayOfWeek = dayOfWeek,
            monthOfYear = monthOfYear, startDate = startDate.toString(), endDate = endDate?.toString(),
            createdAt = now, updatedAt = now, modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertRecurring(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun ensureStandardCategories(profileId: String) {
        val existing = finance.categoryList(profileId).map { "${it.categoryType}|${it.name.trim()}" }.toSet()
        val missing = StandardCategories.ALL.filter { "${it.type.wire}|${it.name}" !in existing }
        if (missing.isEmpty()) return
        val ledger = ensureDefaultLedger(profileId)
        val now = Instant.now().toString()
        db.withTransaction {
            missing.forEach { spec ->
                val entity = CategoryEntity(
                    id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledger.id,
                    name = spec.name, categoryType = spec.type.wire, isSystem = true, createdAt = now, updatedAt = now,
                )
                finance.upsertCategory(entity); sync.enqueue(outboxFor(entity))
            }
        }
    }

    suspend fun archiveAccount(id: String) {
        val current = finance.accountById(id) ?: return
        if (current.isArchived) return
        val updated = current.copy(isArchived = true, updatedAt = Instant.now().toString(), localVersion = current.localVersion + 1)
        db.withTransaction { finance.upsertAccount(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun hideAccount(id: String, hidden: Boolean = true) {
        val current = finance.accountById(id) ?: return
        val updated = current.copy(isHidden = hidden, updatedAt = Instant.now().toString(), localVersion = current.localVersion + 1)
        db.withTransaction { finance.upsertAccount(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun archiveCategory(id: String) {
        val current = finance.categoryById(id) ?: return
        if (current.isArchived) return
        val updated = current.copy(isArchived = true, updatedAt = Instant.now().toString(), localVersion = current.localVersion + 1)
        db.withTransaction { finance.upsertCategory(updated); sync.enqueue(outboxFor(updated)) }
    }

    private suspend fun seedDefaults(profileId: String) {
        val now = Instant.now().toString()
        val ledger = LedgerEntity(
            id = "default-ledger-$profileId", localProfileId = profileId, name = "默认账本",
            createdAt = now, updatedAt = now, modifiedByDevice = deviceId,
        )
        val account = AccountEntity(
            id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledger.id,
            name = "默认账户", accountType = "other", createdAt = now, updatedAt = now,
        )
        val categories = StandardCategories.ALL.map { spec ->
            CategoryEntity(
                id = UUID.randomUUID().toString(), localProfileId = profileId, ledgerId = ledger.id,
                name = spec.name, categoryType = spec.type.wire, isSystem = true, createdAt = now, updatedAt = now,
            )
        }
        db.withTransaction {
            bookkeeping.upsertLedger(ledger); sync.enqueue(outboxFor(ledger))
            finance.upsertAccount(account); sync.enqueue(outboxFor(account))
            categories.forEach { finance.upsertCategory(it); sync.enqueue(outboxFor(it)) }
        }
    }

    private fun meta(id: String, profileId: String, createdAt: String, updatedAt: String, deletedAt: String?, localVersion: Long, serverVersion: String?, modifiedByDevice: String? = deviceId) = JSONObject()
        .put("id", id).put("userId", profileId).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt ?: JSONObject.NULL).put("localVersion", localVersion)
        .put("serverVersion", serverVersion ?: JSONObject.NULL).put("modifiedByDevice", modifiedByDevice ?: JSONObject.NULL)

    private fun dependency(entityType: String, entityId: String) = JSONObject().put("entityType", entityType).put("entityId", entityId)
    private fun dependencies(vararg values: JSONObject) = JSONArray().apply { values.forEach(::put) }.toString()

    private fun outboxFor(entity: LedgerEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("name", entity.name).put("currency", entity.currency).put("ledgerType", entity.ledgerType)
            .put("monthStartDay", entity.monthStartDay).put("sortOrder", entity.sortOrder).put("isArchived", entity.isArchived)
        return outbox("finance.ledger", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: TransactionEntity): OutboxEntity {
        val payload = JSONObject()
            .put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId ?: JSONObject.NULL)
            .put("transactionType", entity.transactionType).put("amountCents", entity.amountCents).put("currency", entity.currency)
            .put("accountId", entity.accountId ?: JSONObject.NULL).put("toAccountId", entity.toAccountId ?: JSONObject.NULL)
            .put("categoryId", entity.categoryId ?: JSONObject.NULL).put("counterparty", entity.counterparty ?: JSONObject.NULL)
            .put("merchant", entity.merchant ?: JSONObject.NULL).put("item", entity.item ?: JSONObject.NULL).put("note", entity.note ?: JSONObject.NULL)
            .put("occurredAt", entity.occurredAt).put("localDate", entity.localDate).put("status", entity.status)
            .put("sourceType", entity.sourceType).put("externalTransactionId", entity.externalTransactionId ?: JSONObject.NULL)
            .put("recurringTransactionId", entity.recurringTransactionId ?: JSONObject.NULL)
            .put("excludeFromStats", entity.excludeFromStats).put("excludeFromBudget", entity.excludeFromBudget)
            .put("nativeAmountCents", entity.nativeAmountCents ?: JSONObject.NULL).put("nativeCurrency", entity.nativeCurrency ?: JSONObject.NULL)
            .put("exchangeRate", entity.exchangeRate ?: JSONObject.NULL)
        return outbox("finance.transaction", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: AccountEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("ledgerId", entity.ledgerId ?: JSONObject.NULL).put("name", entity.name).put("accountType", entity.accountType)
            .put("openingBalanceCents", entity.openingBalanceCents ?: JSONObject.NULL).put("balanceAt", entity.balanceAt ?: JSONObject.NULL)
            .put("last4", entity.last4 ?: JSONObject.NULL).put("color", entity.color).put("icon", entity.icon)
            .put("isArchived", entity.isArchived).put("currency", entity.currency).put("sortOrder", entity.sortOrder)
            .put("creditLimitCents", entity.creditLimitCents ?: JSONObject.NULL).put("billingDay", entity.billingDay ?: JSONObject.NULL)
            .put("paymentDueDay", entity.paymentDueDay ?: JSONObject.NULL).put("bankName", entity.bankName ?: JSONObject.NULL)
            .put("note", entity.note ?: JSONObject.NULL).put("isHidden", entity.isHidden)
        return outbox("finance.account", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: CategoryEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("ledgerId", entity.ledgerId ?: JSONObject.NULL).put("name", entity.name).put("categoryType", entity.categoryType)
            .put("parentId", entity.parentId ?: JSONObject.NULL).put("icon", entity.icon ?: JSONObject.NULL)
            .put("color", entity.color ?: JSONObject.NULL).put("isSystem", entity.isSystem).put("isArchived", entity.isArchived)
            .put("sortOrder", entity.sortOrder).put("level", entity.level).put("iconType", entity.iconType)
            .put("customIconFileId", entity.customIconFileId ?: JSONObject.NULL)
        return outbox("finance.category", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: RecurringTransactionEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId).put("transactionType", entity.transactionType).put("amountCents", entity.amountCents)
            .put("currency", entity.currency).put("categoryId", entity.categoryId ?: JSONObject.NULL)
            .put("accountId", entity.accountId ?: JSONObject.NULL).put("toAccountId", entity.toAccountId ?: JSONObject.NULL)
            .put("note", entity.note ?: JSONObject.NULL).put("frequency", entity.frequency).put("interval", entity.interval)
            .put("dayOfMonth", entity.dayOfMonth ?: JSONObject.NULL).put("dayOfWeek", entity.dayOfWeek ?: JSONObject.NULL)
            .put("monthOfYear", entity.monthOfYear ?: JSONObject.NULL).put("startDate", entity.startDate)
            .put("endDate", entity.endDate ?: JSONObject.NULL).put("lastGeneratedDate", entity.lastGeneratedDate ?: JSONObject.NULL)
            .put("enabled", entity.enabled)
        return outbox("finance.recurring_transaction", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: TagEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId).put("name", entity.name).put("color", entity.color ?: JSONObject.NULL)
            .put("sortOrder", entity.sortOrder).put("isArchived", entity.isArchived)
        return outbox("finance.tag", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: TransactionTagEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("transactionId", entity.transactionId).put("tagId", entity.tagId)
        return outbox(
            "finance.transaction_tag", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString(),
            dependenciesJson = dependencies(dependency("finance.transaction", entity.transactionId), dependency("finance.tag", entity.tagId)),
        )
    }

    private fun outboxFor(entity: BudgetEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId).put("budgetType", entity.budgetType).put("categoryId", entity.categoryId ?: JSONObject.NULL)
            .put("amountCents", entity.amountCents).put("currency", entity.currency).put("period", entity.period)
            .put("startDay", entity.startDay).put("enabled", entity.enabled)
        return outbox("finance.budget", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: TransactionAttachmentEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("transactionId", entity.transactionId).put("fileName", entity.fileName).put("originalName", entity.originalName ?: JSONObject.NULL)
            .put("fileSize", entity.fileSize ?: JSONObject.NULL).put("width", entity.width ?: JSONObject.NULL)
            .put("height", entity.height ?: JSONObject.NULL).put("sortOrder", entity.sortOrder).put("fileId", entity.fileId ?: JSONObject.NULL)
            .put("sha256", entity.sha256 ?: JSONObject.NULL)
        return outbox("finance.transaction_attachment", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString(), dependenciesJson = dependencies(dependency("finance.transaction", entity.transactionId)))
    }

    private fun outboxFor(entity: TransactionEvidenceEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion))
            .put("transactionId", entity.transactionId).put("sourceType", entity.sourceType)
            .put("sourceId", entity.sourceId ?: JSONObject.NULL).put("externalTransactionId", entity.externalTransactionId ?: JSONObject.NULL)
            .put("confidence", entity.confidence ?: JSONObject.NULL)
        return outbox("finance.transaction_evidence", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString(), dependenciesJson = dependencies(dependency("finance.transaction", entity.transactionId)))
    }

    private fun outbox(entityType: String, entityId: String, base: String, modifiedAt: String, payload: String, atomicGroupId: String? = null, dependenciesJson: String = "[]") = OutboxEntity(
        changeId = UUID.randomUUID().toString(), entityType = entityType, entityId = entityId, operation = "upsert",
        baseServerVersion = base, clientModifiedAt = modifiedAt, payloadJson = payload,
        atomicGroupId = atomicGroupId, dependenciesJson = dependenciesJson,
    )

    private fun deleteOutbox(entityType: String, entityId: String, base: String, modifiedAt: String) = OutboxEntity(
        changeId = UUID.randomUUID().toString(), entityType = entityType, entityId = entityId, operation = "delete",
        baseServerVersion = base, clientModifiedAt = modifiedAt, payloadJson = null,
    )
}
