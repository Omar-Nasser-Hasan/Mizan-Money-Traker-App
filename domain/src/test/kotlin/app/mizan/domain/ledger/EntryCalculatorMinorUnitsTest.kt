// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryCalculatorMinorUnitsTest {
    @Test
    fun `rounds calculator results at the selected currency precision`() {
        assertEquals(12_580L, EntryCalculator.minorUnits("100+25.8", 2))
        assertEquals(1_235L, EntryCalculator.minorUnits("1.2345", 3))
        assertEquals(2L, EntryCalculator.minorUnits("1.5", 0))
    }
}
