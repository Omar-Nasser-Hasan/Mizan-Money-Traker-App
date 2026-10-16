// Copyright (c) 2026 Mizan contributors
package app.mizan.domain

import app.mizan.domain.ports.BuySellPrice
import org.junit.Assert.assertEquals
import org.junit.Test

class PortsTest {
    @Test
    fun `prices keep minor units`() {
        assertEquals(6_230_00L, BuySellPrice(6_230_00L, 6_250_00L).buyEgp)
    }
}
