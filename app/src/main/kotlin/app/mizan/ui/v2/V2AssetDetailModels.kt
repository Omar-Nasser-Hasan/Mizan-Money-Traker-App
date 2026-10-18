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

object V2AssetDetailModels {
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
