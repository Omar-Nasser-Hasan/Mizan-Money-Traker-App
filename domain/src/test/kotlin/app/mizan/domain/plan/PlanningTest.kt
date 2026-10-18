// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanningTest {
    @Test fun monthly_recurrence_keeps_month_end_and_leap_year() {
        val dates =
            RecurrenceEngine.occurrences(
                Recurrence(Frequency.MONTHLY, startDate = LocalDate.of(2024, 1, 31)),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 4, 30),
            )
        assertEquals(listOf("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30"), dates.map { it.toString() })
    }

    @Test fun yearly_leap_day_clamps_in_non_leap_year() {
        val dates =
            RecurrenceEngine.occurrences(
                Recurrence(Frequency.YEARLY, startDate = LocalDate.of(2024, 2, 29)),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2028, 12, 31),
            )
        assertEquals(listOf("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"), dates.map { it.toString() })
    }

    @Test fun rollover_and_forecast_follow_spec_formula() {
        val result = BudgetMath.calculate(BudgetMathInput(10_000, 4_000, 10_000, 7_000, Rollover.UNDER, 1_000, 10, 20))
        assertEquals(13_000, result.effectiveLimitMinor)
        assertEquals(13_000, result.forecastMinor)
    }

    @Test fun budget_states_follow_spent_and_forecast_boundaries() {
        fun status(
            spent: Long,
            planned: Long = 0,
            elapsed: Int = 10,
            remaining: Int = 0,
        ) = BudgetMath.status(
            spent,
            BudgetMath.calculate(
                BudgetMathInput(10_000, spent, plannedRemainingMinor = planned, elapsedDays = elapsed, remainingDays = remaining),
            ),
        )

        assertEquals(BudgetStatus.ON_PACE, status(7_900))
        assertEquals(BudgetStatus.TRENDING_OVER, status(8_000, elapsed = 10, remaining = 10))
        assertEquals(BudgetStatus.TRENDING_OVER, status(10_000, remaining = 1))
        assertEquals(BudgetStatus.OVER, status(10_001))
        assertEquals(BudgetStatus.TRENDING_OVER, status(4_000, planned = 6_100))
    }

    @Test fun recurrence_generates_every_frequency_supported_by_the_planned_form() {
        val start = LocalDate.of(2026, 1, 31)
        val cases =
            mapOf(
                Frequency.ONCE to (LocalDate.of(2026, 1, 31) to listOf("2026-01-31")),
                Frequency.DAILY to (LocalDate.of(2026, 2, 2) to listOf("2026-01-31", "2026-02-01", "2026-02-02")),
                Frequency.WEEKLY to (LocalDate.of(2026, 2, 14) to listOf("2026-01-31", "2026-02-07", "2026-02-14")),
                Frequency.MONTHLY to
                    (LocalDate.of(2026, 5, 31) to listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31")),
                Frequency.YEARLY to
                    (LocalDate.of(2030, 1, 31) to listOf("2026-01-31", "2027-01-31", "2028-01-31", "2029-01-31", "2030-01-31")),
            )
        cases.forEach { (frequency, case) ->
            val dates = RecurrenceEngine.occurrences(Recurrence(frequency, startDate = start), start, case.first)
            assertEquals(frequency.name, case.second, dates.map(LocalDate::toString))
        }
    }

    @Test fun debt_outstanding_closes_at_zero() {
        assertEquals(600, DebtBalance(1_000, 400).outstandingMinor)
        assertFalse(DebtBalance(1_000, 400).isClosed)
        assertTrue(DebtBalance(1_000, 1_500).isClosed)
    }

    @Test fun budget_windows_follow_cairo_calendar_boundaries() {
        assertEquals(
            BudgetWindow(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 9)),
            budgetWindow(BudgetPeriod.WEEKLY, LocalDate.of(2026, 10, 7)),
        )
        assertEquals(
            BudgetWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
            budgetWindow(BudgetPeriod.MONTHLY, LocalDate.of(2026, 2, 7)),
        )
    }

    @Test fun recurrence_is_calendar_stable_across_cairo_dst_boundary() {
        val dates =
            RecurrenceEngine.occurrences(
                Recurrence(Frequency.WEEKLY, startDate = LocalDate.of(2026, 4, 17)),
                LocalDate.of(2026, 4, 17),
                LocalDate.of(2026, 5, 8),
            )

        assertEquals(listOf("2026-04-17", "2026-04-24", "2026-05-01", "2026-05-08"), dates.map(LocalDate::toString))
    }
}
