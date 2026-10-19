// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AssetEntity
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.Xirr
import app.mizan.domain.assets.XirrCashFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class V2AssetDetailModelsTest {
    @Test
    fun `chart history applies the selected cutoff and falls back to trades when market history is sparse`() {
        val market =
            listOf(
                V2AssetHistoryPoint(LocalDate.parse("2025-01-01"), 100),
                V2AssetHistoryPoint(LocalDate.parse("2025-07-01"), 200),
                V2AssetHistoryPoint(LocalDate.parse("2025-10-01"), 300),
            )
        val trades =
            listOf(
                V2AssetHistoryPoint(LocalDate.parse("2025-09-15"), 250),
                V2AssetHistoryPoint(LocalDate.parse("2025-10-01"), 300),
            )

        val sixMonths = V2AssetDetailModels.chartHistory(market, trades, LocalDate.parse("2025-04-01"))
        val oneMonth = V2AssetDetailModels.chartHistory(market, trades, LocalDate.parse("2025-09-01"))
        val all = V2AssetDetailModels.chartHistory(market, trades, LocalDate.MIN)

        assertEquals(listOf(200L, 300L), sixMonths.points.map { it.valueMinor })
        assertEquals("Trade prices · limited market history", oneMonth.sourceLabel)
        assertEquals(listOf(250L, 300L), oneMonth.points.map { it.valueMinor })
        assertEquals(listOf(100L, 200L, 300L), all.points.map { it.valueMinor })
    }

    @Test
    fun `detail figures preserve average cost realized and unrealized domain values and xirr`() {
        val day1 = LocalDate.parse("2025-01-01")
        val day2 = LocalDate.parse("2025-07-01")
        val today = LocalDate.parse("2026-01-01")
        val trades =
            listOf(
                AssetTransaction("buy", "a", AssetTransactionKind.BUY, day1, BigDecimal("10"), BigDecimal("100"), 0, 100_000),
                AssetTransaction("sell", "a", AssetTransactionKind.SELL, day2, BigDecimal("5"), BigDecimal("150"), 0, 75_000),
            )
        val position =
            V2WealthPosition(
                asset = AssetEntity("a", "Gold", "GOLD", "21K", "EGP", "g", "0", createdAt = 0, updatedAt = 0),
                quantity = BigDecimal("5"),
                pricePerUnit = BigDecimal("200"),
                priceCurrency = "EGP",
                sourceLabel = "Manual",
                ageHours = 2,
                valueEgpMinor = 100_000,
                costEgpMinor = 50_000,
                unrealizedEgpMinor = 50_000,
                realizedEgpMinor = 25_000,
                weightBasisPoints = 1_500,
            )

        val metrics = V2AssetDetailModels.metrics(position, trades, 100_000, today)
        val expectedXirr =
            Xirr.calculate(
                listOf(
                    XirrCashFlow(day1, BigDecimal("-100000")),
                    XirrCashFlow(day2, BigDecimal("75000")),
                    XirrCashFlow(today, BigDecimal("100000")),
                ),
            )
        assertEquals(BigDecimal("100.0000"), metrics.averageCostEgpPerUnit)
        assertEquals(50_000, metrics.unrealizedProfitEgpMinor)
        assertEquals(25_000, metrics.realizedProfitEgpMinor)
        assertEquals(1_500, metrics.weightBasisPoints)
        assertNotNull(metrics.xirr)
        assertEquals(expectedXirr, metrics.xirr)
    }
}
