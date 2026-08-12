package com.lifetrace.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.core.*
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.data.SnapshotProgressEntity
import com.lifetrace.finance.data.SnapshotStagingEntity
import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceRepositoryTest {
    private lateinit var db: FinanceDatabase
    private lateinit var repo: FinanceRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db, "test-device")
    }

    @After fun tearDown() = db.close()

    @Test fun localWriteAndOutboxAreCommittedTogether() = runBlocking {
        val profile = repo.ensureProfile()
        val initial = db.syncDao().pendingCount().first()
        val account = repo.accounts(profile.id).first().first()
        val category = repo.categories(profile.id).first().first { it.categoryType == "expense" }
        repo.createTransaction(profile.id, TransactionType.EXPENSE, 2580, account.id, categoryId = category.id, merchant = "Coffee", note = "morning")
        val transaction = repo.transactions(profile.id).first().single()
        assertEquals("morning", transaction.note)
        assertEquals(initial + 1, db.syncDao().pendingCount().first())
    }

    @Test fun duplicateNotificationIsStoredOnceWithoutRawTextAndCanClassify() = runBlocking {
        val profile = repo.ensureProfile()
        val candidate = requireNotNull(NotificationTransactionParser.parse(NotificationSample(
            packageName = SupportedPackages.WECHAT,
            postTimeMillis = 1_700_000_000_000L,
            title = "微信支付",
            text = "向咖啡店支付成功 ￥18.00",
            notificationKey = "same-key",
        )))
        val key = NotificationTransactionParser.dedupKey(candidate)
        val capturedId = repo.captureNotificationCandidate(profile.id, candidate, key)
        assertNotNull(capturedId)
        val transactionId = requireNotNull(capturedId)
        assertNull(repo.captureNotificationCandidate(profile.id, candidate, key))
        assertEquals(1, repo.inbox(profile.id).first().size)
        val capturedEvent = db.notificationDao().recent().first().single()
        assertEquals(SupportedPackages.WECHAT, capturedEvent.sourcePackage)
        assertEquals(transactionId, capturedEvent.transactionId)
        val category = repo.categories(profile.id).first().first { it.categoryType == "expense" }
        repo.confirmCandidate(transactionId, category.id)
        val confirmed = repo.transactions(profile.id).first().single()
        assertEquals("confirmed", confirmed.status)
        assertEquals(category.id, confirmed.categoryId)
    }

    @Test fun cloudBindingKeepsStableLocalProfileId() = runBlocking {
        val local = repo.ensureProfile()
        val bound = repo.activateCloudProfile("cloud-user-1", "Alice")
        assertEquals(local.id, bound.id)
        assertEquals("cloud-user-1", bound.cloudUserId)
        assertTrue(db.syncDao().state()!!.snapshotRequired)
    }

    @Test fun newImportedTransactionWithoutCategoryNeedsReview() = runBlocking {
        val profile = repo.ensureProfile()
        val account = repo.accounts(profile.id).first().first()
        val id = repo.createTransaction(
            profile.id,
            TransactionType.EXPENSE,
            3200,
            account.id,
            merchant = "星巴克咖啡",
            status = TransactionStatus.CONFIRMED,
            sourceType = "bill_import",
        )
        assertEquals(id, repo.inbox(profile.id).first().single().id)
        assertEquals("candidate", repo.transactions(profile.id).first().single().status)
        val food = repo.categories(profile.id).first().first { it.name == "餐饮" }
        repo.confirmCandidate(id, food.id)
        assertTrue(repo.inbox(profile.id).first().isEmpty())
    }

    @Test fun historicalConfirmedTransactionWithoutCategoryIsNotReclassified() = runBlocking {
        val profile = repo.ensureProfile()
        val account = repo.accounts(profile.id).first().first()
        repo.createTransaction(
            profile.id,
            TransactionType.EXPENSE,
            1800,
            account.id,
            categoryId = null,
            status = TransactionStatus.CONFIRMED,
            sourceType = "manual",
        )
        assertTrue(repo.inbox(profile.id).first().isEmpty())
    }

    @Test fun billImportMergesWithSingleMatchingManualTransactionAndKeepsManualFields() = runBlocking {
        val profile = repo.ensureProfile()
        val account = repo.accounts(profile.id).first().first()
        val category = repo.categories(profile.id).first().first { it.categoryType == "expense" }
        val manualTime = java.time.Instant.parse("2026-08-11T10:00:00Z")
        val manualId = repo.createTransaction(
            profile.id,
            TransactionType.EXPENSE,
            2880,
            account.id,
            categoryId = category.id,
            merchant = null,
            note = "手工备注",
            occurredAt = manualTime,
        )

        val importedId = repo.createTransaction(
            profile.id,
            TransactionType.EXPENSE,
            2880,
            account.id,
            merchant = "微信商户",
            sourceType = "wechat_bill_import",
            externalTransactionId = "WX-ORDER-1",
            occurredAt = manualTime.plusSeconds(5 * 60),
        )

        assertEquals(manualId, importedId)
        val merged = repo.transactions(profile.id).first().single()
        assertEquals(category.id, merged.categoryId)
        assertEquals("手工备注", merged.note)
        assertEquals("微信商户", merged.merchant)
        assertEquals("manual", merged.sourceType)
        assertEquals("WX-ORDER-1", merged.externalTransactionId)
        assertEquals(1, db.financeDao().evidenceForTransaction(manualId).size)

        val duplicateId = repo.createTransaction(
            profile.id,
            TransactionType.EXPENSE,
            2880,
            account.id,
            sourceType = "wechat_bill_import",
            externalTransactionId = "WX-ORDER-1",
            occurredAt = manualTime.plusSeconds(5 * 60),
        )
        assertEquals(manualId, duplicateId)
        assertEquals(1, repo.transactions(profile.id).first().size)
        assertEquals(1, db.financeDao().evidenceForTransaction(manualId).size)
    }

    @Test fun ambiguousManualMatchesAreNotMergedAutomatically() = runBlocking {
        val profile = repo.ensureProfile()
        val account = repo.accounts(profile.id).first().first()
        val time = java.time.Instant.parse("2026-08-11T12:00:00Z")
        repo.createTransaction(profile.id, TransactionType.EXPENSE, 1000, account.id, occurredAt = time.minusSeconds(60))
        repo.createTransaction(profile.id, TransactionType.EXPENSE, 1000, account.id, occurredAt = time.plusSeconds(60))

        val importedId = repo.createTransaction(
            profile.id,
            TransactionType.EXPENSE,
            1000,
            account.id,
            sourceType = "alipay_bill_import",
            externalTransactionId = "ALI-ORDER-1",
            occurredAt = time,
        )

        assertEquals(3, repo.transactions(profile.id).first().size)
        assertEquals(importedId, repo.inbox(profile.id).first().single().id)
    }

    @Test fun standardCategoriesAreSeededWithoutDuplicates() = runBlocking {
        val profile = repo.ensureProfile()
        repo.ensureStandardCategories(profile.id)
        repo.ensureStandardCategories(profile.id)
        val categories = repo.categories(profile.id).first()
        assertEquals(StandardCategories.ALL.size, categories.size)
        assertTrue(categories.any { it.name == "交通" && it.categoryType == "expense" })
        assertTrue(categories.any { it.name == "退款" && it.categoryType == "income" })
    }

    @Test fun snapshotProgressAndStagingAreDurableRoomState() = runBlocking {
        db.syncDao().stageSnapshot(SnapshotStagingEntity("finance.transaction", "tx-1", "7", "{\"meta\":{\"id\":\"tx-1\"}}"))
        db.syncDao().saveSnapshotProgress(SnapshotProgressEntity(snapshotId = "snap-1", nextPageToken = "p2", snapshotCursor = "42", updatedAt = "2026-08-08T00:00:00Z"))
        assertEquals("p2", db.syncDao().snapshotProgress()!!.nextPageToken)
        assertEquals("tx-1", db.syncDao().stagedSnapshot().single().entityId)
    }

    @Test fun archivedAccountIsHiddenButRetainedAndEnqueued() = runBlocking {
        val profile = repo.ensureProfile()
        val accountId = repo.createAccount(profile.id, "工资卡", "bank")
        val before = requireNotNull(db.financeDao().accountById(accountId))
        repo.archiveAccount(accountId)
        val archived = requireNotNull(db.financeDao().accountById(accountId))
        assertTrue(archived.isArchived)
        assertEquals(before.localVersion + 1, archived.localVersion)
        assertFalse(repo.accounts(profile.id).first().any { it.id == accountId })
        assertTrue(db.syncDao().pendingForEntity("finance.account", accountId) >= 1)
    }

    @Test fun archivedCategoryIsHiddenButRetainedAndEnqueued() = runBlocking {
        val profile = repo.ensureProfile()
        val categoryId = repo.createCategory(profile.id, "旅行", TransactionType.EXPENSE)
        val before = requireNotNull(db.financeDao().categoryById(categoryId))
        repo.archiveCategory(categoryId)
        val archived = requireNotNull(db.financeDao().categoryById(categoryId))
        assertTrue(archived.isArchived)
        assertEquals(before.localVersion + 1, archived.localVersion)
        assertFalse(repo.categories(profile.id).first().any { it.id == categoryId })
        assertTrue(db.syncDao().pendingForEntity("finance.category", categoryId) >= 1)
    }
}
