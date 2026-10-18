// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyConversionTest {
    @Test
    fun convertsThroughBookingRatesWithDestinationHalfUpRounding() {
        assertEquals(
            2_025,
            MoneyMath.convertMinor(1_000, 2, BigDecimal("50.625"), 2, BigDecimal("25")),
        )
    }

    @Test
    fun rejectsMissingOrInvalidRates() {
        try {
            MoneyMath.convertMinor(100, 2, BigDecimal.ZERO, 2, BigDecimal.ONE)
            throw AssertionError("Expected invalid rate to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun quotesDestinationUnitsPerSourceUnit() {
        assertEquals(
            BigDecimal("2.025000000000"),
            MoneyMath.destinationUnitsPerSourceUnit(BigDecimal("50.625"), BigDecimal("25")),
        )
    }
}
