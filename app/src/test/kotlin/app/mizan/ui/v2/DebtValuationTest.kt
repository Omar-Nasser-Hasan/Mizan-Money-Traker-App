// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.DebtRepaymentRow
import app.mizan.data.db.FxRateEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DebtValuationTest {
    @Test fun remaining_units_are_valued_once_at_the_as_of_rate() {
        val debts =
            listOf(
                DebtEntity("lent", "Friend", "LENT", "USD", "cash", 30_000, "2026-10-01"),
                DebtEntity("borrowed", "Friend", "BORROWED", "EGP", "cash", 10_000, "2026-10-01"),
            )
        val paid = listOf(DebtRepaymentRow("lent", "2026-10-19", 10_000), DebtRepaymentRow("borrowed", "2026-10-19", 3_000))
        val currencies = listOf(CurrencyEntity("USD", "Dollar", "USD", 2, true), CurrencyEntity("EGP", "Pound", "EGP", 2, true))
        val rates = listOf(FxRateEntity("2026-10-01", "USD", "45", "Manual", 1), FxRateEntity("2026-10-19", "USD", "50", "Manual", 2))
        val current = valuedDebtBalances(debts, paid, currencies, rates, LocalDate.parse("2026-10-19"))
        assertEquals(listOf(1_000_000L, 7_000L), current.map { it.principalMinor })
        assertEquals(listOf(0L, 0L), current.map { it.repaymentsMinor })
        val previous = valuedDebtBalances(debts, paid, currencies, rates, LocalDate.parse("2026-10-18"))
        assertEquals(listOf(1_350_000L, 10_000L), previous.map { it.principalMinor })
    }
}
