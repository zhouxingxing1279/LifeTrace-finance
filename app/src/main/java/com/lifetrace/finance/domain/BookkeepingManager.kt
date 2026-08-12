package com.lifetrace.finance.domain

import androidx.room.withTransaction
import com.lifetrace.finance.core.TransactionStatus
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Domain write boundary for the BeeCount-inspired advanced entities introduced in Room v2.
 * Every synchronized mutation is committed with a LifeTrace sync_outbox row in the same Room transaction.
 */
class BookkeepingManager(
    private val db: FinanceDatabase,
    private val repository: FinanceRepository,
    private val deviceId: String,
) {
    private val finance = db.financeDao()
    private val bookkeeping = db.bookkeepingDao()
    private val sync = db.syncDao()

    fun ledgers(profileId: String): Flow<List<LedgerEntity>> = bookkeeping.ledgers(profileId)
    fun accounts(profileId: String, ledgerId: String): Flow<List<AccountEntity>> =
        finance.accounts(profileId).map { rows -> rows.filter { it.ledgerId == ledgerId } }
    fun categories(profileId: String, ledgerId: String): Flow<List<CategoryEntity>> =
        finance.categories(profileId).map { rows -> rows.filter { it.ledgerId == ledgerId } }
    fun transactions(profileId: String, ledgerId: String): Flow<List<TransactionEntity>> =
        finance.transactions(profileId).map { rows -> rows.filter { it.ledgerId == ledgerId } }
    fun tags(profileId: String, ledgerId: String): Flow<List<TagEntity>> = bookkeeping.tags(profileId, ledgerId)
    fun budgets(profileId: String, ledgerId: String): Flow<List<BudgetEntity>> = bookkeeping.budgets(profileId, ledgerId)
    fun recurring(profileId: String, ledgerId: String): Flow<List<RecurringTransactionEntity>> = bookkeeping.recurringTransactions(profileId, ledgerId)
    fun tagsForTransaction(transactionId: String): Flow<List<TagEntity>> = bookkeeping.tagsForTransaction(transactionId)

    suspend fun createLedger(profileId: String, name: String, currency: String = "CNY", monthStartDay: Int = 1): String =
        repository.createLedger(profileId, name, currency, monthStartDay)

    suspend fun archiveLedger(id: String) {
        val current = bookkeeping.ledgerById(id) ?: return
        if (current.isArchived) return
        val updated = current.copy(
            isArchived = true,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertLedger(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun createAccount(
        profileId: String,
        ledgerId: String,
        name: String,
        accountType: String,
        currency: String = "CNY",
        openingBalanceCents: Long? = null,
        bankName: String? = null,
        last4: String? = null,
        creditLimitCents: Long? = null,
        billingDay: Int? = null,
        paymentDueDay: Int? = null,
        note: String? = null,
    ): String {
        require(name.isNotBlank())
        require(bookkeeping.ledgerById(ledgerId)?.localProfileId == profileId) { "账本不存在" }
        require(currency.matches(Regex("[A-Z]{3}")))
        require(last4 == null || last4.matches(Regex("\\d{4}"))) { "卡号尾四位必须是 4 位数字" }
        require(billingDay == null || billingDay in 1..31)
        require(paymentDueDay == null || paymentDueDay in 1..31)
        val now = Instant.now().toString()
        val entity = AccountEntity(
            id = UUID.randomUUID().toString(),
            localProfileId = profileId,
            ledgerId = ledgerId,
            name = name.trim(),
            accountType = accountType,
            openingBalanceCents = openingBalanceCents,
            last4 = last4,
            currency = currency,
            creditLimitCents = creditLimitCents,
            billingDay = billingDay,
            paymentDueDay = paymentDueDay,
            bankName = bankName?.trim()?.takeIf(String::isNotBlank),
            note = note?.trim()?.takeIf(String::isNotBlank),
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction { finance.upsertAccount(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun updateAccount(
        id: String,
        name: String,
        accountType: String,
        currency: String,
        openingBalanceCents: Long?,
        bankName: String?,
        last4: String?,
        creditLimitCents: Long?,
        billingDay: Int?,
        paymentDueDay: Int?,
        note: String?,
        hidden: Boolean,
    ) {
        require(name.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}")))
        require(last4 == null || last4.matches(Regex("\\d{4}")))
        require(billingDay == null || billingDay in 1..31)
        require(paymentDueDay == null || paymentDueDay in 1..31)
        val current = finance.accountById(id) ?: error("账户不存在")
        val updated = current.copy(
            name = name.trim(),
            accountType = accountType,
            currency = currency,
            openingBalanceCents = openingBalanceCents,
            bankName = bankName?.trim()?.takeIf(String::isNotBlank),
            last4 = last4,
            creditLimitCents = creditLimitCents,
            billingDay = billingDay,
            paymentDueDay = paymentDueDay,
            note = note?.trim()?.takeIf(String::isNotBlank),
            isHidden = hidden,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
        )
        db.withTransaction { finance.upsertAccount(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun createCategory(
        profileId: String,
        ledgerId: String,
        name: String,
        type: TransactionType,
        parentId: String? = null,
    ): String {
        require(name.isNotBlank())
        val parent = parentId?.let { finance.categoryById(it) }
        require(parent == null || parent.ledgerId == ledgerId) { "父分类必须属于当前账本" }
        require(parent == null || parent.level == 1) { "仅支持二级分类" }
        val now = Instant.now().toString()
        val entity = CategoryEntity(
            id = UUID.randomUUID().toString(),
            localProfileId = profileId,
            ledgerId = ledgerId,
            name = name.trim(),
            categoryType = type.wire,
            parentId = parentId,
            level = if (parent == null) 1 else 2,
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction { finance.upsertCategory(entity); sync.enqueue(outboxFor(entity)) }
        return entity.id
    }

    suspend fun archiveCategory(id: String) = repository.archiveCategory(id)

    suspend fun createTag(profileId: String, ledgerId: String, name: String, color: String? = null): String =
        repository.createTag(profileId, ledgerId, name, color)

    suspend fun archiveTag(id: String) {
        val current = bookkeeping.tagById(id) ?: return
        val updated = current.copy(
            isArchived = true,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertTag(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun addTag(profileId: String, transactionId: String, tagId: String): String =
        repository.addTagToTransaction(profileId, transactionId, tagId)

    suspend fun removeTag(transactionId: String, tagId: String) {
        val current = bookkeeping.transactionTag(transactionId, tagId) ?: return
        val now = Instant.now().toString()
        val deleted = current.copy(
            deletedAt = now,
            updatedAt = now,
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction {
            bookkeeping.upsertTransactionTag(deleted)
            sync.enqueue(deleteOutbox("finance.transaction_tag", deleted.id, deleted.serverVersion ?: "0", now))
        }
    }

    suspend fun createBudget(
        profileId: String,
        ledgerId: String,
        amountCents: Long,
        categoryId: String? = null,
        period: String = "monthly",
        startDay: Int = 1,
    ): String = repository.createBudget(profileId, ledgerId, amountCents, categoryId, period, startDay)

    suspend fun setBudgetEnabled(id: String, enabled: Boolean) {
        val current = bookkeeping.budgetById(id) ?: return
        val updated = current.copy(
            enabled = enabled,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertBudget(updated); sync.enqueue(outboxFor(updated)) }
    }

    fun budgetPeriod(budget: BudgetEntity, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (budget.period) {
        "weekly" -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            start to start.plusDays(6)
        }
        "yearly" -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
        else -> {
            val day = budget.startDay.coerceIn(1, 28)
            val thisMonth = today.withDayOfMonth(day)
            val start = if (today >= thisMonth) thisMonth else thisMonth.minusMonths(1)
            start to start.plusMonths(1).minusDays(1)
        }
    }

    suspend fun createRecurring(
        profileId: String,
        ledgerId: String,
        type: TransactionType,
        amountCents: Long,
        accountId: String?,
        toAccountId: String? = null,
        categoryId: String? = null,
        note: String? = null,
        frequency: String,
        interval: Int = 1,
        startDate: LocalDate = LocalDate.now(),
        dayOfMonth: Int? = null,
        dayOfWeek: Int? = null,
        monthOfYear: Int? = null,
        endDate: LocalDate? = null,
    ): String = repository.createRecurringTransaction(
        profileId, ledgerId, type, amountCents, accountId, toAccountId, categoryId, note,
        frequency, interval, startDate, dayOfMonth, dayOfWeek, monthOfYear, endDate,
    )

    suspend fun setRecurringEnabled(id: String, enabled: Boolean) {
        val current = bookkeeping.recurringById(id) ?: return
        val updated = current.copy(
            enabled = enabled,
            updatedAt = Instant.now().toString(),
            localVersion = current.localVersion + 1,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { bookkeeping.upsertRecurring(updated); sync.enqueue(outboxFor(updated)) }
    }

    suspend fun executeDueRecurring(profileId: String, through: LocalDate = LocalDate.now()): Int {
        val ledgers = bookkeeping.ledgers(profileId).first()
        val existingExternalIds = finance.transactions(profileId).first()
            .mapNotNull { it.externalTransactionId }
            .toMutableSet()
        var generated = 0
        for (ledger in ledgers) {
            val rules = bookkeeping.recurringTransactions(profileId, ledger.id).first()
            for (rule in rules) {
                if (!rule.enabled || rule.deletedAt != null) continue
                val dueDates = occurrencesThrough(rule, through)
                var lastProcessed = rule.lastGeneratedDate?.let(LocalDate::parse)
                for (date in dueDates) {
                    val externalId = "recurring:${rule.id}:$date"
                    if (externalId !in existingExternalIds) {
                        createRecurringOccurrence(rule, date, externalId)
                        existingExternalIds += externalId
                        generated++
                    }
                    if (lastProcessed == null || date > lastProcessed) lastProcessed = date
                }
                if (lastProcessed != null && lastProcessed.toString() != rule.lastGeneratedDate) {
                    val updated = rule.copy(
                        lastGeneratedDate = lastProcessed.toString(),
                        updatedAt = Instant.now().toString(),
                        localVersion = rule.localVersion + 1,
                        modifiedByDevice = deviceId,
                    )
                    db.withTransaction { bookkeeping.upsertRecurring(updated); sync.enqueue(outboxFor(updated)) }
                }
            }
        }
        return generated
    }

    private suspend fun createRecurringOccurrence(rule: RecurringTransactionEntity, date: LocalDate, externalId: String) {
        val now = Instant.now().toString()
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            localProfileId = rule.localProfileId,
            ledgerId = rule.ledgerId,
            transactionType = rule.transactionType,
            amountCents = rule.amountCents,
            currency = rule.currency,
            accountId = rule.accountId,
            toAccountId = rule.toAccountId,
            categoryId = rule.categoryId,
            note = rule.note,
            occurredAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
            localDate = date.toString(),
            status = TransactionStatus.CONFIRMED.wire,
            sourceType = "recurring",
            externalTransactionId = externalId,
            recurringTransactionId = rule.id,
            createdAt = now,
            updatedAt = now,
            modifiedByDevice = deviceId,
        )
        db.withTransaction { finance.upsertTransaction(entity); sync.enqueue(outboxFor(entity)) }
    }

    internal fun occurrencesThrough(rule: RecurringTransactionEntity, through: LocalDate): List<LocalDate> {
        val start = LocalDate.parse(rule.startDate)
        val end = rule.endDate?.let(LocalDate::parse)
        val last = rule.lastGeneratedDate?.let(LocalDate::parse)
        val first = firstOccurrence(rule, start)
        val result = mutableListOf<LocalDate>()
        var cursor = first
        var guard = 0
        while (cursor <= through && (end == null || cursor <= end) && guard < 2048) {
            if ((last == null || cursor > last) && cursor >= start) result += cursor
            cursor = nextOccurrence(rule, cursor)
            guard++
        }
        return result
    }

    private fun firstOccurrence(rule: RecurringTransactionEntity, start: LocalDate): LocalDate = when (rule.frequency) {
        "weekly" -> {
            val target = DayOfWeek.of((rule.dayOfWeek ?: start.dayOfWeek.value).coerceIn(1, 7))
            start.with(TemporalAdjusters.nextOrSame(target))
        }
        "monthly" -> monthlyDate(start, rule.dayOfMonth ?: start.dayOfMonth, allowNext = true)
        "yearly" -> yearlyDate(start, rule.monthOfYear ?: start.monthValue, rule.dayOfMonth ?: start.dayOfMonth, allowNext = true)
        else -> start
    }

    private fun nextOccurrence(rule: RecurringTransactionEntity, current: LocalDate): LocalDate {
        val step = rule.interval.coerceAtLeast(1).toLong()
        return when (rule.frequency) {
            "weekly" -> current.plusWeeks(step)
            "monthly" -> monthlyDate(current.plusMonths(step).withDayOfMonth(1), rule.dayOfMonth ?: current.dayOfMonth, allowNext = false)
            "yearly" -> yearlyDate(current.plusYears(step).withDayOfYear(1), rule.monthOfYear ?: current.monthValue, rule.dayOfMonth ?: current.dayOfMonth, allowNext = false)
            else -> current.plusDays(step)
        }
    }

    private fun monthlyDate(anchor: LocalDate, requestedDay: Int, allowNext: Boolean): LocalDate {
        var month = anchor.withDayOfMonth(1)
        var candidate = month.withDayOfMonth(requestedDay.coerceIn(1, month.lengthOfMonth()))
        if (allowNext && candidate < anchor) {
            month = month.plusMonths(1)
            candidate = month.withDayOfMonth(requestedDay.coerceIn(1, month.lengthOfMonth()))
        }
        return candidate
    }

    private fun yearlyDate(anchor: LocalDate, requestedMonth: Int, requestedDay: Int, allowNext: Boolean): LocalDate {
        var year = anchor.year
        fun date(y: Int): LocalDate {
            val month = requestedMonth.coerceIn(1, 12)
            val first = LocalDate.of(y, month, 1)
            return first.withDayOfMonth(requestedDay.coerceIn(1, first.lengthOfMonth()))
        }
        var candidate = date(year)
        if (allowNext && candidate < anchor) candidate = date(++year)
        return candidate
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

    private fun outboxFor(entity: TagEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId).put("name", entity.name).put("color", entity.color ?: JSONObject.NULL)
            .put("sortOrder", entity.sortOrder).put("isArchived", entity.isArchived)
        return outbox("finance.tag", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
    }

    private fun outboxFor(entity: BudgetEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId).put("budgetType", entity.budgetType).put("categoryId", entity.categoryId ?: JSONObject.NULL)
            .put("amountCents", entity.amountCents).put("currency", entity.currency).put("period", entity.period)
            .put("startDay", entity.startDay).put("enabled", entity.enabled)
        return outbox("finance.budget", entity.id, entity.serverVersion ?: "0", entity.updatedAt, payload.toString())
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

    private fun outboxFor(entity: TransactionEntity): OutboxEntity {
        val payload = JSONObject().put("meta", meta(entity.id, entity.localProfileId, entity.createdAt, entity.updatedAt, entity.deletedAt, entity.localVersion, entity.serverVersion, entity.modifiedByDevice))
            .put("ledgerId", entity.ledgerId ?: JSONObject.NULL).put("transactionType", entity.transactionType)
            .put("amountCents", entity.amountCents).put("currency", entity.currency)
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

    private fun outbox(entityType: String, entityId: String, base: String, modifiedAt: String, payload: String, dependenciesJson: String = "[]") = OutboxEntity(
        changeId = UUID.randomUUID().toString(), entityType = entityType, entityId = entityId, operation = "upsert",
        baseServerVersion = base, clientModifiedAt = modifiedAt, payloadJson = payload, dependenciesJson = dependenciesJson,
    )

    private fun deleteOutbox(entityType: String, entityId: String, base: String, modifiedAt: String) = OutboxEntity(
        changeId = UUID.randomUUID().toString(), entityType = entityType, entityId = entityId, operation = "delete",
        baseServerVersion = base, clientModifiedAt = modifiedAt, payloadJson = null,
    )
}
