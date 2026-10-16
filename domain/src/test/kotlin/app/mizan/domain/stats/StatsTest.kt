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
        assertEquals(Period(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 31)), StatPeriods.previous(april))
    }
}
