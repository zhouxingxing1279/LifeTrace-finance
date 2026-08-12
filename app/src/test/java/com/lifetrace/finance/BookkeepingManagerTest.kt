package com.lifetrace.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.BudgetEntity
import com.lifetrace.finance.data.FinanceDatabase
import com.lifetrace.finance.domain.BookkeepingManager
import com.lifetrace.finance.domain.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookkeepingManagerTest {
    private lateinit var db: FinanceDatabase
    private lateinit var repo: FinanceRepository
    private lateinit var manager: BookkeepingManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db, "test-device")
        manager = BookkeepingManager(db, repo, "test-device")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun recurringOccurrenceIsGeneratedExactlyOncePerDate() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        val account = repo.accounts(profile.id).first().first()
        val category = repo.categories(profile.id).first().first { it.categoryType == "expense" }
        val date = LocalDate.of(2026, 8, 12)

        val ruleId = manager.createRecurring(
            profileId = profile.id,
            ledgerId = ledger.id,
            type = TransactionType.EXPENSE,
            amountCents = 1990,
            accountId = account.id,
            categoryId = category.id,
            note = "会员",
            frequency = "daily",
            startDate = date,
        )

        assertEquals(1, manager.executeDueRecurring(profile.id, date))
        assertEquals(0, manager.executeDueRecurring(profile.id, date))

        val generated = repo.transactions(profile.id).first().single()
        assertEquals("recurring:$ruleId:$date", generated.externalTransactionId)
        assertEquals(ruleId, generated.recurringTransactionId)
        assertEquals("recurring", generated.sourceType)
        assertEquals(date.toString(), generated.localDate)
    }

    @Test
    fun monthlyBudgetPeriodHonorsStartDay() {
        val budget = BudgetEntity(
            id = "b1",
            localProfileId = "p1",
            ledgerId = "l1",
            amountCents = 100_00,
            period = "monthly",
            startDay = 15,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
        )

        val (from, to) = manager.budgetPeriod(budget, LocalDate.of(2026, 8, 12))

        assertEquals(LocalDate.of(2026, 7, 15), from)
        assertEquals(LocalDate.of(2026, 8, 14), to)
    }
}
