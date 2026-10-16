// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val april = Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))
    private val rows =
        listOf(
            StatRecord("i", LocalDate.of(2026, 4, 2), StatKind.INCOME, 10_000, "cash"),
            StatRecord("e", LocalDate.of(2026, 4, 3), StatKind.EXPENSE, -2_500, "cash", "food", "shop", setOf("home")),
            StatRecord("x", LocalDate.of(2026, 4, 4), StatKind.TRANSFER, -500, "cash"),
        )

    @Test fun income_expense_and_cashflow_use_kind_only() {
        val result = StatsEngine.cashFlow(rows, april)
        assertEquals(10_000, result.incomeMinor)
        assertEquals(2_500, result.expenseMinor)
        assertEquals(7_500, result.netMinor)
    }

    @Test fun compare_caps_and_cashflow_sign_flip_uses_absolute_change() {
        assertEquals(99_900L, compare(2_000, 1).percentBasisPoints)
        assertNull(compare(-1_000, 1_000, cashFlow = true).percentBasisPoints)
    }

    @Test fun labels_overlap_and_previous_period_is_same_elapsed_length() {
        assertEquals(2_500L, StatsEngine.byLabel(rows, april)["home"])
        assertEquals(Period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30)), StatPeriods.previous(april))
    }

    @Test fun month_to_date_previous_starts_at_previous_month_start() {
        val octoberToDate = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16))

        assertEquals(Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 16)), StatPeriods.previous(octoberToDate))
    }

    @Test fun report_uses_elapsed_days_and_record_counts() {
        val result = StatsEngine.report(rows, Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5)))

        assertEquals(1, result.incomeCount)
        assertEquals(1, result.expenseCount)
        assertEquals(2_000L, result.averageIncomePerDay)
        assertEquals(500L, result.averageExpensePerDay)
    }

    @Test fun compare_does_not_overflow_for_large_minor_values() {
        assertEquals(10_000L, compare(Long.MAX_VALUE, Long.MAX_VALUE / 2).percentBasisPoints)
    }

    @Test fun one_period_produces_the_same_change_everywhere() {
        val current = StatsEngine.cashFlow(rows, april).netMinor
        val previous = StatsEngine.cashFlow(rows, StatPeriods.previous(april)).netMinor

        assertEquals(compare(current, previous, cashFlow = true), compare(current, previous, cashFlow = true))
    }
}
