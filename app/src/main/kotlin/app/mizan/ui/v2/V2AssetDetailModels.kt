// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.Xirr
import app.mizan.domain.assets.XirrCashFlow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class V2AssetDetailMetrics(
    val averageCostEgpPerUnit: BigDecimal,
    val unrealizedProfitEgpMinor: Long,
    val realizedProfitEgpMinor: Long,
    val weightBasisPoints: Long,
    val xirr: BigDecimal?,
)

data class V2AssetHistoryPoint(
    val date: LocalDate,
    val valueMinor: Long,
)

data class V2AssetChartHistory(
    val points: List<V2AssetHistoryPoint>,
    val sourceLabel: String,
)

object V2AssetDetailModels {
    fun chartHistory(
        marketPoints: List<V2AssetHistoryPoint>,
        tradePoints: List<V2AssetHistoryPoint>,
        cutoff: LocalDate,
    ): V2AssetChartHistory {
        val market = marketPoints.filter { it.date >= cutoff }.sortedBy { it.date }
        val trades = tradePoints.filter { it.date >= cutoff }.sortedBy { it.date }
        return when {
            market.size >= 2 -> V2AssetChartHistory(market, "Market prices")
            trades.size >= 2 -> V2AssetChartHistory(trades, "Trade prices · limited market history")
            market.isNotEmpty() -> V2AssetChartHistory(market, "Market prices")
            else -> V2AssetChartHistory(trades, "Trade prices · no market history")
        }
    }

    fun metrics(
        position: V2WealthPosition,
        trades: List<AssetTransaction>,
        currentValueMinor: Long,
        today: LocalDate,
    ): V2AssetDetailMetrics {
        val averageCost =
            if (position.quantity > BigDecimal.ZERO) {
                BigDecimal
                    .valueOf(position.costEgpMinor)
                    .movePointLeft(2)
                    .divide(position.quantity, 4, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        val flows =
            trades.mapNotNull { trade ->
                when (trade.kind) {
                    AssetTransactionKind.BUY -> XirrCashFlow(trade.date, BigDecimal.valueOf(-trade.totalMinor))
                    AssetTransactionKind.SELL, AssetTransactionKind.INCOME -> XirrCashFlow(trade.date, BigDecimal.valueOf(trade.totalMinor))
                }
            } +
                if (position.quantity > BigDecimal.ZERO && currentValueMinor > 0) {
                    listOf(XirrCashFlow(today, BigDecimal.valueOf(currentValueMinor)))
                } else {
                    emptyList()
                }
        return V2AssetDetailMetrics(
            averageCostEgpPerUnit = averageCost,
            unrealizedProfitEgpMinor = position.unrealizedEgpMinor,
            realizedProfitEgpMinor = position.realizedEgpMinor,
            weightBasisPoints = position.weightBasisPoints,
            xirr = Xirr.calculate(flows),
        )
    }
}
