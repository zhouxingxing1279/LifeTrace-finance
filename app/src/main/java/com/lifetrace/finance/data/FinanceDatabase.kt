package com.lifetrace.finance.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(
    tableName = "finance_accounts",
    indices = [
        Index("local_profile_id"),
        Index("ledger_id"),
        Index(value = ["local_profile_id", "name"]),
    ],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String? = null,
    val name: String,
    @ColumnInfo(name = "account_type") val accountType: String,
    @ColumnInfo(name = "opening_balance_cents") val openingBalanceCents: Long? = null,
    @ColumnInfo(name = "balance_at") val balanceAt: String? = null,
    val last4: String? = null,
    val color: String = "#4F6BED",
    val icon: String = "wallet",
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    val currency: String = "CNY",
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "credit_limit_cents") val creditLimitCents: Long? = null,
    @ColumnInfo(name = "billing_day") val billingDay: Int? = null,
    @ColumnInfo(name = "payment_due_day") val paymentDueDay: Int? = null,
    @ColumnInfo(name = "bank_name") val bankName: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "is_hidden", defaultValue = "0") val isHidden: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
)

@Entity(
    tableName = "finance_categories",
    indices = [Index("local_profile_id"), Index("ledger_id"), Index("parent_id")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String? = null,
    val name: String,
    @ColumnInfo(name = "category_type") val categoryType: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "level", defaultValue = "1") val level: Int = 1,
    @ColumnInfo(name = "icon_type", defaultValue = "'material'") val iconType: String = "material",
    @ColumnInfo(name = "custom_icon_file_id") val customIconFileId: String? = null,
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
        Index(value = ["local_profile_id", "ledger_id", "local_date"]),
        Index(value = ["local_profile_id", "account_id", "occurred_at"]),
        Index(value = ["local_profile_id", "category_id", "local_date"]),
        Index(value = ["local_profile_id", "status", "occurred_at"]),
        Index(value = ["local_profile_id", "external_transaction_id"]),
        Index("recurring_transaction_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String? = null,
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
    @ColumnInfo(name = "recurring_transaction_id") val recurringTransactionId: String? = null,
    @ColumnInfo(name = "exclude_from_stats", defaultValue = "0") val excludeFromStats: Boolean = false,
    @ColumnInfo(name = "exclude_from_budget", defaultValue = "0") val excludeFromBudget: Boolean = false,
    @ColumnInfo(name = "native_amount_cents") val nativeAmountCents: Long? = null,
    @ColumnInfo(name = "native_currency") val nativeCurrency: String? = null,
    @ColumnInfo(name = "exchange_rate") val exchangeRate: String? = null,
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

    @Query("SELECT * FROM finance_transactions WHERE local_profile_id=:profileId AND deleted_at IS NULL AND status IN ('candidate','provisional') ORDER BY occurred_at DESC")
    fun inbox(profileId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM finance_accounts WHERE local_profile_id=:profileId AND deleted_at IS NULL AND is_archived=0 AND is_hidden=0 ORDER BY sort_order, name")
    fun accounts(profileId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM finance_categories WHERE local_profile_id=:profileId AND deleted_at IS NULL AND is_archived=0 ORDER BY sort_order, level, name")
    fun categories(profileId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM finance_categories WHERE local_profile_id=:profileId AND deleted_at IS NULL AND is_archived=0 ORDER BY sort_order, level, name")
    suspend fun categoryList(profileId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransaction(value: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAccount(value: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategory(value: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvidence(value: TransactionEvidenceEntity)

    @Query("SELECT * FROM finance_transactions WHERE id=:id LIMIT 1") suspend fun transactionById(id: String): TransactionEntity?
    @Query("SELECT * FROM finance_transactions WHERE local_profile_id=:profileId AND external_transaction_id=:externalTransactionId AND deleted_at IS NULL LIMIT 1")
    suspend fun transactionByExternalId(profileId: String, externalTransactionId: String): TransactionEntity?
    @Query("SELECT * FROM finance_transactions WHERE local_profile_id=:profileId AND transaction_type=:transactionType AND amount_cents=:amountCents AND source_type='manual' AND status='confirmed' AND deleted_at IS NULL AND occurred_at BETWEEN :fromOccurredAt AND :toOccurredAt ORDER BY occurred_at")
    suspend fun matchingManualTransactions(
        profileId: String,
        transactionType: String,
        amountCents: Long,
        fromOccurredAt: String,
        toOccurredAt: String,
    ): List<TransactionEntity>
    @Query("SELECT * FROM finance_accounts WHERE id=:id LIMIT 1") suspend fun accountById(id: String): AccountEntity?
    @Query("SELECT * FROM finance_accounts WHERE id=:id LIMIT 1") fun accountFlow(id: String): Flow<AccountEntity?>
    @Query("SELECT * FROM finance_transactions WHERE deleted_at IS NULL AND status='confirmed' AND (account_id=:accountId OR to_account_id=:accountId) ORDER BY occurred_at DESC")
    fun transactionsForAccount(accountId: String): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM finance_categories WHERE id=:id LIMIT 1") suspend fun categoryById(id: String): CategoryEntity?
    @Query("SELECT * FROM finance_transaction_evidence WHERE transaction_id=:transactionId AND deleted_at IS NULL ORDER BY created_at")
    suspend fun evidenceForTransaction(transactionId: String): List<TransactionEvidenceEntity>

    @Query("UPDATE finance_transactions SET server_version=:version WHERE id=:id") suspend fun setTransactionServerVersion(id: String, version: String)
    @Query("UPDATE finance_accounts SET server_version=:version WHERE id=:id") suspend fun setAccountServerVersion(id: String, version: String)
    @Query("UPDATE finance_categories SET server_version=:version WHERE id=:id") suspend fun setCategoryServerVersion(id: String, version: String)
    @Query("UPDATE finance_transaction_evidence SET server_version=:version WHERE id=:id") suspend fun setEvidenceServerVersion(id: String, version: String)

    @Query("UPDATE finance_transactions SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteTransaction(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_accounts SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteAccount(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_categories SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteCategory(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_transaction_evidence SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteEvidence(id: String, deletedAt: String, version: String)

    @Query("SELECT COALESCE(SUM(CASE WHEN transaction_type='expense' THEN COALESCE(native_amount_cents, amount_cents) ELSE 0 END),0) FROM finance_transactions WHERE local_profile_id=:profileId AND local_date BETWEEN :from AND :to AND deleted_at IS NULL AND status='confirmed' AND exclude_from_stats=0")
    fun expenseTotal(profileId: String, from: String, to: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN transaction_type='income' THEN COALESCE(native_amount_cents, amount_cents) ELSE 0 END),0) FROM finance_transactions WHERE local_profile_id=:profileId AND local_date BETWEEN :from AND :to AND deleted_at IS NULL AND status='confirmed' AND exclude_from_stats=0")
    fun incomeTotal(profileId: String, from: String, to: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(COALESCE(native_amount_cents, amount_cents)),0) FROM finance_transactions WHERE local_profile_id=:profileId AND ledger_id=:ledgerId AND category_id=:categoryId AND local_date BETWEEN :from AND :to AND transaction_type='expense' AND status='confirmed' AND deleted_at IS NULL AND exclude_from_budget=0")
    fun categoryBudgetUsage(profileId: String, ledgerId: String, categoryId: String, from: String, to: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(COALESCE(native_amount_cents, amount_cents)),0) FROM finance_transactions WHERE local_profile_id=:profileId AND ledger_id=:ledgerId AND local_date BETWEEN :from AND :to AND transaction_type='expense' AND status='confirmed' AND deleted_at IS NULL AND exclude_from_budget=0")
    fun totalBudgetUsage(profileId: String, ledgerId: String, from: String, to: String): Flow<Long>
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(value: OutboxEntity)
    @Query("SELECT * FROM sync_outbox WHERE state='pending' AND next_attempt_at<=:now ORDER BY client_modified_at LIMIT :limit") suspend fun pending(now: Long, limit: Int): List<OutboxEntity>
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state='pending' AND entity_type=:entityType AND entity_id=:entityId") suspend fun pendingForEntity(entityType: String, entityId: String): Int
    @Query("DELETE FROM sync_outbox WHERE change_id=:changeId") suspend fun ack(changeId: String)
    @Query("UPDATE sync_outbox SET attempts=attempts+1, next_attempt_at=:nextAt, last_error=:error WHERE change_id=:changeId") suspend fun retry(changeId: String, nextAt: Long, error: String?)
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
    @Query("SELECT * FROM notification_events ORDER BY captured_at DESC LIMIT :limit") fun recent(limit: Int = 200): Flow<List<NotificationEventEntity>>
}

@Dao
interface DiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: DiagnosticEventEntity)
    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT :limit") fun recent(limit: Int = 200): Flow<List<DiagnosticEventEntity>>
    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY timestamp DESC LIMIT 1000)") suspend fun trim()
    @Query("DELETE FROM diagnostic_events") suspend fun clearAll()
}

@Database(
    entities = [
        LocalProfileEntity::class,
        ActiveProfileEntity::class,
        LedgerEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringTransactionEntity::class,
        TagEntity::class,
        TransactionTagEntity::class,
        BudgetEntity::class,
        TransactionAttachmentEntity::class,
        TransactionEvidenceEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        ConflictEntity::class,
        SnapshotProgressEntity::class,
        SnapshotStagingEntity::class,
        NotificationEventEntity::class,
        DiagnosticEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    abstract fun bookkeepingDao(): BookkeepingDao
    abstract fun syncDao(): SyncDao
    abstract fun profileDao(): ProfileDao
    abstract fun notificationDao(): NotificationDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        @Volatile private var instance: FinanceDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN ledger_id TEXT")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN credit_limit_cents INTEGER")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN billing_day INTEGER")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN payment_due_day INTEGER")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN bank_name TEXT")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE finance_accounts ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE finance_categories ADD COLUMN ledger_id TEXT")
                db.execSQL("ALTER TABLE finance_categories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE finance_categories ADD COLUMN level INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE finance_categories ADD COLUMN icon_type TEXT NOT NULL DEFAULT 'material'")
                db.execSQL("ALTER TABLE finance_categories ADD COLUMN custom_icon_file_id TEXT")

                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN ledger_id TEXT")
                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN recurring_transaction_id TEXT")
                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN exclude_from_stats INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN exclude_from_budget INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN native_amount_cents INTEGER")
                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN native_currency TEXT")
                db.execSQL("ALTER TABLE finance_transactions ADD COLUMN exchange_rate TEXT")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_ledgers (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_profile_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        ledger_type TEXT NOT NULL,
                        month_start_day INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL,
                        is_archived INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        deleted_at TEXT,
                        local_version INTEGER NOT NULL,
                        server_version TEXT,
                        modified_by_device TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO finance_ledgers
                    (id, local_profile_id, name, currency, ledger_type, month_start_day, sort_order, is_archived, created_at, updated_at, deleted_at, local_version, server_version, modified_by_device)
                    SELECT 'default-ledger-' || id, id, '默认账本', 'CNY', 'personal', 1, 0, 0, created_at, updated_at, NULL, 1, NULL, NULL
                    FROM local_profiles
                """.trimIndent())
                db.execSQL("UPDATE finance_accounts SET ledger_id='default-ledger-' || local_profile_id WHERE ledger_id IS NULL")
                db.execSQL("UPDATE finance_categories SET ledger_id='default-ledger-' || local_profile_id WHERE ledger_id IS NULL")
                db.execSQL("UPDATE finance_transactions SET ledger_id='default-ledger-' || local_profile_id WHERE ledger_id IS NULL")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_recurring_transactions (
                        id TEXT NOT NULL PRIMARY KEY, local_profile_id TEXT NOT NULL, ledger_id TEXT NOT NULL,
                        transaction_type TEXT NOT NULL, amount_cents INTEGER NOT NULL, currency TEXT NOT NULL,
                        category_id TEXT, account_id TEXT, to_account_id TEXT, note TEXT, frequency TEXT NOT NULL,
                        interval INTEGER NOT NULL, day_of_month INTEGER, day_of_week INTEGER, month_of_year INTEGER,
                        start_date TEXT NOT NULL, end_date TEXT, last_generated_date TEXT, enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, local_version INTEGER NOT NULL,
                        server_version TEXT, modified_by_device TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_tags (
                        id TEXT NOT NULL PRIMARY KEY, local_profile_id TEXT NOT NULL, ledger_id TEXT NOT NULL,
                        name TEXT NOT NULL, color TEXT, sort_order INTEGER NOT NULL, is_archived INTEGER NOT NULL,
                        created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, local_version INTEGER NOT NULL,
                        server_version TEXT, modified_by_device TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_transaction_tags (
                        id TEXT NOT NULL PRIMARY KEY, local_profile_id TEXT NOT NULL, transaction_id TEXT NOT NULL,
                        tag_id TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT,
                        local_version INTEGER NOT NULL, server_version TEXT, modified_by_device TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_budgets (
                        id TEXT NOT NULL PRIMARY KEY, local_profile_id TEXT NOT NULL, ledger_id TEXT NOT NULL,
                        budget_type TEXT NOT NULL, category_id TEXT, amount_cents INTEGER NOT NULL, currency TEXT NOT NULL,
                        period TEXT NOT NULL, start_day INTEGER NOT NULL, enabled INTEGER NOT NULL, created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL, deleted_at TEXT, local_version INTEGER NOT NULL, server_version TEXT,
                        modified_by_device TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_transaction_attachments (
                        id TEXT NOT NULL PRIMARY KEY, local_profile_id TEXT NOT NULL, transaction_id TEXT NOT NULL,
                        file_name TEXT NOT NULL, original_name TEXT, file_size INTEGER, width INTEGER, height INTEGER,
                        sort_order INTEGER NOT NULL, file_id TEXT, sha256 TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                        deleted_at TEXT, local_version INTEGER NOT NULL, server_version TEXT, modified_by_device TEXT
                    )
                """.trimIndent())

                val indexes = listOf(
                    "CREATE INDEX IF NOT EXISTS index_finance_accounts_ledger_id ON finance_accounts(ledger_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_categories_ledger_id ON finance_categories(ledger_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transactions_local_profile_id_ledger_id_local_date ON finance_transactions(local_profile_id, ledger_id, local_date)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transactions_recurring_transaction_id ON finance_transactions(recurring_transaction_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_ledgers_local_profile_id ON finance_ledgers(local_profile_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_ledgers_local_profile_id_is_archived_sort_order ON finance_ledgers(local_profile_id, is_archived, sort_order)",
                    "CREATE INDEX IF NOT EXISTS index_finance_recurring_transactions_local_profile_id ON finance_recurring_transactions(local_profile_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_recurring_transactions_ledger_id ON finance_recurring_transactions(ledger_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_recurring_transactions_category_id ON finance_recurring_transactions(category_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_recurring_transactions_account_id ON finance_recurring_transactions(account_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_tags_local_profile_id ON finance_tags(local_profile_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_tags_ledger_id ON finance_tags(ledger_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_tags_ledger_id_name ON finance_tags(ledger_id, name)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transaction_tags_local_profile_id ON finance_transaction_tags(local_profile_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transaction_tags_transaction_id ON finance_transaction_tags(transaction_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transaction_tags_tag_id ON finance_transaction_tags(tag_id)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_finance_transaction_tags_transaction_id_tag_id ON finance_transaction_tags(transaction_id, tag_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_budgets_local_profile_id ON finance_budgets(local_profile_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_budgets_ledger_id ON finance_budgets(ledger_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_budgets_category_id ON finance_budgets(category_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transaction_attachments_local_profile_id ON finance_transaction_attachments(local_profile_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transaction_attachments_transaction_id ON finance_transaction_attachments(transaction_id)",
                    "CREATE INDEX IF NOT EXISTS index_finance_transaction_attachments_file_id ON finance_transaction_attachments(file_id)",
                )
                indexes.forEach(db::execSQL)
            }
        }

        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "lifetrace-finance.db",
            ).addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
