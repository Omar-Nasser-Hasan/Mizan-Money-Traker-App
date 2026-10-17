// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.zakat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ZakatCalculatorTest {
    @Test
    fun `uses the 85 gram gold nisab and subtracts due liabilities`() {
        val result =
            ZakatCalculator.calculate(
                ZakatInput(
                    cashMinor = 1_000_000,
                    goldMinor = 400_000,
                    investmentsMinor = 200_000,
                    receivablesMinor = 100_000,
                    debtsDueWithinYearMinor = 150_000,
                    creditCardDebtMinor = 50_000,
                    gold24kPricePerGramMinor = 6_000,
                ),
            )
        assertEquals(1_500_000, result.eligibleMinor)
        assertEquals(510_000, result.nisabMinor)
        assertTrue(result.isDue)
        assertEquals(37_500, result.dueMinor)
    }

    @Test
    fun `does not charge below nisab and rounds half up`() {
        val result = ZakatCalculator.calculate(ZakatInput(100, 0, 0, 0, 0, 0, 2, BigDecimal("85"), BigDecimal("0.025")))
        assertFalse(result.isDue)
        assertEquals(0, result.dueMinor)
    }
}
