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

    @Test fun `price source order is dealer then spot then manual`() {
        val day = LocalDate.parse("2026-02-02")
        val manual = PriceCandidate(BigDecimal("100"), day, "Manual", true)
        val spot = PriceCandidate(BigDecimal("110"), day, "Spot-derived", false)
        val dealer = PriceCandidate(BigDecimal("120"), day, "Dealer", false)
        assertEquals(BigDecimal("120"), PriceResolver.resolve(dealer, spot, manual, day, 1)?.price)
        assertEquals(BigDecimal("110"), PriceResolver.resolve(null, spot, manual, day, 1)?.price)
        assertEquals(BigDecimal("100"), PriceResolver.resolve(null, null, manual, day, 1)?.price)
    }

    @Test fun `ten grams of 21k changes net worth by value minus cost and is not an expense`() {
        val cost = 50_000_00L
        val value = AssetValuation.valueMinor(BigDecimal("10"), BigDecimal("5100"), 2, BigDecimal.ZERO)
        assertEquals(51_000_00L, value)
        assertEquals(101_000_00L, AssetValuation.netWorthMinor(100_000_00L - cost, listOf(value)))
        assertEquals(1_000_00L, AssetValuation.netWorthMinor(100_000_00L - cost, listOf(value)) - 100_000_00L)
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
