package com.lifetrace.finance.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_profiles")
data class LocalProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "cloud_user_id") val cloudUserId: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String = "Local",
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(tableName = "active_profile")
data class ActiveProfileEntity(
    @PrimaryKey val slot: String = "default",
    @ColumnInfo(name = "profile_id") val profileId: String,
)

@Entity(tableName = "finance_accounts", indices = [Index("local_profile_id"), Index(value = ["local_profile_id", "name"])])
data class AccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    val name: String,
    @ColumnInfo(name = "account_type") val accountType: String,
    @ColumnInfo(name = "opening_balance_cents") val openingBalanceCents: Long? = null,
    @ColumnInfo(name = "balance_at") val balanceAt: String? = null,
    val last4: String? = null,
    val color: String = "#4F6BED",
    val icon: String = "wallet",
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    val currency: String = "CNY",
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
)

@Entity(tableName = "finance_categories", indices = [Index("local_profile_id"), Index("parent_id")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    val name: String,
    @ColumnInfo(name = "category_type") val categoryType: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
)

@Entity(
    tableName = "finance_transactions",
    indices = [
        Index(value = ["local_profile_id", "local_date"]),
        Index(value = ["local_profile_id", "occurred_at"]),
        Index(value = ["local_profile_id", "account_id", "occurred_at"]),
        Index(value = ["local_profile_id", "category_id", "local_date"]),
        Index(value = ["local_profile_id", "status", "occurred_at"]),
        Index(value = ["local_profile_id", "external_transaction_id"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val currency: String = "CNY",
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    val counterparty: String? = null,
    val merchant: String? = null,
    val item: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    val status: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "external_transaction_id") val externalTransactionId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Entity(tableName = "finance_transaction_evidence", indices = [Index("transaction_id"), Index("local_profile_id")])
data class TransactionEvidenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    @ColumnInfo(name = "external_transaction_id") val externalTransactionId: String? = null,
    val confidence: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
)

@Entity(tableName = "sync_outbox", indices = [Index("state"), Index("entity_type"), Index("entity_id")])
data class OutboxEntity(
    @PrimaryKey @ColumnInfo(name = "change_id") val changeId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @ColumnInfo(name = "base_server_version") val baseServerVersion: String,
    @ColumnInfo(name = "entity_schema_version") val entitySchemaVersion: Int = 1,
    @ColumnInfo(name = "client_modified_at") val clientModifiedAt: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String? = null,
    @ColumnInfo(name = "atomic_group_id") val atomicGroupId: String? = null,
    @ColumnInfo(name = "dependencies_json") val dependenciesJson: String = "[]",
    val state: String = "pending",
    val attempts: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: String = "default",
    val cursor: String? = null,
    @ColumnInfo(name = "last_push_at") val lastPushAt: String? = null,
    @ColumnInfo(name = "last_pull_at") val lastPullAt: String? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "snapshot_required") val snapshotRequired: Boolean = false,
)

@Entity(tableName = "sync_conflicts", indices = [Index("entity_type"), Index("entity_id")])
data class ConflictEntity(
    @PrimaryKey @ColumnInfo(name = "conflict_id") val conflictId: String,
    @ColumnInfo(name = "change_id") val changeId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "local_payload") val localPayload: String?,
    @ColumnInfo(name = "remote_payload") val remotePayload: String?,
    @ColumnInfo(name = "base_server_version") val baseServerVersion: String,
    @ColumnInfo(name = "remote_server_version") val remoteServerVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "resolution_state") val resolutionState: String = "pending",
)

@Entity(tableName = "snapshot_progress")
data class SnapshotProgressEntity(
    @PrimaryKey val id: String = "default",
    @ColumnInfo(name = "snapshot_id") val snapshotId: String,
    @ColumnInfo(name = "next_page_token") val nextPageToken: String? = null,
    @ColumnInfo(name = "snapshot_cursor") val snapshotCursor: String,
    @ColumnInfo(name = "download_complete") val downloadComplete: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(tableName = "snapshot_staging", primaryKeys = ["entity_type", "entity_id"])
data class SnapshotStagingEntity(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "server_version") val serverVersion: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
)

