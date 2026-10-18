// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.EntryEntity
import app.mizan.data.db.TxnEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Ensures Home's Room projections use actual ledger rows instead of screen-local sample data. */
@RunWith(AndroidJUnit4::class)
class HomeDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun recent_records_and_balance_are_backed_by_the_in_memory_ledger() =
        runBlocking {
            database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)))
            database.accountDao().upsert(AccountEntity("cash", "Cash", "CASH", "#000000", "payments", "EGP", "2026-10-01", true, false, 0))
            database.accountDao().upsertBuckets(listOf(AccountBucketEntity("cash", "EGP", 1_000_00)))
            database.ledgerDao().insertTransaction(
                TxnEntity("expense", "EXPENSE", 1L, "2026-10-18", null, null, "Lunch", "CLEARED", "CASH", createdAt = 1L, updatedAt = 1L),
            )
            database.ledgerDao().insertEntries(listOf(EntryEntity("entry", "expense", "cash", "EGP", -250_00, "1", -250_00)))

            assertEquals(
                listOf("expense"),
                database
                    .ledgerDao()
                    .observeRecent(5)
                    .first()
                    .map { it.id },
            )
            assertEquals(
                750_00,
                database
                    .ledgerDao()
                    .observeAccountBalances()
                    .first()
                    .single()
                    .let { it.openingMinor + it.entryMinor },
            )
        }

    @Test
    fun transaction_detail_returns_both_transfer_legs() =
        runBlocking {
            database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)))
            database.accountDao().upsert(AccountEntity("cash", "Cash", "CASH", "#000000", "payments", "EGP", "2026-10-01", true, false, 0))
            database.accountDao().upsert(AccountEntity("bank", "Bank", "BANK", "#000000", "payments", "EGP", "2026-10-01", true, false, 1))
            database.accountDao().upsertBuckets(listOf(AccountBucketEntity("cash", "EGP", 0), AccountBucketEntity("bank", "EGP", 0)))
            database.ledgerDao().insertTransaction(
                TxnEntity(
                    "transfer",
                    "TRANSFER",
                    1L,
                    "2026-10-18",
                    null,
                    null,
                    "",
                    "CLEARED",
                    "BANK_TRANSFER",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            database.ledgerDao().insertEntries(
                listOf(
                    EntryEntity("out", "transfer", "cash", "EGP", -500_00, "1", -500_00),
                    EntryEntity("in", "transfer", "bank", "EGP", 500_00, "1", 500_00),
                ),
            )

            val rows = database.ledgerDao().observeRowsForTransaction("transfer").first()
            assertEquals(listOf(-500_00L, 500_00L), rows.map { it.amountMinor })
            assertEquals(listOf("cash", "bank"), rows.map { it.accountId })
        }

    @Test
    fun split_income_persists_one_transaction_with_exact_positive_entry_and_base_totals() =
        runBlocking {
            database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)))
            listOf("essential", "fun", "invest").forEachIndexed { index, id ->
                database.accountDao().upsert(AccountEntity(id, id, "CASH", "#000000", "payments", "EGP", "2026-10-01", true, false, index))
            }
            database.accountDao().upsertBuckets(listOf("essential", "fun", "invest").map { AccountBucketEntity(it, "EGP", 0) })
            database.ledgerDao().insertTransaction(
                TxnEntity(
                    "income",
                    "INCOME",
                    1L,
                    "2026-10-18",
                    "salary",
                    null,
                    "",
                    "CLEARED",
                    "BANK_TRANSFER",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            database.ledgerDao().insertEntries(
                listOf(
                    EntryEntity("income-1", "income", "essential", "EGP", 125_800L, "1", 125_800L),
                    EntryEntity("income-2", "income", "fun", "EGP", 503_200L, "1", 503_200L),
                    EntryEntity("income-3", "income", "invest", "EGP", 629_000L, "1", 629_000L),
                ),
            )

            val rows = database.ledgerDao().observeRowsForTransaction("income").first()
            assertEquals(3, rows.size)
            assertEquals(1_258_000L, rows.sumOf { it.amountMinor })
            assertEquals(1_258_000L, rows.sumOf { it.baseMinor })
            assertEquals(1, rows.map { it.id }.distinct().size)
        }
}
