// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AssetsTest {
    @Test fun `partial sell releases moving average cost and keeps realized profit`() {
        val position =
            AverageCost.position(
                listOf(
                    trade("a", AssetTransactionKind.BUY, "10", 1000),
                    trade("b", AssetTransactionKind.BUY, "10", 3000),
                    trade("c", AssetTransactionKind.SELL, "5", 1500),
                ),
            )
        assertEquals(BigDecimal("15"), position.quantity)
        assertEquals(3000, position.costMinor)
        assertEquals(500, position.realizedProfitMinor)
    }

    @Test fun `xirr gives known annual return`() {
        val result =
            Xirr.calculate(
                listOf(
                    XirrCashFlow(LocalDate.parse("2026-01-01"), BigDecimal("-1000")),
                    XirrCashFlow(LocalDate.parse("2027-01-01"), BigDecimal("1100")),
                ),
            )
        assertNotNull(result)
        assertEquals(result.toString(), true, result!!.subtract(BigDecimal("0.10")).abs() <= BigDecimal("0.001"))
    }

    @Test fun `gold karat and pound ratios derive from 21k`() {
        val prices = GoldRatios.from21K(BigDecimal("100"))
        assertEquals(0, prices.getValue("24K").compareTo(BigDecimal("114.285714285700")))
        assertEquals(0, prices.getValue("18K").compareTo(BigDecimal("85.714285714300")))
        assertEquals(0, prices.getValue("POUND").compareTo(BigDecimal("800")))
    }

    @Test fun `old fallback is retained and visibly stale`() {
        val resolved =
            PriceResolver.resolve(
                null,
                null,
                PriceCandidate(BigDecimal("3000"), LocalDate.parse("2026-01-01"), "Last known", false),
                LocalDate.parse("2026-02-02"),
                24,
            )
        assertNotNull(resolved)
        assertFalse(resolved!!.manual)
        assertEquals(true, resolved.stale)
        assertEquals(true, resolved.fallback)
    }

    private fun trade(
        id: String,
        kind: AssetTransactionKind,
        quantity: String,
        total: Long,
    ) = AssetTransaction(
        id,
        "gold",
        kind,
        LocalDate.parse(
            "2026-01-0${if (id == "a") {
                1
            } else if (id == "b") {
                2
            } else {
                3
            }}",
        ),
        BigDecimal(quantity),
        BigDecimal("1"),
        0,
        total,
    )
}