@Entity(tableName = "notification_events", indices = [Index(value = ["dedup_key"], unique = true), Index("captured_at")])
data class NotificationEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "dedup_key") val dedupKey: String,
    @ColumnInfo(name = "evidence_hash") val evidenceHash: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val merchant: String? = null,
    @ColumnInfo(name = "account_hint") val accountHint: String? = null,
    val confidence: Double,
    @ColumnInfo(name = "parser_id") val parserId: String,
    @ColumnInfo(name = "parser_version") val parserVersion: Int,
    @ColumnInfo(name = "captured_at") val capturedAt: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String? = null,
)

@Entity(tableName = "diagnostic_events", indices = [Index("timestamp")])
data class DiagnosticEventEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val level: String,
    val component: String,
    @ColumnInfo(name = "event_code") val eventCode: String,
    val message: String,
    @ColumnInfo(name = "correlation_id") val correlationId: String? = null,
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_transactions WHERE local_profile_id=:profileId AND deleted_at IS NULL ORDER BY occurred_at DESC")
    fun transactions(profileId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM finance_transactions WHERE local_profile_id=:profileId AND status IN ('candidate','provisional') AND deleted_at IS NULL ORDER BY occurred_at DESC")
    fun inbox(profileId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM finance_accounts WHERE local_profile_id=:profileId AND deleted_at IS NULL AND is_archived=0 ORDER BY name")
    fun accounts(profileId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM finance_categories WHERE local_profile_id=:profileId AND deleted_at IS NULL AND is_archived=0 ORDER BY name")
    fun categories(profileId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransaction(value: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAccount(value: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategory(value: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvidence(value: TransactionEvidenceEntity)

    @Query("SELECT * FROM finance_transactions WHERE id=:id LIMIT 1") suspend fun transactionById(id: String): TransactionEntity?
    @Query("SELECT * FROM finance_accounts WHERE id=:id LIMIT 1") suspend fun accountById(id: String): AccountEntity?
    @Query("SELECT * FROM finance_categories WHERE id=:id LIMIT 1") suspend fun categoryById(id: String): CategoryEntity?

    @Query("UPDATE finance_transactions SET server_version=:version WHERE id=:id") suspend fun setTransactionServerVersion(id: String, version: String)
    @Query("UPDATE finance_accounts SET server_version=:version WHERE id=:id") suspend fun setAccountServerVersion(id: String, version: String)
    @Query("UPDATE finance_categories SET server_version=:version WHERE id=:id") suspend fun setCategoryServerVersion(id: String, version: String)
    @Query("UPDATE finance_transaction_evidence SET server_version=:version WHERE id=:id") suspend fun setEvidenceServerVersion(id: String, version: String)

    @Query("UPDATE finance_transactions SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteTransaction(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_accounts SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteAccount(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_categories SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteCategory(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_transaction_evidence SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteEvidence(id: String, deletedAt: String, version: String)

    @Query("SELECT COALESCE(SUM(CASE WHEN transaction_type='expense' THEN amount_cents ELSE 0 END),0) FROM finance_transactions WHERE local_profile_id=:profileId AND local_date BETWEEN :from AND :to AND deleted_at IS NULL AND status='confirmed'")
    fun expenseTotal(profileId: String, from: String, to: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN transaction_type='income' THEN amount_cents ELSE 0 END),0) FROM finance_transactions WHERE local_profile_id=:profileId AND local_date BETWEEN :from AND :to AND deleted_at IS NULL AND status='confirmed'")
    fun incomeTotal(profileId: String, from: String, to: String): Flow<Long>
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(value: OutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE state='pending' AND next_attempt_at<=:now ORDER BY client_modified_at LIMIT :limit")
    suspend fun pending(now: Long, limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state='pending' AND entity_type=:entityType AND entity_id=:entityId")
    suspend fun pendingForEntity(entityType: String, entityId: String): Int

    @Query("DELETE FROM sync_outbox WHERE change_id=:changeId") suspend fun ack(changeId: String)

    @Query("UPDATE sync_outbox SET attempts=attempts+1, next_attempt_at=:nextAt, last_error=:error WHERE change_id=:changeId")
    suspend fun retry(changeId: String, nextAt: Long, error: String?)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state='pending'") fun pendingCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveState(value: SyncStateEntity)
    @Query("SELECT * FROM sync_state WHERE id='default' LIMIT 1") suspend fun state(): SyncStateEntity?
    @Query("SELECT * FROM sync_state WHERE id='default' LIMIT 1") fun stateFlow(): Flow<SyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveConflict(value: ConflictEntity)
    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE resolution_state='pending'") fun conflictCount(): Flow<Int>
    @Query("SELECT * FROM sync_conflicts WHERE resolution_state='pending' ORDER BY created_at DESC") fun conflicts(): Flow<List<ConflictEntity>>
    @Query("SELECT * FROM sync_conflicts WHERE conflict_id=:id LIMIT 1") suspend fun conflict(id: String): ConflictEntity?
    @Query("UPDATE sync_conflicts SET resolution_state=:state WHERE conflict_id=:id") suspend fun markConflict(id: String, state: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSnapshotProgress(value: SnapshotProgressEntity)
    @Query("SELECT * FROM snapshot_progress WHERE id='default' LIMIT 1") suspend fun snapshotProgress(): SnapshotProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun stageSnapshot(value: SnapshotStagingEntity)
    @Query("SELECT * FROM snapshot_staging ORDER BY entity_type, entity_id") suspend fun stagedSnapshot(): List<SnapshotStagingEntity>
    @Query("DELETE FROM snapshot_staging") suspend fun clearSnapshotStaging()
    @Query("DELETE FROM snapshot_progress") suspend fun clearSnapshotProgress()
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: LocalProfileEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setActive(value: ActiveProfileEntity)
    @Query("SELECT p.* FROM local_profiles p INNER JOIN active_profile a ON p.id=a.profile_id WHERE a.slot='default' LIMIT 1") suspend fun active(): LocalProfileEntity?
    @Query("SELECT * FROM local_profiles ORDER BY created_at LIMIT 1") suspend fun first(): LocalProfileEntity?
    @Query("SELECT * FROM local_profiles WHERE cloud_user_id IS NULL ORDER BY created_at LIMIT 1") suspend fun firstUnbound(): LocalProfileEntity?
    @Query("SELECT * FROM local_profiles WHERE cloud_user_id=:cloudUserId LIMIT 1") suspend fun byCloudUserId(cloudUserId: String): LocalProfileEntity?
    @Query("SELECT * FROM local_profiles WHERE id=:id LIMIT 1") suspend fun byId(id: String): LocalProfileEntity?
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: NotificationEventEntity): Long
    @Query("DELETE FROM notification_events WHERE captured_at < :cutoff") suspend fun deleteBefore(cutoff: String)
    @Query("DELETE FROM notification_events") suspend fun clearAll()
    @Query("SELECT captured_at FROM notification_events ORDER BY captured_at DESC LIMIT 1") fun latestCapture(): Flow<String?>
}

@Dao
interface DiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: DiagnosticEventEntity)
    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT :limit") fun recent(limit: Int = 200): Flow<List<DiagnosticEventEntity>>
    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY timestamp DESC LIMIT 1000)") suspend fun trim()
}

@Database(
    entities = [
        LocalProfileEntity::class,
        ActiveProfileEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionEvidenceEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        ConflictEntity::class,
        SnapshotProgressEntity::class,
        SnapshotStagingEntity::class,
        NotificationEventEntity::class,
        DiagnosticEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    abstract fun syncDao(): SyncDao
    abstract fun profileDao(): ProfileDao
    abstract fun notificationDao(): NotificationDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        @Volatile private var instance: FinanceDatabase? = null

        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "lifetrace-finance.db",
            ).fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
