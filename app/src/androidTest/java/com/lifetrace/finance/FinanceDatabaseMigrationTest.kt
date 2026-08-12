package com.lifetrace.finance

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifetrace.finance.data.FinanceDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var databaseName: String? = null
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun cleanup() {
        helper?.close()
        databaseName?.let(context::deleteDatabase)
    }

    @Test
    fun migration1To2PreservesFinanceRowsAndBackfillsDefaultLedger() {
        databaseName = "finance-migration-${UUID.randomUUID()}.db"
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV1FinanceTables(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

        val db = requireNotNull(helper).writableDatabase
        val stamp = "2026-08-12T04:00:00Z"
        db.execSQL(
            "INSERT INTO local_profiles(id, cloud_user_id, display_name, created_at, updated_at) VALUES(?, NULL, ?, ?, ?)",
            arrayOf("profile-1", "Local", stamp, stamp),
        )
        db.execSQL(
            """INSERT INTO finance_accounts(
                id, local_profile_id, name, account_type, opening_balance_cents, balance_at, last4,
                color, icon, is_archived, currency, created_at, updated_at, deleted_at, local_version, server_version
            ) VALUES(?, ?, ?, ?, ?, NULL, NULL, ?, ?, 0, ?, ?, ?, NULL, 1, NULL)""".trimIndent(),
            arrayOf("account-1", "profile-1", "微信", "wechat", 10000L, "#4F6BED", "wallet", "CNY", stamp, stamp),
        )
        db.execSQL(
            """INSERT INTO finance_categories(
                id, local_profile_id, name, category_type, parent_id, icon, color, is_system, is_archived,
                created_at, updated_at, deleted_at, local_version, server_version
            ) VALUES(?, ?, ?, ?, NULL, NULL, NULL, 1, 0, ?, ?, NULL, 1, NULL)""".trimIndent(),
            arrayOf("category-1", "profile-1", "餐饮", "expense", stamp, stamp),
        )
        db.execSQL(
            """INSERT INTO finance_transactions(
                id, local_profile_id, transaction_type, amount_cents, currency, account_id, to_account_id,
                category_id, counterparty, merchant, item, note, occurred_at, local_date, status, source_type,
                external_transaction_id, created_at, updated_at, deleted_at, local_version, server_version, modified_by_device
            ) VALUES(?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?, NULL, NULL, ?, ?, ?, ?, NULL, ?, ?, NULL, 1, NULL, ? )""".trimIndent(),
            arrayOf(
                "transaction-1", "profile-1", "expense", 2590L, "CNY", "account-1", "category-1",
                "测试商户", stamp, "2026-08-12", "confirmed", "manual", stamp, stamp, "device-1",
            ),
        )

        FinanceDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT id, currency, ledger_type, month_start_day FROM finance_ledgers WHERE local_profile_id='profile-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("default-ledger-profile-1", cursor.getString(0))
            assertEquals("CNY", cursor.getString(1))
            assertEquals("personal", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertFalse(cursor.moveToNext())
        }

        assertLedgerBackfill(db, "finance_accounts", "account-1")
        assertLedgerBackfill(db, "finance_categories", "category-1")
        assertLedgerBackfill(db, "finance_transactions", "transaction-1")

        db.query("SELECT amount_cents, exclude_from_stats, exclude_from_budget FROM finance_transactions WHERE id='transaction-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2590L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }

        for (table in listOf(
            "finance_recurring_transactions",
            "finance_tags",
            "finance_transaction_tags",
            "finance_budgets",
            "finance_transaction_attachments",
        )) {
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private fun assertLedgerBackfill(db: SupportSQLiteDatabase, table: String, id: String) {
        db.query("SELECT id, ledger_id FROM $table WHERE id=?", arrayOf(id)).use { cursor ->
            assertTrue("$table row must survive migration", cursor.moveToFirst())
            assertEquals(id, cursor.getString(0))
            assertEquals("default-ledger-profile-1", cursor.getString(1))
        }
    }

    private fun createV1FinanceTables(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE local_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                cloud_user_id TEXT,
                display_name TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE finance_accounts (
                id TEXT NOT NULL PRIMARY KEY,
                local_profile_id TEXT NOT NULL,
                name TEXT NOT NULL,
                account_type TEXT NOT NULL,
                opening_balance_cents INTEGER,
                balance_at TEXT,
                last4 TEXT,
                color TEXT NOT NULL,
                icon TEXT NOT NULL,
                is_archived INTEGER NOT NULL,
                currency TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted_at TEXT,
                local_version INTEGER NOT NULL,
                server_version TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE finance_categories (
                id TEXT NOT NULL PRIMARY KEY,
                local_profile_id TEXT NOT NULL,
                name TEXT NOT NULL,
                category_type TEXT NOT NULL,
                parent_id TEXT,
                icon TEXT,
                color TEXT,
                is_system INTEGER NOT NULL,
                is_archived INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted_at TEXT,
                local_version INTEGER NOT NULL,
                server_version TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE finance_transactions (
                id TEXT NOT NULL PRIMARY KEY,
                local_profile_id TEXT NOT NULL,
                transaction_type TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                account_id TEXT,
                to_account_id TEXT,
                category_id TEXT,
                counterparty TEXT,
                merchant TEXT,
                item TEXT,
                note TEXT,
                occurred_at TEXT NOT NULL,
                local_date TEXT NOT NULL,
                status TEXT NOT NULL,
                source_type TEXT NOT NULL,
                external_transaction_id TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted_at TEXT,
                local_version INTEGER NOT NULL,
                server_version TEXT,
                modified_by_device TEXT
            )
        """.trimIndent())
    }
}
