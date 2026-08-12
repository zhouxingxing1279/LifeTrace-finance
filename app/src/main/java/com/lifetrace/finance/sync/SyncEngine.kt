package com.lifetrace.finance.sync

import androidx.room.withTransaction
import com.lifetrace.finance.BuildConfig
import com.lifetrace.finance.auth.AuthManager
import com.lifetrace.finance.core.LifeTraceContract
import com.lifetrace.finance.core.SyncPolicy
import com.lifetrace.finance.data.*
import com.lifetrace.finance.network.ApiHttpException
import com.lifetrace.finance.network.LifeTraceApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class SyncEngine(
    private val db: FinanceDatabase,
    private val api: LifeTraceApi,
    private val auth: AuthManager,
    private val diagnostics: Diagnostics,
) {
    private val sync = db.syncDao()
    private val finance = db.financeDao()
    private val bookkeeping = db.bookkeepingDao()
    private val profiles = db.profileDao()
    private var dynamicPushBatchSize = 50
    private var pullBatchSize = 100

    suspend fun runOnce(): Result<Unit> = withContext(Dispatchers.IO) {
        val correlation = UUID.randomUUID().toString()
        runCatching {
            diagnostics.event("SYNC", "SYNC_START", "sync cycle started", correlationId = correlation)
            if (auth.accessToken() == null) return@runCatching
            loadCapabilities(correlation)
            if (sync.state()?.snapshotRequired == true || sync.snapshotProgress() != null) snapshotInternal(correlation)
            push(correlation)
            pull(correlation)
            diagnostics.event("SYNC", "SYNC_SUCCESS", "sync cycle completed", correlationId = correlation)
        }.onFailure {
            saveLastError(it)
            diagnostics.event("SYNC", "SYNC_FAILED", "${it.javaClass.simpleName}: ${it.message ?: "unknown"}", "ERROR", correlation)
        }
    }

    private suspend fun loadCapabilities(correlation: String) {
        runCatching { api.capabilities() }.onSuccess { value ->
            val protocol = value.optInt("protocolVersion", LifeTraceContract.PROTOCOL_VERSION)
            val minimumSchema = value.optInt("minimumSchemaVersion", LifeTraceContract.SCHEMA_VERSION)
            require(protocol == LifeTraceContract.PROTOCOL_VERSION) { "unsupported sync protocol $protocol" }
            require(LifeTraceContract.SCHEMA_VERSION >= minimumSchema) { "client schema is too old" }
            dynamicPushBatchSize = minOf(dynamicPushBatchSize, value.optInt("maximumPushBatchSize", dynamicPushBatchSize).coerceAtLeast(1))
            pullBatchSize = value.optInt("maximumPullBatchSize", pullBatchSize).coerceIn(1, 500)
        }.onFailure {
            diagnostics.event("SYNC", "CAPABILITIES_FAILED", it.message ?: "capabilities failed", "WARN", correlation)
        }
    }

    private suspend fun push(correlation: String) {
        while (true) {
            val batch = sync.pending(System.currentTimeMillis(), dynamicPushBatchSize)
            if (batch.isEmpty()) return
            val request = JSONObject()
                .put("requestId", UUID.randomUUID().toString())
                .put("client", clientInfo())
                .put("changes", JSONArray().apply {
                    batch.forEach { outbox ->
                        put(JSONObject()
                            .put("changeId", outbox.changeId)
                            .put("entityType", outbox.entityType)
                            .put("entityId", outbox.entityId)
                            .put("operation", outbox.operation)
                            .put("baseServerVersion", outbox.baseServerVersion)
                            .put("entitySchemaVersion", outbox.entitySchemaVersion)
                            .put("clientModifiedAt", outbox.clientModifiedAt)
                            .put("payload", outbox.payloadJson?.let(::JSONObject) ?: JSONObject.NULL)
                            .put("atomicGroupId", outbox.atomicGroupId ?: JSONObject.NULL)
                            .put("dependencies", JSONArray(outbox.dependenciesJson)))
                    }
                })
            try {
                diagnostics.event("SYNC_PUSH", "HTTP_REQUEST_START", "push batch size=${batch.size}", correlationId = correlation)
                val response = withAuthorizedToken { api.syncPush(it, request) }
                applyPushResults(batch, response)
                dynamicPushBatchSize = minOf(50, dynamicPushBatchSize + 5)
            } catch (error: ApiHttpException) {
                when (error.status) {
                    413 -> {
                        val next = SyncPolicy.nextBatchSize(dynamicPushBatchSize, 413)
                        if (dynamicPushBatchSize == 1 && batch.size == 1) throw error
                        dynamicPushBatchSize = next
                    }
                    429 -> {
                        val delay = SyncPolicy.retryDelayMillis(batch.first().attempts, error.retryAfterSeconds)
                        batch.forEach { sync.retry(it.changeId, System.currentTimeMillis() + delay, "HTTP 429") }
                        return
                    }
                    else -> {
                        val delay = SyncPolicy.retryDelayMillis(batch.first().attempts)
                        batch.forEach { sync.retry(it.changeId, System.currentTimeMillis() + delay, "HTTP ${error.status}") }
                        throw error
                    }
                }
            }
        }
    }

    private suspend fun applyPushResults(batch: List<OutboxEntity>, response: JSONObject) = db.withTransaction {
        val results = response.getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val changeId = result.getString("changeId")
            val original = batch.firstOrNull { it.changeId == changeId } ?: continue
            when (result.getString("status")) {
                "accepted", "duplicate" -> {
                    setServerVersion(original.entityType, original.entityId, result.getString("serverVersion"))
                    sync.ack(changeId)
                }
                "conflict" -> {
                    sync.saveConflict(ConflictEntity(
                        conflictId = result.getString("conflictId"),
                        changeId = changeId,
                        entityType = original.entityType,
                        entityId = original.entityId,
                        localPayload = original.payloadJson,
                        remotePayload = result.opt("serverEntity")?.takeUnless { it === JSONObject.NULL }?.toString(),
                        baseServerVersion = result.getString("clientBaseServerVersion"),
                        remoteServerVersion = result.getString("currentServerVersion"),
                        createdAt = Instant.now().toString(),
                    ))
                    sync.ack(changeId)
                }
                "rejected" -> sync.retry(changeId, System.currentTimeMillis() + 15 * 60_000L, result.optString("code", "rejected"))
            }
        }
        sync.saveState((sync.state() ?: SyncStateEntity()).copy(lastPushAt = Instant.now().toString(), lastError = null))
    }

    private suspend fun pull(correlation: String) {
        var cursor = sync.state()?.cursor
        var hasMore: Boolean
        do {
            val request = JSONObject()
                .put("requestId", UUID.randomUUID().toString())
                .put("client", clientInfo())
                .put("afterCursor", cursor ?: JSONObject.NULL)
                .put("limit", pullBatchSize)
                .put("entityTypes", JSONArray(LifeTraceContract.FINANCE_ENTITY_TYPES.toList()))
            diagnostics.event("SYNC_PULL", "HTTP_REQUEST_START", "pull page", correlationId = correlation)
            val response = try {
                withAuthorizedToken { api.syncPull(it, request) }
            } catch (error: ApiHttpException) {
                if (error.responseBody.contains("LIFETRACE_SNAPSHOT_REQUIRED")) {
                    sync.saveState((sync.state() ?: SyncStateEntity()).copy(snapshotRequired = true, lastError = "snapshot required"))
                    snapshotInternal(correlation)
                    return
                }
                throw error
            }
            val changes = response.getJSONArray("changes")
            db.withTransaction {
                for (i in 0 until changes.length()) applyServerChange(changes.getJSONObject(i))
                cursor = response.getString("nextCursor")
                sync.saveState((sync.state() ?: SyncStateEntity()).copy(
                    cursor = cursor, lastPullAt = Instant.now().toString(), lastError = null, snapshotRequired = false,
                ))
            }
            hasMore = response.getBoolean("hasMore")
        } while (hasMore)
    }

    suspend fun snapshot(): Result<Unit> = withContext(Dispatchers.IO) {
        val correlation = UUID.randomUUID().toString()
        runCatching { snapshotInternal(correlation) }.onFailure {
            saveLastError(it)
            diagnostics.event("SYNC_SNAPSHOT", "SNAPSHOT_FAILED", it.message ?: "snapshot failed", "ERROR", correlation)
        }
    }

    private suspend fun snapshotInternal(correlation: String) {
        require(auth.accessToken() != null) { "not authenticated" }
        var progress = sync.snapshotProgress()
        if (progress?.downloadComplete == true) {
            applyStagedSnapshot(progress.snapshotCursor, correlation)
            return
        }
        var snapshotId = progress?.snapshotId
        var pageToken = progress?.nextPageToken
        var snapshotCursor = progress?.snapshotCursor
        do {
            val request = JSONObject()
                .put("requestId", UUID.randomUUID().toString())
                .put("client", clientInfo())
                .put("snapshotId", snapshotId ?: JSONObject.NULL)
                .put("pageToken", pageToken ?: JSONObject.NULL)
                .put("entityTypes", JSONArray(LifeTraceContract.FINANCE_ENTITY_TYPES.toList()))
                .put("pageSize", 200)
            diagnostics.event("SYNC_SNAPSHOT", if (progress == null) "SNAPSHOT_PAGE_START" else "SNAPSHOT_PAGE_RESUME", "snapshot page request", correlationId = correlation)
            val response = withAuthorizedToken { api.snapshot(it, request) }
            val responseSnapshotId = response.getString("snapshotId")
            val responseCursor = response.getString("snapshotCursor")
            val nextToken = response.opt("nextPageToken")?.takeUnless { it === JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank)
            val items = response.getJSONArray("items")
            val now = Instant.now().toString()
            db.withTransaction {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val payload = item.getJSONObject("payload")
                    sync.stageSnapshot(SnapshotStagingEntity(
                        entityType = item.getString("entityType"),
                        entityId = payload.getJSONObject("meta").getString("id"),
                        serverVersion = item.getString("serverVersion"),
                        payloadJson = payload.toString(),
                    ))
                }
                sync.saveSnapshotProgress(SnapshotProgressEntity(
                    snapshotId = responseSnapshotId,
                    nextPageToken = nextToken,
                    snapshotCursor = responseCursor,
                    downloadComplete = nextToken == null,
                    updatedAt = now,
                ))
            }
            snapshotId = responseSnapshotId
            pageToken = nextToken
            snapshotCursor = responseCursor
            progress = sync.snapshotProgress()
        } while (pageToken != null)
        applyStagedSnapshot(requireNotNull(snapshotCursor), correlation)
    }

    private suspend fun applyStagedSnapshot(snapshotCursor: String, correlation: String) {
        val profileId = activeProfileId()
        val staged = sync.stagedSnapshot()
        var skippedLocalChanges = 0
        db.withTransaction {
            for (item in staged) {
                if (sync.pendingForEntity(item.entityType, item.entityId) > 0) {
                    skippedLocalChanges++
                    continue
                }
                RemoteMapper.upsert(finance, bookkeeping, item.entityType, JSONObject(item.payloadJson), item.serverVersion, profileId)
            }
            sync.saveState((sync.state() ?: SyncStateEntity()).copy(
                cursor = snapshotCursor, snapshotRequired = false, lastPullAt = Instant.now().toString(), lastError = null,
            ))
            sync.clearSnapshotStaging()
            sync.clearSnapshotProgress()
        }
        diagnostics.event("SYNC_SNAPSHOT", "SNAPSHOT_APPLIED", "snapshot applied items=${staged.size} skippedLocalPending=$skippedLocalChanges", correlationId = correlation)
    }

    suspend fun resolveConflictKeepLocal(conflictId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conflict = sync.conflict(conflictId) ?: return@runCatching
            val payload = conflict.localPayload ?: error("local conflict payload is unavailable")
            db.withTransaction {
                sync.enqueue(OutboxEntity(
                    changeId = UUID.randomUUID().toString(), entityType = conflict.entityType, entityId = conflict.entityId,
                    operation = "upsert", baseServerVersion = conflict.remoteServerVersion,
                    clientModifiedAt = Instant.now().toString(), payloadJson = payload,
                ))
                sync.markConflict(conflictId, "local_requeued")
            }
        }
    }

    suspend fun resolveConflictUseRemote(conflictId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conflict = sync.conflict(conflictId) ?: return@runCatching
            val profileId = activeProfileId()
            db.withTransaction {
                if (conflict.remotePayload == null) {
                    remoteDelete(conflict.entityType, conflict.entityId, Instant.now().toString(), conflict.remoteServerVersion)
                } else {
                    RemoteMapper.upsert(finance, bookkeeping, conflict.entityType, JSONObject(conflict.remotePayload), conflict.remoteServerVersion, profileId)
                }
                sync.markConflict(conflictId, "remote_applied")
            }
        }
    }

    private suspend fun applyServerChange(change: JSONObject) {
        val entityType = change.getString("entityType")
        val entityId = change.getString("entityId")
        val version = change.getString("serverVersion")
        if (change.getString("operation") == "delete") {
            val at = change.optJSONObject("tombstone")?.optString("deletedAt")?.takeIf(String::isNotBlank) ?: Instant.now().toString()
            remoteDelete(entityType, entityId, at, version)
        } else {
            RemoteMapper.upsert(finance, bookkeeping, entityType, change.getJSONObject("payload"), version, activeProfileId())
        }
    }

    private suspend fun remoteDelete(entityType: String, id: String, deletedAt: String, version: String) = when (entityType) {
        "finance.ledger" -> bookkeeping.remoteDeleteLedger(id, deletedAt, version)
        "finance.transaction" -> finance.remoteDeleteTransaction(id, deletedAt, version)
        "finance.account" -> finance.remoteDeleteAccount(id, deletedAt, version)
        "finance.category" -> finance.remoteDeleteCategory(id, deletedAt, version)
        "finance.recurring_transaction" -> bookkeeping.remoteDeleteRecurring(id, deletedAt, version)
        "finance.tag" -> bookkeeping.remoteDeleteTag(id, deletedAt, version)
        "finance.transaction_tag" -> bookkeeping.remoteDeleteTransactionTag(id, deletedAt, version)
        "finance.budget" -> bookkeeping.remoteDeleteBudget(id, deletedAt, version)
        "finance.transaction_attachment" -> bookkeeping.remoteDeleteAttachment(id, deletedAt, version)
        "finance.transaction_evidence" -> finance.remoteDeleteEvidence(id, deletedAt, version)
        else -> Unit
    }

    private suspend fun setServerVersion(entityType: String, id: String, version: String) = when (entityType) {
        "finance.ledger" -> bookkeeping.setLedgerServerVersion(id, version)
        "finance.transaction" -> finance.setTransactionServerVersion(id, version)
        "finance.account" -> finance.setAccountServerVersion(id, version)
        "finance.category" -> finance.setCategoryServerVersion(id, version)
        "finance.recurring_transaction" -> bookkeeping.setRecurringServerVersion(id, version)
        "finance.tag" -> bookkeeping.setTagServerVersion(id, version)
        "finance.transaction_tag" -> bookkeeping.setTransactionTagServerVersion(id, version)
        "finance.budget" -> bookkeeping.setBudgetServerVersion(id, version)
        "finance.transaction_attachment" -> bookkeeping.setAttachmentServerVersion(id, version)
        "finance.transaction_evidence" -> finance.setEvidenceServerVersion(id, version)
        else -> Unit
    }

    private suspend fun activeProfileId(): String = profiles.active()?.id ?: error("active local profile is missing")

    private suspend fun <T> withAuthorizedToken(block: (String) -> T): T {
        val initial = auth.accessToken() ?: error("not authenticated")
        return try { block(initial) } catch (error: ApiHttpException) {
            if (error.status != 401) throw error
            val refreshed = auth.refreshAfterUnauthorized() ?: throw error
            block(refreshed)
        }
    }

    private suspend fun saveLastError(error: Throwable) {
        sync.saveState((sync.state() ?: SyncStateEntity()).copy(lastError = error.message ?: error.javaClass.simpleName))
    }

    private fun clientInfo() = JSONObject()
        .put("appId", LifeTraceContract.APP_ID)
        .put("clientVersion", BuildConfig.VERSION_NAME)
        .put("platform", LifeTraceContract.PLATFORM)
        .put("protocolVersion", LifeTraceContract.PROTOCOL_VERSION)
        .put("schemaVersion", LifeTraceContract.SCHEMA_VERSION)
        .put("deviceId", auth.deviceId)
}

