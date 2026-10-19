// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.assets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class WealthDebtTest {
    @Test fun net_worth_includes_receivables_and_payables_without_recounting_card_cash() {
        val value =
            WealthCalculator.calculate(
                listOf(WealthCashBucket(50_000, 2, BigDecimal.ONE), WealthCashBucket(-10_000, 2, BigDecimal.ONE)),
                emptyList(),
                receivablesEgpMinor = 20_000,
                payablesEgpMinor = 7_000,
            )
        assertEquals(40_000L, value.cashEgpMinor)
        assertEquals(53_000L, value.netWorthEgpMinor)
        assertEquals(20_000L, value.receivablesEgpMinor)
        assertEquals(7_000L, value.payablesEgpMinor)
    }
}
