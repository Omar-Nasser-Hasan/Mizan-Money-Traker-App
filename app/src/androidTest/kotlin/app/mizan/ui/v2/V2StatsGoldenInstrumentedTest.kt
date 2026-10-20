// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.data.db.StatsEntryRow
import app.mizan.data.db.StatsRecordRow
import app.mizan.domain.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class V2StatsGoldenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun snapshot(
        accountFilter: String? = null,
        current: Period = Period(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-20")),
    ): StatsSnapshot {
        val rows =
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
                        parts[8].ifBlank { "" },
                        null,
                        null,
                    )
                }
        val entries = rows.map { StatsEntryRow(it.bookedDate, it.accountId, it.baseMinor, it.id) }
        val accounts =
            rows.map { it.accountId }.distinct().mapIndexed { index, id ->
                AccountEntity(
                    id,
                    id.replaceFirstChar(Char::uppercase),
                    "CASH",
                    "#D9C27F",
                    "wallet",
                    "EGP",
                    "2026-03-01",
                    true,
                    false,
                    index,
                )
            }
        val balances =
            rows.groupBy { it.accountId }.map { (id, accountRows) ->
                AccountBalanceRow(
                    id,
                    accounts.first { it.id == id }.name,
                    "EGP",
                    2,
                    0,
                    accountRows.sumOf { it.baseMinor },
                    "1",
                    "1",
                )
            }
        return StatsSnapshot.create(
            rows,
            entries,
            balances,
            emptyList<FxRateEntity>(),
            accounts,
            emptyList<CategoryEntity>(),
            emptyList<LabelEntity>(),
            emptyList<PayeeEntity>(),
            emptyList<AssetEntity>(),
            emptyList<AssetTransactionEntity>(),
            emptyList<PriceSnapshotEntity>(),
            emptyList<CurrencyEntity>(),
            emptyList<DebtEntity>(),
            current,
            Period(LocalDate.parse("2026-02-09"), LocalDate.parse("2026-02-28")),
            Period(LocalDate.parse("2025-03-01"), LocalDate.parse("2025-03-20")),
            accountFilter,
        )
    }

    @Test
    fun cash_flow_displays_the_golden_dataset_numbers_and_share_captures_a_bitmap() {
        val data = snapshot()
        assertEquals(200_000L, data.current.incomeMinor)
        assertEquals(60_000L, data.current.expenseMinor)
        assertEquals(140_000L, data.current.netMinor)
        val bitmap = AtomicReference<Bitmap?>()
        compose.setContent {
            val view = LocalView.current
            MizanTheme {
                CashFlowPage(data, hide = false) { _, bounds -> bitmap.set(captureCardBitmap(view, bounds)) }
            }
        }
        compose.onNodeWithText("+EGP 1,400.00").assertIsDisplayed()
        compose.onNodeWithText("EGP 2,000.00").assertIsDisplayed()
        compose.onNodeWithText("EGP 600.00").assertIsDisplayed()
        compose.onNodeWithText("Net cash flow by day").assertIsDisplayed()
        compose.onNodeWithText("Cumulative").performClick()
        compose.onNodeWithText("Running net cash flow").assertIsDisplayed()
        compose.onAllNodesWithText("Net cash flow by day").assertCountEquals(0)
        compose.onNodeWithText("Trend").performClick()
        compose.onNodeWithText("Net cash flow by day").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share Cash flow as image").performClick()
        val image = bitmap.get()
        assertNotNull(image)
        assertTrue(requireNotNull(image).width > 0 && image.height > 0)
        saveCapture("p7-cash-flow-card.png", requireNotNull(image))
        image?.recycle()
    }

    @Test
    fun category_and_label_rows_render_the_golden_totals() {
        val data = snapshot()
        assertEquals(30_000L, data.categories.first { it.first == "groceries" }.second)
        assertEquals(60_000L, data.labels.first { it.first == "food" }.second)
        val bitmap = AtomicReference<Bitmap?>()
        compose.setContent {
            val view = LocalView.current
            MizanTheme {
                SpendingPage(data, hide = false, goDeeper = {}, share = { _, bounds -> bitmap.set(captureCardBitmap(view, bounds)) })
            }
        }
        compose.onAllNodesWithText("EGP 300.00", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("EGP 600.00", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithContentDescription("Share Spending as image").performClick()
        val image = requireNotNull(bitmap.get())
        assertTrue(image.width > 0 && image.height > 0)
        saveCapture("p7-spending-card.png", image)
        image.recycle()
    }

    @Test
    fun balance_tab_uses_the_golden_entry_total_and_produces_a_share_bitmap() {
        val data = snapshot()
        assertEquals(140_000L, data.balanceTrend.last())
        val bitmap = AtomicReference<Bitmap?>()
        compose.setContent {
            val view = LocalView.current
            MizanTheme {
                BalancePage(data, hide = false, period = Period(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-20"))) {
                    _,
                    bounds,
                    ->
                    bitmap.set(captureCardBitmap(view, bounds))
                }
            }
        }
        compose.onNodeWithText("EGP 1,400.00", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Share Balance trend as image").performClick()
        val image = requireNotNull(bitmap.get())
        saveCapture("p7-balance-card.png", image)
        assertTrue(image.width > 0 && image.height > 0)
        image.recycle()
    }

    @Test
    fun account_selection_recalculates_cash_flow_and_category_cards() {
        val allAccounts = snapshot()
        val cashOnly = snapshot("cash")
        assertTrue(cashOnly.current.incomeMinor < allAccounts.current.incomeMinor)
        assertTrue(cashOnly.current.expenseMinor < allAccounts.current.expenseMinor)
        assertEquals(70_000L, cashOnly.current.incomeMinor)
        assertEquals(21_000L, cashOnly.current.expenseMinor)
        assertEquals(49_000L, cashOnly.balanceTrend.last())
        assertTrue(cashOnly.categories.sumOf { it.second } < allAccounts.categories.sumOf { it.second })
        assertEquals(listOf("cash"), cashOnly.accountBalances.map { it.name.lowercase() })
    }

    @Test
    fun changing_the_period_recalculates_flow_spending_and_balance_series() {
        val firstThreeDays = snapshot(current = Period(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-03")))
        assertEquals(30_000L, firstThreeDays.current.incomeMinor)
        assertEquals(9_000L, firstThreeDays.current.expenseMinor)
        assertEquals(21_000L, firstThreeDays.current.netMinor)
        assertEquals(9_000L, firstThreeDays.categories.sumOf { it.second })
        assertEquals(3, firstThreeDays.daily.size)
        assertEquals(21_000L, firstThreeDays.balanceTrend.last())
    }

    private fun saveCapture(
        name: String,
        bitmap: Bitmap,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
