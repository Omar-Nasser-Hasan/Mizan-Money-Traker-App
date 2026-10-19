// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.InboxDraftEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ports.GoldPriceProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class P14InboxPostingIntegrationTest {
    private fun db() =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test fun confirming_posts_once_and_dismissing_posts_nothing() =
        runBlocking {
            val database = db()
            try {
                database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Pound", "EGP", 2, true)))
                database.accountDao().upsert(
                    AccountEntity("cash", "Cash", "CASH", "#0785DB", "wallet", "EGP", "2026-10-01", true, false, 0),
                )
                database.accountDao().upsertBuckets(
                    listOf(
                        AccountBucketEntity("cash", "EGP", 0),
                    ),
                )
                val repository = repository(database)
                val now = Instant.parse("2026-10-19T12:00:00Z")
                database.inboxDao().insert(
                    InboxDraftEntity(
                        "confirm",
                        "SMS",
                        "BM",
                        1_250,
                        "EGP",
                        "Cafe",
                        null,
                        occurredAt = now.toEpochMilli(),
                        receivedAt = now.toEpochMilli(),
                        rawMessage = "sample",
                    ),
                )
                val posting =
                    LedgerService().postExpense(
                        ExpenseRequest("cash", "EGP", 1_250, 2, BigDecimal.ONE, "food", now, LocalDate.parse("2026-10-19")),
                    )

                repository.postInboxDraft("confirm", posting)

                assertEquals("CONFIRMED", database.inboxDao().findById("confirm")?.status)
                assertEquals(1, countTransactions(database))
                assertThrows(IllegalStateException::class.java) { runBlocking { repository.postInboxDraft("confirm", posting) } }
                assertEquals(1, countTransactions(database))

                database.inboxDao().insert(
                    InboxDraftEntity(
                        "dismiss",
                        "SMS",
                        "BM",
                        500,
                        "EGP",
                        "Shop",
                        null,
                        occurredAt = now.toEpochMilli(),
                        receivedAt = now.toEpochMilli(),
                        rawMessage = "sample",
                    ),
                )
                repository.rejectInboxDraft("dismiss")
                assertEquals(0, countPending(database, "dismiss"))
                assertEquals(1, countTransactions(database))
            } finally {
                database.close()
            }
        }

    @Test fun reversal_confirmation_creates_adjustment_not_income_or_expense() =
        runBlocking {
            val database = db()
            try {
                database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Pound", "EGP", 2, true)))
                database.accountDao().upsert(
                    AccountEntity("cash", "Cash", "CASH", "#0785DB", "wallet", "EGP", "2026-10-01", true, false, 0),
                )
                database.accountDao().upsertBuckets(listOf(AccountBucketEntity("cash", "EGP", 0)))
                val repository = repository(database)
                val now = Instant.parse("2026-10-19T12:00:00Z")
                database.inboxDao().insert(
                    InboxDraftEntity(
                        "reversal",
                        "SMS",
                        "HSBC",
                        1_250,
                        "EGP",
                        "Cafe",
                        "1234",
                        "REVERSAL",
                        occurredAt = now.toEpochMilli(),
                        receivedAt = now.toEpochMilli(),
                        rawMessage = "reversal",
                    ),
                )

                repository.postInboxAdjustment("reversal", "cash", "EGP", 1_250)

                assertEquals("ADJUSTMENT", transactionKind(database, 1))
                assertEquals("CONFIRMED", database.inboxDao().findById("reversal")?.status)
            } finally {
                database.close()
            }
        }

    private fun repository(database: AppDatabase) =
        MizanRepository(
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
            ApplicationProvider.getApplicationContext(),
            OkHttpClient(),
            Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC),
        )

    private fun countTransactions(database: AppDatabase): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun countPending(
        database: AppDatabase,
        id: String,
    ): Int =
        database.openHelper.readableDatabase
            .query(
                "SELECT COUNT(*) FROM inbox_drafts WHERE id = '$id' AND status = 'PENDING'",
            ).use {
                it.moveToFirst()
                it.getInt(0)
            }

    private fun transactionKind(
        database: AppDatabase,
        index: Int,
    ): String =
        database.openHelper.readableDatabase
            .query(
                "SELECT kind FROM transactions ORDER BY createdAt LIMIT 1 OFFSET ${index - 1}",
            ).use {
                check(it.moveToFirst())
                it.getString(0)
            }
}
