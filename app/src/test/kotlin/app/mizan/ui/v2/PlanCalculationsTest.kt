// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.DebtRepaymentTotal
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.StatsRecordRow
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.BudgetStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanCalculationsTest {
    @Test
    fun home_and_plan_agree_when_forecast_exceeds_limit_before_eighty_percent_is_spent() {
        val today = LocalDate.of(2026, 10, 15)
        val budgets =
            listOf(
                BudgetEntity("pace", "On pace", "ALL", "", "MONTHLY", limitMinor = 10_000, rollover = "NONE"),
                BudgetEntity("trend", "Trending", "ALL", "", "MONTHLY", limitMinor = 5_000, rollover = "NONE"),
                BudgetEntity("over", "Over", "ALL", "", "MONTHLY", limitMinor = 2_000, rollover = "NONE"),
            )
        val rows = listOf(StatsRecordRow("expense", "2026-10-10", "EXPENSE", -3_000, "fun", null, null, "", null, null))
        val currencies = listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true))
        val home = PlanCalculations.homeBudgets(budgets, rows, emptyList(), emptyList(), emptyList(), currencies, emptyList(), today)
        val plan =
            PlanCalculations
                .budgets(
                    budgets,
                    rows,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    currencies,
                    emptyList(),
                    BudgetPeriod.MONTHLY,
                    today,
                    today,
                ).cards

        assertEquals(listOf("over", "trend", "pace"), home.map { it.budget.id })
        home.forEach { card ->
            val sameBudget = plan.single { it.budget.id == card.budget.id }
            assertEquals(sameBudget.result, card.result)
            assertEquals(sameBudget.status, card.status)
            assertEquals(sameBudget.spentMinor, card.spentMinor)
        }
        assertEquals(BudgetStatus.ON_PACE, home.single { it.budget.id == "pace" }.status)
        assertEquals(BudgetStatus.TRENDING_OVER, home.single { it.budget.id == "trend" }.status)
        assertEquals(BudgetStatus.OVER, home.single { it.budget.id == "over" }.status)
    }

    @Test
    fun home_preserves_rollover_and_ignores_paid_planned_occurrences_like_plan() {
        val today = LocalDate.of(2026, 10, 15)
        val budgets = listOf(BudgetEntity("food", "Food", "ACCOUNT", "fun", "MONTHLY", limitMinor = 10_000, rollover = "UNDER"))
        val rows =
            listOf(
                StatsRecordRow("previous", "2026-09-10", "EXPENSE", -4_000, "fun", null, null, "", null, null),
                StatsRecordRow("current", "2026-10-10", "EXPENSE", -3_000, "fun", null, null, "", null, null),
            )
        val entries =
            listOf(
                app.mizan.data.db
                    .StatsEntryRow("2026-09-10", "fun", -4_000, "previous"),
                app.mizan.data.db
                    .StatsEntryRow("2026-10-10", "fun", -3_000, "current"),
            )
        val payment =
            app.mizan.data.db.PlannedPaymentEntity(
                "planned",
                "EXPENSE",
                "Bill",
                accountId = "fun",
                currency = "EGP",
                amountMinor = 15_000,
                paymentType = "CREDIT",
                frequency = "ONCE",
                interval = 1,
                startDate = "2026-10-20",
                remindDaysBefore = 0,
            )
        val currencies = listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true))
        val home =
            PlanCalculations
                .homeBudgets(
                    budgets,
                    rows,
                    entries,
                    listOf(payment),
                    emptyList(),
                    currencies,
                    emptyList(),
                    today,
                ).single()
        val plan =
            PlanCalculations
                .budgets(
                    budgets,
                    rows,
                    entries,
                    listOf(payment),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    currencies,
                    emptyList(),
                    BudgetPeriod.MONTHLY,
                    today,
                    today,
                ).cards
                .single()
        assertEquals(plan.result, home.result)
        assertEquals(16_000L, home.result.effectiveLimitMinor)
        assertEquals(BudgetStatus.TRENDING_OVER, home.status)
        val paid =
            listOf(
                app.mizan.data.db
                    .PlannedOccurrenceEntity("planned", "2026-10-20", "PAID"),
            )
        val afterPayment =
            PlanCalculations
                .homeBudgets(
                    budgets,
                    rows,
                    entries,
                    listOf(payment),
                    paid,
                    currencies,
                    emptyList(),
                    today,
                ).single()
        assertEquals(BudgetStatus.ON_PACE, afterPayment.status)
    }

    @Test
    fun partially_repaid_debt_appears_in_planned_with_only_the_outstanding_balance() {
        val today = LocalDate.parse("2026-10-15")
        val page =
            PlanCalculations.planned(
                payments = emptyList(),
                occurrences = emptyList(),
                debts = listOf(DebtEntity("loan", "Nadia", "LENT", "EGP", "cash", 10_000, "2026-09-01", "2026-10-17")),
                balances = emptyList(),
                accounts = emptyList(),
                rows = emptyList(),
                entries = emptyList(),
                categories = emptyList(),
                currencies = listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)),
                rates = emptyList(),
                today = today,
                debtRepayments = listOf(DebtRepaymentTotal("loan", 4_000)),
            )

        assertEquals(6_000L, page.next7InMinor)
        assertEquals(6_000L, page.next7.single().amountMinor)
        assertEquals(true, page.next7.single().isReadOnly)
        assertEquals(
            "Nadia",
            page.next7
                .single()
                .debt
                ?.person,
        )
    }

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
