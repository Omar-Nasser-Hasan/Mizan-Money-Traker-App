// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class V2AssetTradeModelsTest {
    @Test
    fun `buy cash paid includes fee and compares current dealer value`() {
        val preview = V2AssetTradeModels.preview(BigDecimal("2"), BigDecimal("100"), 500, 2, false, BigDecimal("110"), BigDecimal("10"))
        assertEquals(20_000L, preview.grossMinor)
        assertEquals(20_500L, preview.cashMinor)
        assertEquals(19_800L, preview.currentDealerValueMinor)
        assertEquals(-700L, preview.marketImpactMinor)
    }

    @Test
    fun `sale cash received subtracts fee and does not invent market comparison`() {
        val preview = V2AssetTradeModels.preview(BigDecimal("2"), BigDecimal("100"), 100, 2, true, null, BigDecimal.ZERO)
        assertEquals(20_000L, preview.grossMinor)
        assertEquals(19_900L, preview.cashMinor)
        assertNull(preview.currentDealerValueMinor)
    }
}
