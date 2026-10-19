// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.repository.MizanImportWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class MizanImportIntegrationTest {
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()

    @Test fun typed_asset_and_debt_events_import_once_without_becoming_income_or_expense() =
        runBlocking {
            val database = db()
            try {
                database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Pound", "EGP", 2, true)))
                val writer =
                    MizanImportWriter(
                        database,
                        Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC),
                    ) { _, _ -> BigDecimal.ONE }
                val csv = manifest()
                val preview = writer.preview(csv)
                assertEquals(emptyList<String>(), preview.errors)
                assertEquals(5, preview.events.size)
                assertEquals(-35_000L, preview.bucketTotalsMinor.values.single())

                assertEquals(5, writer.commit(csv))
                assertEquals(0, writer.commit(csv))

                val imported = database.ledgerDao().exportRows()
                assertEquals(listOf("ASSET_BUY", "DEBT_BORROW", "DEBT_REPAY_OUT", "DEBT_REPAY_OUT"), imported.map { it.kind }.sorted())
                assertTrue(imported.none { it.kind == "INCOME" || it.kind == "EXPENSE" })
                assertEquals(
                    "gold",
                    database
                        .assetDao()
                        .observeAssets()
                        .first()
                        .single()
                        .id,
                )
                assertEquals(20000L, database.planningDao().debt("loan")?.principalMinor)
                assertEquals(10000L, database.planningDao().repayments("loan"))
                assertEquals(27000L, database.planningDao().debt("historic")?.principalMinor)
                assertEquals(25000L, database.planningDao().repayments("historic"))
                val balance =
                    database
                        .ledgerDao()
                        .observeAllAccountBalances()
                        .first()
                        .sumOf { it.entryMinor }
                assertEquals(-35_000L, balance)
            } finally {
                database.close()
            }
        }

    private fun manifest(): String {
        val headers =
            listOf(
                "event_id",
                "source_ref",
                "event_kind",
                "date",
                "account",
                "account_type",
                "currency",
                "amount",
                "category",
                "payee",
                "payment_type",
                "note",
                "destination",
                "asset_id",
                "asset_name",
                "asset_type",
                "asset_symbol",
                "asset_unit",
                "asset_gold_form",
                "quantity",
                "unit_price",
                "fee",
                "debt_id",
                "debt_person",
                "debt_direction",
                "debt_principal",
                "debt_opened_at",
                "debt_due_date",
            )
        val rows =
            listOf(
                listOf(
                    "borrow",
                    "test",
                    "DEBT_BORROW",
                    "2026-01-01",
                    "Bank",
                    "BANK",
                    "EGP",
                    "200.00",
                    "",
                    "",
                    "CASH",
                    "Borrowed from Dad",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "loan",
                    "Dad",
                    "BORROWED",
                    "200.00",
                    "2026-01-01",
                    "",
                ),
                listOf(
                    "buy",
                    "test",
                    "ASSET_BUY",
                    "2026-01-01",
                    "Bank",
                    "BANK",
                    "EGP",
                    "200.00",
                    "",
                    "",
                    "CASH",
                    "Gold buy",
                    "",
                    "gold",
                    "Gold",
                    "GOLD",
                    "21K",
                    "g",
                    "coin",
                    "4",
                    "50",
                    "0",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                ),
                listOf(
                    "repay",
                    "test",
                    "DEBT_REPAY_OUT",
                    "2026-01-02",
                    "Bank",
                    "BANK",
                    "EGP",
                    "100.00",
                    "",
                    "",
                    "CASH",
                    "Repayment",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "loan",
                    "",
                    "BORROWED",
                    "",
                    "",
                    "",
                ),
                listOf(
                    "historic",
                    "test-debt",
                    "DEBT_OPENING",
                    "2026-05-18",
                    "Essential",
                    "BANK",
                    "EGP",
                    "270.00",
                    "",
                    "",
                    "CASH",
                    "Historic debt from source debt sheet",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "historic",
                    "Essam",
                    "BORROWED",
                    "270.00",
                    "2026-05-18",
                    "2027-05-18",
                ),
                listOf(
                    "historic-repay",
                    "test-debt",
                    "DEBT_REPAY_OUT",
                    "2026-06-22",
                    "Essential",
                    "BANK",
                    "EGP",
                    "250.00",
                    "",
                    "Essam",
                    "CASH",
                    "Historic repayments",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "historic",
                    "",
                    "BORROWED",
                    "",
                    "",
                    "",
                ),
            )
        return (listOf(headers) + rows).joinToString("\n") { row -> row.joinToString(",") }
    }
}
