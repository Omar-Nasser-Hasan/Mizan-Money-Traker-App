// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentSourceBalancesCalculatorTest {
    @Test
    fun account_money_without_a_source_is_reported_as_unallocated() {
        val result = PaymentSourceBalancesCalculator.reconcile(3_870_000, cashMinor = 0, bankMinor = 0, creditOwedMinor = 0)

        assertEquals(3_870_000, result.unallocatedMinor)
    }

    @Test
    fun assigning_a_cash_portion_reduces_unallocated_by_the_same_amount() {
        val result = PaymentSourceBalancesCalculator.reconcile(3_870_000, cashMinor = 100_000, bankMinor = 0, creditOwedMinor = 0)

        assertEquals(100_000, result.cashMinor)
        assertEquals(3_770_000, result.unallocatedMinor)
    }

    @Test
    fun card_debt_is_separate_from_reconciliation_of_account_money() {
        val result =
            PaymentSourceBalancesCalculator.reconcile(
                3_870_000,
                cashMinor = 100_000,
                bankMinor = 200_000,
                creditOwedMinor = 500_000,
            )

        assertEquals(500_000, result.creditOwedMinor)
        assertEquals(3_570_000, result.unallocatedMinor)
    }
}
