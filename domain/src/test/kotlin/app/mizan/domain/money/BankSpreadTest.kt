// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BankSpreadTest {
    @Test fun exact_rate_and_manual_pin() {
        assertEquals(BigDecimal("50.625000"), bankAdjustedRate(BigDecimal("50.00"), BigDecimal("1.25")))
        assertEquals(BigDecimal("49.123456"), bankAdjustedRate(BigDecimal("50"), BigDecimal("2"), BigDecimal("49.123456")))
        assertTrue(runCatching { bankAdjustedRate(BigDecimal("50"), BigDecimal("-1")) }.isFailure)
        assertTrue(runCatching { bankAdjustedRate(BigDecimal.ZERO, BigDecimal.ZERO) }.isFailure)
    }
}
