package com.lifetrace.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentFileStoreTest {
    private lateinit var db: FinanceDatabase
    private lateinit var repo: FinanceRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db, "test-device")
    }

    @After fun tearDown() = db.close()

    @Test fun tagAndAttachmentChangesEnterOutbox() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        val account = repo.accounts(profile.id).first().first()
        val tx = repo.createTransaction(profile.id, TransactionType.EXPENSE, 1200, account.id, ledgerId = ledger.id)
        val tag = repo.createTag(profile.id, ledger.id, "出差", "#4F9DDE")

        repo.updateTag(tag, "差旅", "#55A86B")
        val attachment = repo.createAttachment(profile.id, tx, "receipt.jpg", "小票.jpg", 120, 200, 300, "abc")

        assertEquals("差旅", db.bookkeepingDao().tagById(tag)?.name)
        assertEquals(1, repo.attachmentsForTransaction(tx).first().size)
        assertTrue(db.syncDao().pendingForEntity("finance.transaction_attachment", attachment) >= 1)

        repo.deleteAttachment(attachment)
        assertNotNull(db.bookkeepingDao().attachmentById(attachment)?.deletedAt)
        assertTrue(repo.attachmentsForTransaction(tx).first().isEmpty())
    }
}
