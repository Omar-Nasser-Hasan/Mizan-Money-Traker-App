// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.StatsRecordRow
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.BudgetStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanCalculationsTest {
    @Test
    fun budget_card_and_summary_match_expense_rows_and_domain_forecast() {
        val budget = BudgetEntity("b1", "Food", "CATEGORY", "food", "MONTHLY", limitMinor = 10_000, rollover = "NONE")
        val rows =
            listOf(
                StatsRecordRow("lunch", "2026-10-02", "EXPENSE", -1_500, "cash", "food", "cafe", "", null, null),
                StatsRecordRow("market", "2026-10-10", "EXPENSE", -1_500, "cash", "food", null, "", null, null),
                StatsRecordRow("salary", "2026-10-11", "INCOME", 20_000, "cash", "food", null, "", null, null),
                StatsRecordRow("other", "2026-10-11", "EXPENSE", -8_000, "cash", "other", null, "", null, null),
            )

        val page =
            PlanCalculations.budgets(
                budgets = listOf(budget),
                rows = rows,
                entries = emptyList(),
                payments = emptyList(),
                occurrences = emptyList(),
                categories = listOf(CategoryEntity("food", name = "Food", icon = "", color = "", kind = "EXPENSE")),
                payees = listOf(PayeeEntity("cafe", "Cafe")),
                currencies = listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)),
                rates = emptyList(),
                period = BudgetPeriod.MONTHLY,
                anchor = LocalDate.of(2026, 10, 15),
                today = LocalDate.of(2026, 10, 15),
            )

        assertEquals(3_000L, page.spentMinor)
        assertEquals(10_000L, page.limitMinor)
        assertEquals(3_000L, page.percentBasisPoints)
        assertEquals(6_200L, page.paceMinor)
        assertEquals(7_000L, page.leftMinor)
        assertEquals(BudgetStatus.ON_PACE, page.cards.single().status)
        assertEquals(
            listOf("Food" to 3_000L),
            page.cards
                .single()
                .categories
                .map { it.name to it.amountMinor },
        )
        assertEquals(
            listOf("Cafe" to 1_500L),
            page.cards
                .single()
                .payees
                .map { it.name to it.amountMinor },
        )
    }
}
