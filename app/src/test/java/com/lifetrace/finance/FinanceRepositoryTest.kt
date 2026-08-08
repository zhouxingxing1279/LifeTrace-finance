package com.lifetrace.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.core.*
import com.lifetrace.finance.data.FinanceDatabase
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

    @Test fun duplicateNotificationIsStoredOnceWithoutRawText() = runBlocking {
        val profile = repo.ensureProfile()
        val candidate = requireNotNull(NotificationTransactionParser.parse(NotificationSample(
            packageName = SupportedPackages.WECHAT,
            postTimeMillis = 1_700_000_000_000L,
            title = "微信支付",
            text = "向咖啡店支付成功 ￥18.00",
            notificationKey = "same-key",
        )))
        val key = NotificationTransactionParser.dedupKey(candidate)
        assertNotNull(repo.captureNotificationCandidate(profile.id, candidate, key))
        assertNull(repo.captureNotificationCandidate(profile.id, candidate, key))
        assertEquals(1, repo.inbox(profile.id).first().size)
    }

    @Test fun cloudBindingKeepsStableLocalProfileId() = runBlocking {
        val local = repo.ensureProfile()
        val bound = repo.activateCloudProfile("cloud-user-1", "Alice")
        assertEquals(local.id, bound.id)
        assertEquals("cloud-user-1", bound.cloudUserId)
        assertTrue(db.syncDao().state()!!.snapshotRequired)
    }
}
