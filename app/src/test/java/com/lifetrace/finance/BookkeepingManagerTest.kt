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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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

    @Test
    fun categoryCanMoveUnderParentAndKeepsSyncHistory() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        val parentId = manager.createCategory(profile.id, ledger.id, "生活", TransactionType.EXPENSE)
        val childId = manager.createCategory(profile.id, ledger.id, "咖啡", TransactionType.EXPENSE)

        manager.updateCategory(childId, "咖啡茶饮", parentId)

        val updated = requireNotNull(db.financeDao().categoryById(childId))
        assertEquals("咖啡茶饮", updated.name)
        assertEquals(parentId, updated.parentId)
        assertEquals(2, updated.level)
        assertTrue(db.syncDao().pendingForEntity("finance.category", childId) >= 1)
    }

    @Test
    fun categoryIconAndSiblingOrderArePersisted() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        val parentId = manager.createCategory(profile.id, ledger.id, "饮食测试", TransactionType.EXPENSE)
        val breakfastId = manager.createCategory(profile.id, ledger.id, "早餐测试", TransactionType.EXPENSE, parentId, "restaurant")
        val dinnerId = manager.createCategory(profile.id, ledger.id, "晚餐测试", TransactionType.EXPENSE, parentId, "restaurant")

        manager.moveCategory(breakfastId, 1)

        val siblings = db.financeDao().categoryList(profile.id).filter { it.parentId == parentId }
        assertEquals(listOf(dinnerId, breakfastId), siblings.map { it.id })
        assertEquals("restaurant", db.financeDao().categoryById(breakfastId)?.icon)
        assertTrue(db.syncDao().pendingForEntity("finance.category", breakfastId) >= 1)
        assertTrue(db.syncDao().pendingForEntity("finance.category", dinnerId) >= 1)
    }

    @Test
    fun rejectsNonCnyLedgerAndAccount() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { manager.createLedger(profile.id, "美元账本", "USD") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { manager.createAccount(profile.id, ledger.id, "美元账户", "cash", "USD") } }
        Unit
    }

    @Test
    fun budgetCanBeEditedAndSoftDeleted() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        val category = repo.categories(profile.id).first().first { it.categoryType == "expense" }
        val id = manager.createBudget(profile.id, ledger.id, 10_000, null, "monthly", 1)

        manager.updateBudget(id, 20_000, category.id, "weekly", 7)
        val edited = requireNotNull(db.bookkeepingDao().budgetById(id))
        assertEquals(20_000, edited.amountCents)
        assertEquals(category.id, edited.categoryId)
        assertEquals("category", edited.budgetType)
        assertEquals("weekly", edited.period)

        manager.archiveBudget(id)
        assertNotNull(db.bookkeepingDao().budgetById(id)?.deletedAt)
        assertFalse(manager.budgets(profile.id, ledger.id).first().any { it.id == id })
        assertTrue(db.syncDao().pendingForEntity("finance.budget", id) >= 1)
    }

    @Test
    fun recurringRuleCanBeEditedAndDeletedWithoutDeletingGeneratedTransactions() = runBlocking {
        val profile = repo.ensureProfile()
        val ledger = repo.ensureDefaultLedger(profile.id)
        val account = repo.accounts(profile.id).first().first()
        val date = LocalDate.of(2026, 8, 12)
        val id = manager.createRecurring(profile.id, ledger.id, TransactionType.EXPENSE, 1000, account.id,
            frequency = "daily", startDate = date)
        assertEquals(1, manager.executeDueRecurring(profile.id, date))

        manager.updateRecurring(id, TransactionType.EXPENSE, 2500, account.id, null, null, "会员续费",
            "monthly", 1, date, 12, null, null, null)
        val edited = requireNotNull(db.bookkeepingDao().recurringById(id))
        assertEquals(2500, edited.amountCents)
        assertEquals("monthly", edited.frequency)
        assertEquals("会员续费", edited.note)

        manager.archiveRecurring(id)
        assertNotNull(db.bookkeepingDao().recurringById(id)?.deletedAt)
        assertFalse(manager.recurring(profile.id, ledger.id).first().any { it.id == id })
        assertEquals(1, repo.transactions(profile.id).first().count { it.recurringTransactionId == id })
        assertTrue(db.syncDao().pendingForEntity("finance.recurring_transaction", id) >= 1)
    }
}
