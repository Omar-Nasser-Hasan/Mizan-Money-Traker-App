// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.PlannedOccurrenceEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.db.StatsEntryRow
import app.mizan.data.db.StatsRecordRow
import app.mizan.domain.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class V2StatsP8InstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.parse("2026-03-01")
    private val period = Period(today, LocalDate.parse("2026-03-20"))
    private val accounts =
        listOf(AccountEntity("cash", "Cash", "CASH", "#D9C27F", "wallet", "EGP", today.toString(), true, false, 0))
    private val currencies = listOf(CurrencyEntity("EGP", "Egyptian Pound", "E£", 2, true))

    private fun goldenRows(): List<StatsRecordRow> =
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("golden/stats-v1.csv")
            .bufferedReader()
            .readLines()
            .drop(1)
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split(',')
                StatsRecordRow(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3].toLong(),
                    parts[4],
                    parts[6].ifBlank {
                        null
                    },
                    parts[7].ifBlank { null },
                    parts[8],
                    null,
                    null,
                )
            }

    private fun entries(rows: List<StatsRecordRow>) = rows.map { StatsEntryRow(it.bookedDate, it.accountId, it.baseMinor, it.id) }

    private fun reportData(): ReportsStats {
        val rows = goldenRows()
        val expenseCategories =
            listOf(
                CategoryEntity("food", null, "Food & drinks", "restaurant", "#123456", "EXPENSE"),
                CategoryEntity("groceries", "food", "Groceries", "shopping_basket", "#123456", "EXPENSE"),
                CategoryEntity("restaurants", "food", "Restaurants", "restaurant", "#123456", "EXPENSE", isDebtService = true),
            )
        val incomeCategories =
            listOf(
                CategoryEntity("income", null, "Income", "payments", "#123456", "INCOME"),
                CategoryEntity("salary", "income", "Salary", "payments", "#123456", "INCOME"),
            )
        val card = accounts.single().copy(id = "card", name = "Card", type = "CREDIT_CARD", creditLimitMinor = 100_000L)
        return ReportsStats.create(
            rows,
            entries(rows),
            listOf(AccountBalanceRow("card", "Card", "EGP", 2, -50_000L, 0L, "1", "1")),
            accounts + card,
            expenseCategories,
            incomeCategories,
            period,
            today,
            null,
        )
    }

    private fun payment(kind: String = "INCOME") =
        PlannedPaymentEntity(
            id = "planned-income",
            kind = kind,
            name = "Scheduled salary",
            accountId = "cash",
            currency = "EGP",
            amountMinor = 12_000L,
            paymentType = "CARD",
            frequency = "ONCE",
            interval = 1,
            startDate = "2026-03-02",
            remindDaysBefore = 0,
        )

    private fun outlookData(
        rows: List<StatsRecordRow> = emptyList(),
        payments: List<PlannedPaymentEntity> = emptyList(),
        typical: Boolean = false,
    ) = OutlookStats.create(
        rows,
        emptyList(),
        accounts,
        emptyList(),
        payments,
        emptyList<PlannedOccurrenceEntity>(),
        entries(rows),
        currencies,
        emptyList(),
        period,
        LocalDate.parse("2026-03-05"),
        today,
        null,
        typical,
    )

    @Test
    fun report_numbers_match_the_shared_golden_dataset_and_use_elapsed_days() {
        val data = reportData()
        assertEquals(20, data.report.incomeCount)
        assertEquals(20, data.report.expenseCount)
        assertEquals(20, data.report.elapsedDays)
        assertEquals(200_000L, data.report.cashFlow.incomeMinor)
        assertEquals(60_000L, data.report.cashFlow.expenseMinor)
        assertEquals(140_000L, data.report.cashFlow.netMinor)
        assertEquals(10_000L, data.report.averageIncomePerDay)
        assertEquals(3_000L, data.report.averageExpensePerDay)
        assertEquals(10_000L, data.report.averageIncomePerRecord)
        assertEquals(3_000L, data.report.averageExpensePerRecord)
        assertEquals(60_000L, data.expenseCategories.single().amountMinor)
        assertEquals(0L, data.expenseCategories.single().previousMinor)
        assertEquals(
            null,
            data.expenseCategories
                .single()
                .change.percentBasisPoints,
        )
        assertEquals(
            30_000L,
            data.expenseCategories
                .single()
                .children
                .single { it.id == "restaurants" }
                .amountMinor,
        )
        assertEquals(
            30_000L,
            data.expenseCategories
                .single()
                .children
                .single { it.id == "groceries" }
                .amountMinor,
        )
        assertEquals(200_000L, data.incomeCategories.single().amountMinor)
        assertEquals(
            200_000L,
            data.incomeCategories
                .single()
                .children
                .single { it.id == "salary" }
                .amountMinor,
        )
        assertEquals(7_000L, data.savingsRate)
        assertEquals(0L, data.investRate)
        assertEquals(6, data.savingsSeries.size)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 7_000L), data.savingsSeries)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L), data.investSeries)
        assertEquals(30_000L, data.debtServiceMinor)
        assertEquals(1_500L, data.debtToIncome)
        assertEquals(5_000L, data.creditUtilization)
        assertTrue(data.hasCreditCards)
    }

    @Test
    fun forecast_only_adds_scheduled_or_assumed_income_and_builds_the_timeline() {
        val empty = outlookData()
        assertEquals(0L, empty.forecast.startMinor)
        assertEquals(0L, empty.forecast.plannedMinor)
        assertEquals(0L, empty.forecast.unplannedMinor)
        assertEquals(0L, empty.forecast.expectedIncomeMinor)
        assertEquals(0L, empty.forecast.endMinor)

        val priorIncomes =
            listOf("2025-12-15", "2026-01-15", "2026-02-15").mapIndexed { index, date ->
                StatsRecordRow("prior-$index", date, "INCOME", 10_000L, "cash", "salary", null, "", null, null)
            }
        val scheduled = outlookData(priorIncomes, listOf(payment()))
        assertEquals(12_000L, scheduled.forecast.plannedMinor)
        assertEquals(0L, scheduled.forecast.expectedIncomeMinor)
        assertEquals(12_000L, scheduled.forecast.endMinor)
        assertEquals(12_000L, scheduled.timeline.single().inMinor)
        assertEquals(
            12_000L,
            scheduled.timeline
                .single()
                .items
                .single()
                .balanceAfterMinor,
        )
        assertEquals(10_000L, outlookData(priorIncomes, typical = true).forecast.expectedIncomeMinor)
    }

    @Test
    fun outlook_cards_render_and_share_bitmaps() {
        val outlookBitmap = AtomicReference<Bitmap?>()
        compose.setContent {
            val view = LocalView.current
            MizanTheme {
                V2StatsOutlookPage(outlookData(payments = listOf(payment())), false, false, {
                }, { _, bounds -> outlookBitmap.set(captureCardBitmap(view, bounds)) })
            }
        }
        compose.onNodeWithContentDescription("Share Planned payments as image").performClick()
        assertNotNull(outlookBitmap.get())
        compose.onNodeWithContentDescription("Share Balance forecast as image").performScrollTo().performClick()
        assertNotNull(outlookBitmap.get())
    }

    @Test
    fun each_reports_card_exposes_the_bitmap_share_action() {
        val reportsBitmap = AtomicReference<Bitmap?>()
        val selectedCategory = AtomicReference<String?>()
        compose.setContent {
            val view = LocalView.current
            MizanTheme {
                V2StatsReportsPage(
                    reportData(),
                    false,
                    { selectedCategory.set(it) },
                    { _, bounds -> reportsBitmap.set(captureCardBitmap(view, bounds)) },
                )
            }
        }
        listOf(
            "Cash flow table",
            "Income & expenses book",
            "Savings & investing",
            "Debt to income",
        ).forEach { title ->
            compose
                .onNodeWithContentDescription("Share $title as image")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            assertTrue(requireNotNull(reportsBitmap.getAndSet(null)).width > 0)
        }
        compose.onNodeWithContentDescription("Expand Food & drinks").performScrollTo().performClick()
        compose.onNodeWithText("Groceries").performScrollTo().performClick()
        assertEquals("groceries", selectedCategory.get())
    }
}
