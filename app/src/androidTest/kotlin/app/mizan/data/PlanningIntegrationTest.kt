// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.TxnStatus
import app.mizan.domain.ports.GoldPriceProvider
import app.mizan.worker.PlanningReminderScheduler
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlanningIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MizanRepository

    @Before fun createDatabase() =
        runBlocking {
            database =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        AppDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()
            val context = ApplicationProvider.getApplicationContext<Context>()
            repository =
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
                    context,
                    OkHttpClient(),
                    Clock.systemUTC(),
                )
            database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)))
            database.accountDao().upsert(AccountEntity("cash", "Cash", "CASH", "#F2B84B", "wallet", "EGP", "2026-01-01", true, false, 0))
            database.accountDao().upsertBuckets(listOf(AccountBucketEntity("cash", "EGP", 100_000)))
            database.referenceDataDao().upsertCategory(
                CategoryEntity("food", name = "Food", icon = "restaurant", color = "#111111", kind = "EXPENSE"),
            )
        }

    @After fun closeDatabase() {
        database.close()
    }

    @Test fun create_edit_archive_budget_and_planned_payment_round_trip() =
        runBlocking {
            repository.saveBudget(BudgetEntity("budget", "Food", "CATEGORY", "food", "MONTHLY", limitMinor = 50_000, rollover = "UNDER"))
            assertEquals("Food", repository.activeBudgets().single().name)
            repository.saveBudget(BudgetEntity("budget", "Dining", "CATEGORY", "food", "MONTHLY", limitMinor = 75_000, rollover = "BOTH"))
            assertEquals(75_000L, repository.activeBudgets().single().limitMinor)
            repository.setBudgetArchived("budget", true)
            assertTrue(repository.activeBudgets().isEmpty())
            repository.setBudgetArchived("budget", false)
            assertEquals("Dining", repository.activeBudgets().single().name)
            assertTrue(repository.recordBudgetAlert("budget", "2026-10-01", 80))
            assertTrue(!repository.recordBudgetAlert("budget", "2026-10-01", 80))
            assertTrue(repository.recordBudgetAlert("budget", "2026-11-01", 80))

            repository.savePlannedPayment(payment("planned"))
            assertEquals("Rent", repository.activePlannedPayments().single().name)
            repository.savePlannedPayment(payment("planned").copy(name = "Updated rent"))
            assertEquals("Updated rent", repository.activePlannedPayment("planned")?.name)
            repository.setPlannedActive("planned", false)
            assertTrue(repository.activePlannedPayments().isEmpty())
            repository.setPlannedActive("planned", true)
            assertEquals("Updated rent", repository.activePlannedPayments().single().name)
        }

    @Test fun entry_confirmation_links_one_transaction_and_skip_snooze_are_persisted() =
        runBlocking {
            val due = LocalDate.now().toString()
            repository.savePlannedPayment(payment("paid-plan"))
            val posting = expensePosting("posted-one", due)
            repository.post(posting, plannedOccurrence = "paid-plan" to due)
            assertEquals("posted-one", repository.plannedOccurrence("paid-plan", due)?.txnId)
            assertEquals("PAID", repository.plannedOccurrence("paid-plan", due)?.status)

            runCatching { repository.post(expensePosting("posted-two", due), plannedOccurrence = "paid-plan" to due) }
            assertNotNull(database.ledgerDao().findTransaction("posted-one"))
            assertEquals(null, database.ledgerDao().findTransaction("posted-two"))
            assertEquals(1, repository.observeStatsRows().first().size)

            repository.savePlannedPayment(payment("skip-plan"))
            repository.markPlannedOccurrence("skip-plan", due, "SKIPPED")
            assertEquals("SKIPPED", repository.plannedOccurrence("skip-plan", due)?.status)

            val context = ApplicationProvider.getApplicationContext<Context>()
            PlanningReminderScheduler.snooze(context, "skip-plan", due)
            val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("snooze-skip-plan-$due").get()
            assertEquals(1, work.size)
            assertTrue(work.single().state in setOf(androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.RUNNING))
        }

    @Test fun debt_create_edit_partial_repay_and_close_stays_out_of_income_and_expense() =
        runBlocking {
            val due = LocalDate.now().plusDays(7).toString()
            val debt =
                DebtEntity("debt-roundtrip", "Nadia", "LENT", "EGP", "cash", 30_000, LocalDate.now().toString(), due, "Lent for travel")
            repository.saveDebt(debt)
            val originId = requireNotNull(database.ledgerDao().findDebtOrigin(debt.id)).id
            repository.saveDebt(debt.copy(person = "Nadia A", principalMinor = 40_000, note = "Updated loan"))
            assertEquals(originId, database.ledgerDao().findDebtOrigin(debt.id)?.id)
            assertEquals(
                40_000L,
                kotlin.math.abs(
                    database
                        .ledgerDao()
                        .entriesForTransaction(originId)
                        .single()
                        .amountMinor,
                ),
            )

            val context = ApplicationProvider.getApplicationContext<Context>()
            PlanningReminderScheduler.remindDebt(context, debt.id)
            val reminderWork = WorkManager.getInstance(context).getWorkInfosForUniqueWork("debt-reminder-${debt.id}").get()
            assertTrue(reminderWork.isNotEmpty())

            repository.repayDebt(debt.id, 10_000, "cash", LocalDate.now())
            assertEquals(10_000L, repository.debtRepayments(debt.id))
            assertEquals("ACTIVE", requireNotNull(repository.debt(debt.id)).status)
            repository.repayDebt(debt.id, 30_000, "cash", LocalDate.now())
            assertEquals("CLOSED", requireNotNull(repository.debt(debt.id)).status)
            val borrowed = DebtEntity("debt-borrowed", "Omar", "BORROWED", "EGP", "cash", 5_000, LocalDate.now().toString(), due)
            repository.saveDebt(borrowed)
            repository.repayDebt(borrowed.id, 5_000, "cash", LocalDate.now())
            assertEquals("CLOSED", requireNotNull(repository.debt(borrowed.id)).status)
            val kinds =
                repository
                    .observeStatsRows()
                    .first()
                    .filter { it.debtId == debt.id || it.debtId == borrowed.id }
                    .map { it.kind }
            assertTrue(kinds.containsAll(listOf("DEBT_LEND", "DEBT_REPAY_IN", "DEBT_BORROW", "DEBT_REPAY_OUT")))
            assertTrue(kinds.none { it == "INCOME" || it == "EXPENSE" })
            val cash = repository.observeAccountBalances().first().single { it.accountId == "cash" && it.currency == "EGP" }
            assertEquals(100_000L, cash.openingMinor + cash.entryMinor)
        }

    private fun payment(id: String) =
        PlannedPaymentEntity(
            id,
            "EXPENSE",
            "Rent",
            "food",
            "cash",
            currency = "EGP",
            amountMinor = 15_000,
            paymentType = "CASH",
            frequency = "MONTHLY",
            interval = 1,
            startDate = LocalDate.now().toString(),
            remindDaysBefore = 2,
        )

    private fun expensePosting(
        id: String,
        due: String,
    ): app.mizan.domain.ledger.LedgerPosting {
        val posting =
            LedgerService().postExpense(
                ExpenseRequest(
                    "cash",
                    "EGP",
                    15_000,
                    2,
                    BigDecimal.ONE,
                    "food",
                    Instant.now(),
                    LocalDate.parse(due),
                    note = "Planned rent",
                    status = TxnStatus.CLEARED,
                    paymentType = PaymentType.CASH,
                ),
            )
        return posting.copy(
            transaction = posting.transaction.copy(id = id),
            entries = posting.entries.mapIndexed { index, entry -> entry.copy(id = "$id-entry-$index", txnId = id) },
        )
    }
}
