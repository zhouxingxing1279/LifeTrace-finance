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
    private val profiles = db.profileDao()
    private var dynamicPushBatchSize = 50
    private var pullBatchSize = 100

    suspend fun runOnce(): Result<Unit> = withContext(Dispatchers.IO) {
        val correlation = UUID.randomUUID().toString()
        runCatching {
            diagnostics.event("SYNC", "SYNC_START", "sync cycle started", correlationId = correlation)
            if (auth.accessToken() == null) return@runCatching
            loadCapabilities(correlation)
            if (sync.state()?.snapshotRequired == true) snapshotInternal(correlation)
            push(correlation)
            pull(correlation)
            diagnostics.event("SYNC", "SYNC_SUCCESS", "sync cycle completed", correlationId = correlation)
        }.onFailure {
            saveLastError(it)
            diagnostics.event(
                "SYNC", "SYNC_FAILED", "${it.javaClass.simpleName}: ${it.message ?: "unknown"}",
                "ERROR", correlation,
            )
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
                        put(
                            JSONObject()
                                .put("changeId", outbox.changeId)
                                .put("entityType", outbox.entityType)
                                .put("entityId", outbox.entityId)
                                .put("operation", outbox.operation)
                                .put("baseServerVersion", outbox.baseServerVersion)
                                .put("entitySchemaVersion", outbox.entitySchemaVersion)
                                .put("clientModifiedAt", outbox.clientModifiedAt)
                                .put("payload", outbox.payloadJson?.let(::JSONObject) ?: JSONObject.NULL)
                                .put("atomicGroupId", outbox.atomicGroupId ?: JSONObject.NULL)
                                .put("dependencies", JSONArray(outbox.dependenciesJson))
                        )
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
                    val version = result.getString("serverVersion")
                    setServerVersion(original.entityType, original.entityId, version)
                    sync.ack(changeId)
                }
                "conflict" -> {
                    sync.saveConflict(
                        ConflictEntity(
                            conflictId = result.getString("conflictId"),
                            changeId = changeId,
                            entityType = original.entityType,
                            entityId = original.entityId,
                            localPayload = original.payloadJson,
                            remotePayload = result.opt("serverEntity")?.takeUnless { it === JSONObject.NULL }?.toString(),
                            baseServerVersion = result.getString("clientBaseServerVersion"),
                            remoteServerVersion = result.getString("currentServerVersion"),
                            createdAt = Instant.now().toString(),
                        )
                    )
                    sync.ack(changeId)
                }
                "rejected" -> sync.retry(
                    changeId,
                    System.currentTimeMillis() + 15 * 60_000L,
                    result.optString("code", "rejected"),
                )
            }
        }
        val previous = sync.state() ?: SyncStateEntity()
        sync.saveState(previous.copy(lastPushAt = Instant.now().toString(), lastError = null))
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
                    val previous = sync.state() ?: SyncStateEntity()
                    sync.saveState(previous.copy(snapshotRequired = true, lastError = "snapshot required"))
                    snapshotInternal(correlation)
                    return
                }
                throw error
            }
            val changes = response.getJSONArray("changes")
            db.withTransaction {
                for (i in 0 until changes.length()) applyServerChange(changes.getJSONObject(i))
                cursor = response.getString("nextCursor")
                val previous = sync.state() ?: SyncStateEntity()
                sync.saveState(previous.copy(cursor = cursor, lastPullAt = Instant.now().toString(), lastError = null, snapshotRequired = false))
            }
            hasMore = response.getBoolean("hasMore")
        } while (hasMore)
    }

    suspend fun snapshot(): Result<Unit> = withContext(Dispatchers.IO) {
        val correlation = UUID.randomUUID().toString()
        runCatching { snapshotInternal(correlation) }
            .onFailure { saveLastError(it); diagnostics.event("SYNC_SNAPSHOT", "SNAPSHOT_FAILED", it.message ?: "snapshot failed", "ERROR", correlation) }
    }

    private suspend fun snapshotInternal(correlation: String) {
        require(auth.accessToken() != null) { "not authenticated" }
        var snapshotId: String? = null
        var pageToken: String? = null
        var snapshotCursor: String? = null
        do {
            val request = JSONObject()
                .put("requestId", UUID.randomUUID().toString())
                .put("client", clientInfo())
                .put("snapshotId", snapshotId ?: JSONObject.NULL)
                .put("pageToken", pageToken ?: JSONObject.NULL)
                .put("entityTypes", JSONArray(LifeTraceContract.FINANCE_ENTITY_TYPES.toList()))
                .put("pageSize", 200)
            diagnostics.event("SYNC_SNAPSHOT", "HTTP_REQUEST_START", "snapshot page", correlationId = correlation)
            val response = withAuthorizedToken { api.snapshot(it, request) }
            snapshotId = response.getString("snapshotId")
            snapshotCursor = response.getString("snapshotCursor")
            val items = response.getJSONArray("items")
            db.withTransaction {
                for (i in 0 until items.length()) applySnapshot(items.getJSONObject(i))
            }
            pageToken = response.opt("nextPageToken")
                ?.takeUnless { it === JSONObject.NULL }
                ?.toString()
                ?.takeIf(String::isNotBlank)
        } while (pageToken != null)
        val previous = sync.state() ?: SyncStateEntity()
        sync.saveState(previous.copy(cursor = snapshotCursor, snapshotRequired = false, lastPullAt = Instant.now().toString(), lastError = null))
    }

    /** Conflict resolution is explicit; there is no implicit last-write-wins. */
    suspend fun resolveConflictKeepLocal(conflictId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conflict = sync.conflict(conflictId) ?: return@runCatching
            val payload = conflict.localPayload ?: error("local conflict payload is unavailable")
            db.withTransaction {
                sync.enqueue(
                    OutboxEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = conflict.entityType,
                        entityId = conflict.entityId,
                        operation = "upsert",
                        baseServerVersion = conflict.remoteServerVersion,
                        clientModifiedAt = Instant.now().toString(),
                        payloadJson = payload,
                    )
                )
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
                    RemoteMapper.upsert(finance, conflict.entityType, JSONObject(conflict.remotePayload), conflict.remoteServerVersion, profileId)
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
            RemoteMapper.upsert(finance, entityType, change.getJSONObject("payload"), version, activeProfileId())
        }
    }

    private suspend fun applySnapshot(item: JSONObject) {
        RemoteMapper.upsert(
            finance,
            item.getString("entityType"),
            item.getJSONObject("payload"),
            item.getString("serverVersion"),
            activeProfileId(),
        )
    }

    private suspend fun remoteDelete(entityType: String, id: String, deletedAt: String, version: String) = when (entityType) {
        "finance.transaction" -> finance.remoteDeleteTransaction(id, deletedAt, version)
        "finance.account" -> finance.remoteDeleteAccount(id, deletedAt, version)
        "finance.category" -> finance.remoteDeleteCategory(id, deletedAt, version)
        "finance.transaction_evidence" -> finance.remoteDeleteEvidence(id, deletedAt, version)
        else -> Unit
    }

    private suspend fun setServerVersion(entityType: String, id: String, version: String) = when (entityType) {
        "finance.transaction" -> finance.setTransactionServerVersion(id, version)
        "finance.account" -> finance.setAccountServerVersion(id, version)
        "finance.category" -> finance.setCategoryServerVersion(id, version)
        "finance.transaction_evidence" -> finance.setEvidenceServerVersion(id, version)
        else -> Unit
    }

    private suspend fun activeProfileId(): String = profiles.active()?.id ?: error("active local profile is missing")

    private suspend fun <T> withAuthorizedToken(block: (String) -> T): T {
        val initial = auth.accessToken() ?: error("not authenticated")
        return try {
            block(initial)
        } catch (error: ApiHttpException) {
            if (error.status != 401) throw error
            val refreshed = auth.refreshAfterUnauthorized() ?: throw error
            block(refreshed)
        }
    }

    private suspend fun saveLastError(error: Throwable) {
        val previous = sync.state() ?: SyncStateEntity()
        sync.saveState(previous.copy(lastError = error.message ?: error.javaClass.simpleName))
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
        entityType: String,
        payload: JSONObject,
        serverVersion: String,
        localProfileId: String,
    ) {
        val meta = payload.getJSONObject("meta")
        fun nullable(key: String): String? = payload.opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()
        // Cloud ownership comes from the authenticated principal. Wire meta.userId
        // must never replace this device's stable LocalProfileId.
        val createdAt = meta.getString("createdAt")
        val updatedAt = meta.getString("updatedAt")
        val deletedAt = meta.opt("deletedAt")?.takeUnless { it === JSONObject.NULL }?.toString()
        when (entityType) {
            "finance.transaction" -> dao.upsertTransaction(
                TransactionEntity(
                    id = meta.getString("id"), localProfileId = localProfileId,
                    transactionType = payload.getString("transactionType"), amountCents = payload.getLong("amountCents"),
                    currency = payload.optString("currency", "CNY"), accountId = nullable("accountId"),
                    toAccountId = nullable("toAccountId"), categoryId = nullable("categoryId"), counterparty = nullable("counterparty"),
                    merchant = nullable("merchant"), item = nullable("item"), note = nullable("note"), occurredAt = payload.getString("occurredAt"),
                    localDate = payload.getString("localDate"), status = payload.getString("status"), sourceType = payload.getString("sourceType"),
                    externalTransactionId = nullable("externalTransactionId"), createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
                    localVersion = meta.optLong("localVersion", 1), serverVersion = serverVersion,
                    modifiedByDevice = meta.opt("modifiedByDevice")?.takeUnless { it === JSONObject.NULL }?.toString(),
                )
            )
            "finance.account" -> dao.upsertAccount(
                AccountEntity(
                    id = meta.getString("id"), localProfileId = localProfileId, name = payload.getString("name"), accountType = payload.getString("accountType"),
                    openingBalanceCents = payload.opt("openingBalanceCents")?.takeUnless { it === JSONObject.NULL }?.toString()?.toLongOrNull(),
                    balanceAt = nullable("balanceAt"), last4 = nullable("last4"), color = payload.optString("color", "#4F6BED"),
                    icon = payload.optString("icon", "wallet"), isArchived = payload.optBoolean("isArchived"), currency = payload.optString("currency", "CNY"),
                    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, localVersion = meta.optLong("localVersion", 1), serverVersion = serverVersion,
                )
            )
            "finance.category" -> dao.upsertCategory(
                CategoryEntity(
                    id = meta.getString("id"), localProfileId = localProfileId, name = payload.getString("name"), categoryType = payload.getString("categoryType"),
                    parentId = nullable("parentId"), icon = nullable("icon"), color = nullable("color"), isSystem = payload.optBoolean("isSystem"),
                    isArchived = payload.optBoolean("isArchived"), createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
                    localVersion = meta.optLong("localVersion", 1), serverVersion = serverVersion,
                )
            )
            "finance.transaction_evidence" -> dao.upsertEvidence(
                TransactionEvidenceEntity(
                    id = meta.getString("id"), localProfileId = localProfileId, transactionId = payload.getString("transactionId"),
                    sourceType = payload.getString("sourceType"), sourceId = nullable("sourceId"), externalTransactionId = nullable("externalTransactionId"),
                    confidence = payload.opt("confidence")?.takeUnless { it === JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, localVersion = meta.optLong("localVersion", 1), serverVersion = serverVersion,
                )
            )
        }
    }
}
