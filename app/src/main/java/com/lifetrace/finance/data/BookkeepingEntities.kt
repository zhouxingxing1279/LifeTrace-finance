package com.lifetrace.finance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * BeeCount-inspired bookkeeping entities, expressed in LifeTrace's stable string-ID
 * and integer-money conventions. They participate in the existing LifeTrace sync
 * outbox; no parallel cloud/sync model is introduced.
 */
@Entity(
    tableName = "finance_ledgers",
    indices = [Index("local_profile_id"), Index(value = ["local_profile_id", "is_archived", "sort_order"])],
)
data class LedgerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    val name: String,
    val currency: String = "CNY",
    @ColumnInfo(name = "ledger_type") val ledgerType: String = "personal",
    @ColumnInfo(name = "month_start_day") val monthStartDay: Int = 1,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Entity(
    tableName = "finance_recurring_transactions",
    indices = [Index("local_profile_id"), Index("ledger_id"), Index("category_id"), Index("account_id")],
)
data class RecurringTransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val currency: String = "CNY",
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    val note: String? = null,
    val frequency: String,
    val interval: Int = 1,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int? = null,
    @ColumnInfo(name = "month_of_year") val monthOfYear: Int? = null,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    @ColumnInfo(name = "last_generated_date") val lastGeneratedDate: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Entity(
    tableName = "finance_tags",
    indices = [Index("local_profile_id"), Index("ledger_id"), Index(value = ["ledger_id", "name"])],
)
data class TagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val name: String,
    val color: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Entity(
    tableName = "finance_transaction_tags",
    indices = [
        Index("local_profile_id"),
        Index("transaction_id"),
        Index("tag_id"),
        Index(value = ["transaction_id", "tag_id"], unique = true),
    ],
)
data class TransactionTagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Entity(
    tableName = "finance_budgets",
    indices = [Index("local_profile_id"), Index("ledger_id"), Index("category_id")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "budget_type") val budgetType: String = "total",
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val currency: String = "CNY",
    val period: String = "monthly",
    @ColumnInfo(name = "start_day") val startDay: Int = 1,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Entity(
    tableName = "finance_transaction_attachments",
    indices = [Index("local_profile_id"), Index("transaction_id"), Index("file_id")],
)
data class TransactionAttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_profile_id") val localProfileId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "original_name") val originalName: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "file_id") val fileId: String? = null,
    val sha256: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "local_version") val localVersion: Long = 1,
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "modified_by_device") val modifiedByDevice: String? = null,
)

@Dao
interface BookkeepingDao {
    @Query("SELECT * FROM finance_ledgers WHERE local_profile_id=:profileId AND deleted_at IS NULL AND is_archived=0 ORDER BY sort_order, name")
    fun ledgers(profileId: String): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM finance_ledgers WHERE id=:id LIMIT 1") suspend fun ledgerById(id: String): LedgerEntity?

    /**
     * Returns a ledger that is already known by the cloud or already has a
     * pending LifeTrace outbox change. A v1->v2 migration-created default
     * ledger has neither, so FinanceRepository.ensureDefaultLedger() will
     * deterministically upsert the same ID and enqueue it once.
     */
    @Query(
        """SELECT * FROM finance_ledgers l
           WHERE l.local_profile_id=:profileId AND l.deleted_at IS NULL
             AND (l.server_version IS NOT NULL OR EXISTS (
                 SELECT 1 FROM sync_outbox o
                 WHERE o.entity_type='finance.ledger' AND o.entity_id=l.id AND o.state='pending'
             ))
           ORDER BY l.sort_order, l.created_at LIMIT 1""",
    )
    suspend fun firstLedger(profileId: String): LedgerEntity?

    @Query("SELECT * FROM finance_tags WHERE local_profile_id=:profileId AND ledger_id=:ledgerId AND deleted_at IS NULL AND is_archived=0 ORDER BY sort_order, name")
    fun tags(profileId: String, ledgerId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM finance_budgets WHERE local_profile_id=:profileId AND ledger_id=:ledgerId AND deleted_at IS NULL AND enabled=1 ORDER BY budget_type, category_id")
    fun budgets(profileId: String, ledgerId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM finance_recurring_transactions WHERE local_profile_id=:profileId AND ledger_id=:ledgerId AND deleted_at IS NULL ORDER BY enabled DESC, start_date")
    fun recurringTransactions(profileId: String, ledgerId: String): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT t.* FROM finance_tags t INNER JOIN finance_transaction_tags r ON t.id=r.tag_id WHERE r.transaction_id=:transactionId AND r.deleted_at IS NULL AND t.deleted_at IS NULL ORDER BY t.sort_order, t.name")
    fun tagsForTransaction(transactionId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM finance_transaction_tags WHERE transaction_id=:transactionId AND tag_id=:tagId AND deleted_at IS NULL LIMIT 1")
    suspend fun transactionTag(transactionId: String, tagId: String): TransactionTagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLedger(value: LedgerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRecurring(value: RecurringTransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTag(value: TagEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransactionTag(value: TransactionTagEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(value: BudgetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttachment(value: TransactionAttachmentEntity)

    @Query("SELECT * FROM finance_recurring_transactions WHERE id=:id LIMIT 1") suspend fun recurringById(id: String): RecurringTransactionEntity?
    @Query("SELECT * FROM finance_tags WHERE id=:id LIMIT 1") suspend fun tagById(id: String): TagEntity?
    @Query("SELECT * FROM finance_transaction_tags WHERE id=:id LIMIT 1") suspend fun transactionTagById(id: String): TransactionTagEntity?
    @Query("SELECT * FROM finance_budgets WHERE id=:id LIMIT 1") suspend fun budgetById(id: String): BudgetEntity?
    @Query("SELECT * FROM finance_transaction_attachments WHERE id=:id LIMIT 1") suspend fun attachmentById(id: String): TransactionAttachmentEntity?

    @Query("UPDATE finance_ledgers SET server_version=:version WHERE id=:id") suspend fun setLedgerServerVersion(id: String, version: String)
    @Query("UPDATE finance_recurring_transactions SET server_version=:version WHERE id=:id") suspend fun setRecurringServerVersion(id: String, version: String)
    @Query("UPDATE finance_tags SET server_version=:version WHERE id=:id") suspend fun setTagServerVersion(id: String, version: String)
    @Query("UPDATE finance_transaction_tags SET server_version=:version WHERE id=:id") suspend fun setTransactionTagServerVersion(id: String, version: String)
    @Query("UPDATE finance_budgets SET server_version=:version WHERE id=:id") suspend fun setBudgetServerVersion(id: String, version: String)
    @Query("UPDATE finance_transaction_attachments SET server_version=:version WHERE id=:id") suspend fun setAttachmentServerVersion(id: String, version: String)

    @Query("UPDATE finance_ledgers SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteLedger(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_recurring_transactions SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteRecurring(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_tags SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteTag(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_transaction_tags SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteTransactionTag(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_budgets SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteBudget(id: String, deletedAt: String, version: String)
    @Query("UPDATE finance_transaction_attachments SET deleted_at=:deletedAt, server_version=:version WHERE id=:id") suspend fun remoteDeleteAttachment(id: String, deletedAt: String, version: String)
}