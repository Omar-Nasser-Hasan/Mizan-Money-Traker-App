// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.assets

import app.mizan.domain.ledger.AssetCashRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.TxnKind
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
        val posting =
            LedgerService { "asset-buy" }.postAssetCash(
                AssetCashRequest(
                    "investment",
                    "EGP",
                    cost,
                    2,
                    BigDecimal.ONE,
                    TxnKind.ASSET_BUY,
                    java.time.Instant.EPOCH,
                    LocalDate.parse("2026-10-17"),
                ),
            )
        assertEquals(TxnKind.ASSET_BUY, posting.transaction.kind)
        assertEquals(-cost, posting.entries.single().amountMinor)
    }

    @Test fun `resale deduction reduces valuation without changing cost`() {
        assertEquals(9_000L, AssetValuation.valueMinor(BigDecimal("10"), BigDecimal("10"), 2, BigDecimal("10")))
    }

    @Test fun `spot formula applies karat and local premium without floating point`() {
        val base24 = GoldSpotFormula.dealerEquivalentPerGram(BigDecimal("2400"), BigDecimal("50"), 24, BigDecimal.ZERO)
        val premium21 = GoldSpotFormula.dealerEquivalentPerGram(BigDecimal("2400"), BigDecimal("50"), 21, BigDecimal("10"))
        assertEquals(
            0,
            premium21.compareTo(
                base24.multiply(BigDecimal(21)).divide(BigDecimal(24), 12, java.math.RoundingMode.HALF_UP).multiply(BigDecimal("1.10")),
            ),
        )
        assertEquals(
            0,
            GoldSpotFormula
                .premiumPct(
                    premium21,
                    base24.multiply(BigDecimal(21)).divide(BigDecimal(24), 12, java.math.RoundingMode.HALF_UP),
                ).compareTo(BigDecimal("10.0")),
        )
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
