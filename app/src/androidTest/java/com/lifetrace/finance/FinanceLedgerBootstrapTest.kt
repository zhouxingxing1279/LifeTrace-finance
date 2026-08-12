package com.lifetrace.finance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.data.LedgerEntity
import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceLedgerBootstrapTest {
    private lateinit var db: FinanceDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FinanceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun unsyncedMigrationLedgerIsEnqueuedExactlyOnce() = runBlocking {
        val stamp = "2026-08-12T04:00:00Z"
        val migratedLedger = LedgerEntity(
            id = "default-ledger-profile-1",
            localProfileId = "profile-1",
            name = "默认账本",
            createdAt = stamp,
            updatedAt = stamp,
            serverVersion = null,
            modifiedByDevice = null,
        )
        db.bookkeepingDao().upsertLedger(migratedLedger)

        val repository = FinanceRepository(db, "device-1")
        val first = repository.ensureDefaultLedger("profile-1")
        val second = repository.ensureDefaultLedger("profile-1")

        assertEquals(migratedLedger.id, first.id)
        assertEquals(first.id, second.id)
        assertNotNull(db.bookkeepingDao().ledgerById(migratedLedger.id))
        assertEquals(1, db.syncDao().pendingForEntity("finance.ledger", migratedLedger.id))
    }
}