private object RemoteMapper {
    suspend fun upsert(
        dao: FinanceDao,
        bookkeeping: BookkeepingDao,
        entityType: String,
        payload: JSONObject,
        serverVersion: String,
        localProfileId: String,
    ) {
        val meta = payload.getJSONObject("meta")
        fun nullable(key: String): String? = payload.opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()
        fun nullableLong(key: String): Long? = payload.opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()?.toLongOrNull()
        fun nullableInt(key: String): Int? = payload.opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()?.toIntOrNull()
        fun nullableDouble(key: String): Double? = payload.opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()?.toDoubleOrNull()
        val createdAt = meta.getString("createdAt")
        val updatedAt = meta.getString("updatedAt")
        val deletedAt = nullableMeta(meta, "deletedAt")
        val localVersion = meta.optLong("localVersion", 1)
        val modifiedByDevice = nullableMeta(meta, "modifiedByDevice")
        val fallbackLedgerId = "default-ledger-$localProfileId"

        when (entityType) {
            "finance.ledger" -> bookkeeping.upsertLedger(LedgerEntity(
                id = meta.getString("id"), localProfileId = localProfileId, name = payload.getString("name"),
                currency = payload.optString("currency", "CNY"), ledgerType = payload.optString("ledgerType", "personal"),
                monthStartDay = payload.optInt("monthStartDay", 1), sortOrder = payload.optInt("sortOrder", 0),
                isArchived = payload.optBoolean("isArchived"), createdAt = createdAt, updatedAt = updatedAt,
                deletedAt = deletedAt, localVersion = localVersion, serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.transaction" -> dao.upsertTransaction(TransactionEntity(
                id = meta.getString("id"), localProfileId = localProfileId,
                ledgerId = nullable("ledgerId") ?: dao.transactionById(meta.getString("id"))?.ledgerId ?: fallbackLedgerId,
                transactionType = payload.getString("transactionType"), amountCents = payload.getLong("amountCents"),
                currency = payload.optString("currency", "CNY"), accountId = nullable("accountId"), toAccountId = nullable("toAccountId"),
                categoryId = nullable("categoryId"), counterparty = nullable("counterparty"), merchant = nullable("merchant"),
                item = nullable("item"), note = nullable("note"), occurredAt = payload.getString("occurredAt"),
                localDate = payload.getString("localDate"), status = payload.getString("status"), sourceType = payload.getString("sourceType"),
                externalTransactionId = nullable("externalTransactionId"), recurringTransactionId = nullable("recurringTransactionId"),
                excludeFromStats = payload.optBoolean("excludeFromStats", false), excludeFromBudget = payload.optBoolean("excludeFromBudget", false),
                nativeAmountCents = nullableLong("nativeAmountCents"), nativeCurrency = nullable("nativeCurrency"), exchangeRate = nullable("exchangeRate"),
                createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, localVersion = localVersion,
                serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.account" -> dao.upsertAccount(AccountEntity(
                id = meta.getString("id"), localProfileId = localProfileId,
                ledgerId = nullable("ledgerId") ?: dao.accountById(meta.getString("id"))?.ledgerId ?: fallbackLedgerId,
                name = payload.getString("name"), accountType = payload.getString("accountType"),
                openingBalanceCents = nullableLong("openingBalanceCents"), balanceAt = nullable("balanceAt"), last4 = nullable("last4"),
                color = payload.optString("color", "#4F6BED"), icon = payload.optString("icon", "wallet"),
                isArchived = payload.optBoolean("isArchived"), currency = payload.optString("currency", "CNY"),
                sortOrder = payload.optInt("sortOrder", 0), creditLimitCents = nullableLong("creditLimitCents"),
                billingDay = nullableInt("billingDay"), paymentDueDay = nullableInt("paymentDueDay"), bankName = nullable("bankName"),
                note = nullable("note"), isHidden = payload.optBoolean("isHidden", false), createdAt = createdAt, updatedAt = updatedAt,
                deletedAt = deletedAt, localVersion = localVersion, serverVersion = serverVersion,
            ))
            "finance.category" -> dao.upsertCategory(CategoryEntity(
                id = meta.getString("id"), localProfileId = localProfileId,
                ledgerId = nullable("ledgerId") ?: dao.categoryById(meta.getString("id"))?.ledgerId ?: fallbackLedgerId,
                name = payload.getString("name"), categoryType = payload.getString("categoryType"), parentId = nullable("parentId"),
                icon = nullable("icon"), color = nullable("color"), isSystem = payload.optBoolean("isSystem"),
                isArchived = payload.optBoolean("isArchived"), sortOrder = payload.optInt("sortOrder", 0),
                level = payload.optInt("level", 1), iconType = payload.optString("iconType", "material"),
                customIconFileId = nullable("customIconFileId"), createdAt = createdAt, updatedAt = updatedAt,
                deletedAt = deletedAt, localVersion = localVersion, serverVersion = serverVersion,
            ))
            "finance.recurring_transaction" -> bookkeeping.upsertRecurring(RecurringTransactionEntity(
                id = meta.getString("id"), localProfileId = localProfileId, ledgerId = payload.getString("ledgerId"),
                transactionType = payload.getString("transactionType"), amountCents = payload.getLong("amountCents"),
                currency = payload.optString("currency", "CNY"), categoryId = nullable("categoryId"), accountId = nullable("accountId"),
                toAccountId = nullable("toAccountId"), note = nullable("note"), frequency = payload.getString("frequency"),
                interval = payload.optInt("interval", 1), dayOfMonth = nullableInt("dayOfMonth"), dayOfWeek = nullableInt("dayOfWeek"),
                monthOfYear = nullableInt("monthOfYear"), startDate = payload.getString("startDate"), endDate = nullable("endDate"),
                lastGeneratedDate = nullable("lastGeneratedDate"), enabled = payload.optBoolean("enabled", true),
                createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, localVersion = localVersion,
                serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.tag" -> bookkeeping.upsertTag(TagEntity(
                id = meta.getString("id"), localProfileId = localProfileId, ledgerId = payload.getString("ledgerId"),
                name = payload.getString("name"), color = nullable("color"), sortOrder = payload.optInt("sortOrder", 0),
                isArchived = payload.optBoolean("isArchived"), createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
                localVersion = localVersion, serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.transaction_tag" -> bookkeeping.upsertTransactionTag(TransactionTagEntity(
                id = meta.getString("id"), localProfileId = localProfileId, transactionId = payload.getString("transactionId"),
                tagId = payload.getString("tagId"), createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
                localVersion = localVersion, serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.budget" -> bookkeeping.upsertBudget(BudgetEntity(
                id = meta.getString("id"), localProfileId = localProfileId, ledgerId = payload.getString("ledgerId"),
                budgetType = payload.optString("budgetType", "total"), categoryId = nullable("categoryId"),
                amountCents = payload.getLong("amountCents"), currency = payload.optString("currency", "CNY"),
                period = payload.optString("period", "monthly"), startDay = payload.optInt("startDay", 1), enabled = payload.optBoolean("enabled", true),
                createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, localVersion = localVersion,
                serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.transaction_attachment" -> bookkeeping.upsertAttachment(TransactionAttachmentEntity(
                id = meta.getString("id"), localProfileId = localProfileId, transactionId = payload.getString("transactionId"),
                fileName = payload.getString("fileName"), originalName = nullable("originalName"), fileSize = nullableLong("fileSize"),
                width = nullableInt("width"), height = nullableInt("height"), sortOrder = payload.optInt("sortOrder", 0),
                fileId = nullable("fileId"), sha256 = nullable("sha256"), createdAt = createdAt, updatedAt = updatedAt,
                deletedAt = deletedAt, localVersion = localVersion, serverVersion = serverVersion, modifiedByDevice = modifiedByDevice,
            ))
            "finance.transaction_evidence" -> dao.upsertEvidence(TransactionEvidenceEntity(
                id = meta.getString("id"), localProfileId = localProfileId, transactionId = payload.getString("transactionId"),
                sourceType = payload.getString("sourceType"), sourceId = nullable("sourceId"),
                externalTransactionId = nullable("externalTransactionId"), confidence = nullableDouble("confidence"),
                createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, localVersion = localVersion, serverVersion = serverVersion,
            ))
        }
    }

    private fun nullableMeta(meta: JSONObject, key: String): String? =
        meta.opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()
}
