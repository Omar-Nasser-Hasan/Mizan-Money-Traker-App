// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsAndOutlookTest {
    @Test fun `goal monthly need eta and reached prompt follow progress`() {
        val goal = GoalProgress(120_000, 30_000, 10_000, LocalDate.parse("2026-07-01"))
        assertEquals(16_000L, goal.monthlyNeeded(LocalDate.parse("2026-02-01")))
        assertEquals(LocalDate.parse("2026-06-01"), goal.eta(LocalDate.parse("2026-02-01"), 20_000))
        assertFalse(goal.needsReachedPrompt)
        assertTrue(GoalProgress(100, 100, 0).needsReachedPrompt)
    }

    @Test fun `forecast has no income unless planned income or assumption toggle exists`() {
        val noIncome = OutlookMath.forecast(ForecastInput(1_000, 0, 200, 10, 5, 500, false))
        assertEquals(0L, noIncome.expectedIncomeMinor)
        assertEquals(-200L, noIncome.plannedMinor)
        assertEquals(750L, noIncome.endMinor)
        assertEquals(1_250L, OutlookMath.forecast(ForecastInput(1_000, 0, 200, 10, 5, 500, true)).endMinor)
    }

    @Test fun `outlier capped run rate and rates use specified formula`() {
        assertEquals(10L, OutlookMath.cappedDailyRunRate(listOf(10, 10, 10, 1_000)))
        assertEquals(2_500L, SavingsMath.savingsRate(250, 1_000))
        assertEquals(1_000L, SavingsMath.investRate(300, 200, 1_000))
        assertEquals(5_000L, CreditMath.utilization(listOf(CreditCardBalance(10_000, -5_000))))
        assertNull(CreditMath.debtToIncome(100, 0))
    }

    @Test fun `net worth is cash plus holdings plus receivables minus payables`() {
        val cash = 100_000L
        val holdings = 50_000L
        val receivables = 10_000L
        val payables = 15_000L
        assertEquals(145_000L, cash + holdings + receivables - payables)
    }
}
