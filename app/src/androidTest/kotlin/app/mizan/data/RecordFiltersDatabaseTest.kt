// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.EntryEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.db.PagedRecordRow
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.RecordLabelEntity
import app.mizan.data.db.TxnEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ports.GoldPriceProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class RecordFiltersDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun sql_count_matches_each_records_filter_individually_and_in_combination() =
        runBlocking {
            seedFixtures()

            assertCount(5)
            assertCount(3, accountId = "cash")
            assertCount(5, accountIds = "cash|bank")
            assertCount(2, categoryId = "food")
            assertCount(1, labelId = "essential")
            assertCount(2, kind = "EXPENSE")
            assertCount(1, kind = "INVESTMENT")
            assertCount(1, kind = "DEBT")
            assertCount(1, status = "UNCLEARED")
            assertCount(1, currency = "USD")
            assertCount(2, start = "2026-10-03", end = "2026-10-04")
            assertCount(2, minimum = 20_000, maximum = 50_000)
            assertCount(1, search = "Lunch")
            assertCount(1, search = "Roma")
            assertCount(
                1,
                accountId = "cash",
                categoryId = "food",
                kind = "EXPENSE",
                status = "UNCLEARED",
                currency = "EGP",
                minimum = 20_000,
                maximum = 40_000,
                search = "Lunch",
            )

            assertIds(setOf("lunch", "groceries", "salary", "buy", "debt"))
            assertIds(setOf("lunch", "salary", "buy"), accountId = "cash")
            assertIds(setOf("lunch", "groceries", "salary", "buy", "debt"), accountIds = "cash|bank")
            assertIds(setOf("lunch", "groceries"), categoryId = "food")
            assertIds(setOf("lunch"), labelId = "essential")
            assertIds(setOf("lunch", "groceries"), kind = "EXPENSE")
            assertIds(setOf("buy"), kind = "INVESTMENT")
            assertIds(setOf("debt"), kind = "DEBT")
            assertIds(setOf("lunch"), status = "UNCLEARED")
            assertIds(setOf("groceries"), currency = "USD")
            assertIds(setOf("groceries", "salary"), start = "2026-10-03", end = "2026-10-04")
            assertIds(setOf("lunch", "salary"), minimum = 20_000, maximum = 50_000)
            assertIds(setOf("lunch"), search = "Lunch")
            assertIds(setOf("lunch"), search = "Roma")
            assertIds(
                setOf("lunch"),
                accountId = "cash",
                categoryId = "food",
                kind = "EXPENSE",
                status = "UNCLEARED",
                currency = "EGP",
                minimum = 20_000,
                maximum = 40_000,
                search = "Lunch",
            )
        }

    @Test
    fun income_and_two_leg_transfer_filters_return_exact_transactions_once() =
        runBlocking {
            seedFixtures()
            database.ledgerDao().insertTransaction(
                TxnEntity(
                    "split-income",
                    "INCOME",
                    3L,
                    "2026-10-07",
                    "salary",
                    null,
                    "Split payday",
                    "CLEARED",
                    "CASH",
                    createdAt = 3L,
                    updatedAt = 3L,
                ),
            )
            database.ledgerDao().insertEntries(
                listOf(
                    EntryEntity("split-essential", "split-income", "cash", "EGP", 10_000, "1", 10_000),
                    EntryEntity("split-investment", "split-income", "bank", "EGP", 40_000, "1", 40_000),
                ),
            )
            database.ledgerDao().insertTransaction(
                TxnEntity(
                    "transfer",
                    "TRANSFER",
                    2L,
                    "2026-10-07",
                    null,
                    null,
                    "Exchange",
                    "CLEARED",
                    "BANK_TRANSFER",
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            )
            database.ledgerDao().insertEntries(
                listOf(
                    EntryEntity("transfer-out", "transfer", "cash", "EGP", -10_000, "1", -10_000),
                    EntryEntity("transfer-in", "transfer", "bank", "USD", 200, "50", 10_000),
                ),
            )

            assertCount(2, kind = "INCOME")
            assertIds(setOf("salary", "split-income"), kind = "INCOME")
            val splitIncome = records(kind = "INCOME").single { it.id == "split-income" }
            assertEquals(50_000, splitIncome.amountMinor)
            assertEquals(2, splitIncome.allocationData.split(',').size)
            assertCount(1, kind = "TRANSFER")
            assertIds(setOf("transfer"), kind = "TRANSFER")
            assertIds(setOf("transfer"), kind = "TRANSFER", accountId = "cash")
            assertIds(setOf("transfer"), kind = "TRANSFER", accountId = "bank")
            assertCount(1, kind = "TRANSFER", accountIds = "cash|bank")
            val page = records(kind = "TRANSFER", accountId = "cash")
            val transferRow = page.single()
            assertEquals(-10_000, transferRow.amountMinor)
            assertEquals("bank", transferRow.counterAccountName)
            assertEquals("USD", transferRow.counterCurrency)
            assertEquals(200L, transferRow.counterAmountMinor)
        }

    @Test
    fun soft_delete_undo_duplicate_and_attachments_preserve_record_rows() =
        runBlocking {
            seedFixtures()
            val repository = testRepository()
            val originalCount =
                database
                    .ledgerDao()
                    .observeRecordCount(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ).first()

            repository.deleteRecord("lunch")
            assertIds(setOf("groceries", "salary", "buy", "debt"))
            repository.restoreRecord("lunch")
            assertIds(setOf("lunch", "groceries", "salary", "buy", "debt"))

            repository.addRecordAttachments("lunch", listOf("content://receipt/one", "content://receipt/two"))
            assertEquals(2, database.ledgerDao().attachmentsForTransaction("lunch").size)
            val duplicateId = repository.duplicateRecord("lunch")
            assertNotNull(duplicateId)
            val duplicate = duplicateId!!
            assertEquals(1, database.ledgerDao().entriesForTransaction(duplicate).size)
            assertEquals(1, database.ledgerDao().labelsForTransaction(duplicate).size)
            assertEquals(2, database.ledgerDao().attachmentsForTransaction(duplicate).size)
            assertEquals(
                originalCount + 1,
                database.ledgerDao().observeRecordCount(null, null, null, null, null, null, null, null, null, null, null, null).first(),
            )
        }

    private suspend fun seedFixtures() {
        database.currencyDao().upsertAll(
            listOf(
                CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true),
                CurrencyEntity("USD", "US dollar", "$", 2, true),
            ),
        )
        listOf("cash", "bank").forEachIndexed { index, id ->
            database.accountDao().upsert(AccountEntity(id, id, "CASH", "#000000", "payments", "EGP", "2026-01-01", true, false, index))
        }
        database.referenceDataDao().upsertCategory(
            CategoryEntity("food", name = "Food", icon = "restaurant", color = "#111111", kind = "EXPENSE"),
        )
        database.referenceDataDao().upsertCategory(
            CategoryEntity("salary", name = "Salary", icon = "payments", color = "#111111", kind = "INCOME"),
        )
        database.referenceDataDao().upsertLabel(LabelEntity("essential", "Essential", "#ffffff"))
        database.referenceDataDao().upsertPayee(PayeeEntity("cafe", "Cafe Roma"))

        addRecord("lunch", "EXPENSE", "cash", "EGP", -30_000, "2026-10-02", "Lunch", "UNCLEARED", "food", "cafe")
        addRecord("groceries", "EXPENSE", "bank", "USD", -10_000, "2026-10-03", "Market", "CLEARED", "food", null)
        addRecord("salary", "INCOME", "cash", "EGP", 50_000, "2026-10-04", "Payday", "CLEARED", "salary", null)
        addRecord("buy", "ASSET_BUY", "cash", "EGP", -5_000, "2026-10-05", "Gold", "CLEARED", null, null)
        addRecord("debt", "DEBT_LEND", "bank", "EGP", -8_000, "2026-10-06", "Loan to Sam", "CLEARED", null, null)
        database.ledgerDao().insertLabels(listOf(RecordLabelEntity("lunch", "essential")))
    }

    private fun testRepository(): MizanRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return MizanRepository(
            database.currencyDao(),
            database,
            database.fxRateDao(),
            database.accountDao(),
            database.referenceDataDao(),
            database.incomeSetupDao(),
            database.inboxDao(),
            database.planningDao(),
            database.ledgerDao(),
            database.assetDao(),
            object : GoldPriceProvider {
                override suspend fun latest() = null
            },
            context,
            OkHttpClient(),
            Clock.systemUTC(),
        )
    }

    private suspend fun addRecord(
        id: String,
        kind: String,
        account: String,
        currency: String,
        amount: Long,
        date: String,
        note: String,
        status: String,
        category: String?,
        payee: String?,
    ) {
        database.ledgerDao().insertTransaction(
            TxnEntity(id, kind, 1L, date, category, payee, note, status, "CASH", createdAt = 1L, updatedAt = 1L),
        )
        database.ledgerDao().insertEntries(listOf(EntryEntity("$id-entry", id, account, currency, amount, "1", amount)))
    }

    private suspend fun assertIds(
        expected: Set<String>,
        accountId: String? = null,
        accountIds: String? = null,
        categoryId: String? = null,
        labelId: String? = null,
        kind: String? = null,
        status: String? = null,
        currency: String? = null,
        start: String? = null,
        end: String? = null,
        minimum: Long? = null,
        maximum: Long? = null,
        search: String? = null,
    ) {
        val actual =
            records(accountId, accountIds, categoryId, labelId, kind, status, currency, start, end, minimum, maximum, search)
                .map(PagedRecordRow::id)
                .toSet()
        assertEquals(expected, actual)
        assertEquals(
            actual.size,
            count(accountId, accountIds, categoryId, labelId, kind, status, currency, start, end, minimum, maximum, search),
        )
    }

    private suspend fun records(
        accountId: String? = null,
        accountIds: String? = null,
        categoryId: String? = null,
        labelId: String? = null,
        kind: String? = null,
        status: String? = null,
        currency: String? = null,
        start: String? = null,
        end: String? = null,
        minimum: Long? = null,
        maximum: Long? = null,
        search: String? = null,
    ): List<PagedRecordRow> {
        val result =
            database
                .ledgerDao()
                .records(accountId, accountIds, categoryId, labelId, kind, status, currency, start, end, minimum, maximum, search)
                .load(PagingSource.LoadParams.Refresh(0, 100, false))
        assertTrue("Expected a page, got $result", result is PagingSource.LoadResult.Page)
        return (result as PagingSource.LoadResult.Page).data
    }

    private suspend fun assertCount(
        expected: Int,
        accountId: String? = null,
        accountIds: String? = null,
        categoryId: String? = null,
        labelId: String? = null,
        kind: String? = null,
        status: String? = null,
        currency: String? = null,
        start: String? = null,
        end: String? = null,
        minimum: Long? = null,
        maximum: Long? = null,
        search: String? = null,
    ) {
        val actual = count(accountId, accountIds, categoryId, labelId, kind, status, currency, start, end, minimum, maximum, search)
        assertEquals(
            "Filter result count for ${listOf(
                accountId,
                accountIds,
                categoryId,
                labelId,
                kind,
                status,
                currency,
                start,
                end,
                minimum,
                maximum,
                search,
            )}",
            expected,
            actual,
        )
    }

    private suspend fun count(
        accountId: String? = null,
        accountIds: String? = null,
        categoryId: String? = null,
        labelId: String? = null,
        kind: String? = null,
        status: String? = null,
        currency: String? = null,
        start: String? = null,
        end: String? = null,
        minimum: Long? = null,
        maximum: Long? = null,
        search: String? = null,
    ): Int =
        database
            .ledgerDao()
            .observeRecordCount(
                accountId,
                accountIds,
                categoryId,
                labelId,
                kind,
                status,
                currency,
                start,
                end,
                minimum,
                maximum,
                search,
            ).first()
}
