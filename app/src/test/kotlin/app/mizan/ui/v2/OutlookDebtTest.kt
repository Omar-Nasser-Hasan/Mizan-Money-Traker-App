// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.DebtRepaymentTotal
import app.mizan.domain.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OutlookDebtTest {
    @Test
    fun due_receivable_is_in_outlook_timeline_and_forecast_at_remaining_amount() {
        val today = LocalDate.parse("2026-10-15")
        val data =
            OutlookStats.create(
                rows = emptyList(),
                balances = listOf(AccountBalanceRow("cash", "Cash", "EGP", 2, 10_000, 0, "1", "1")),
                accounts = listOf(AccountEntity("cash", "Cash", "CASH", "#000000", "wallet", "EGP", "2026-01-01", true, false, 0)),
                categories = emptyList(),
                payments = emptyList(),
                occurrences = emptyList(),
                entries = emptyList(),
                currencies = listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true)),
                rates = emptyList(),
                period = Period(today, today.plusDays(30)),
                forecastEnd = today.plusDays(30),
                today = today,
                accountFilter = null,
                includeTypicalIncome = false,
                debts =
                    listOf(
                        DebtEntity("d1", "Nadia", "LENT", "EGP", "cash", 10_000, "2026-09-01", today.plusDays(1).toString()),
                        DebtEntity("d2", "Omar", "BORROWED", "EGP", "cash", 5_000, "2026-09-01", today.plusDays(1).toString()),
                    ),
                debtRepayments = listOf(DebtRepaymentTotal("d1", 4_000), DebtRepaymentTotal("d2", 1_000)),
            )

        assertEquals(2_000L, data.forecast.plannedMinor)
        assertEquals(12_000L, data.forecast.endMinor)
        assertEquals(
            2,
            data.timeline
                .single()
                .items.size,
        )
        assertEquals(
            6_000L,
            data.timeline
                .single()
                .items
                .first { it.name == "Debt · Nadia" }
                .inMinor,
        )
        assertEquals(
            4_000L,
            data.timeline
                .single()
                .items
                .first { it.name == "Debt · Omar" }
                .outMinor,
        )
    }
}
