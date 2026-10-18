// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.domain.assets.AssetValuation
import java.math.BigDecimal
import java.math.RoundingMode

data class V2AssetTradePreview(
    val grossMinor: Long,
    val cashMinor: Long,
    val currentDealerValueMinor: Long?,
    val marketImpactMinor: Long?,
)

object V2AssetTradeModels {
    fun preview(
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        feeMinor: Long,
        currencyDecimals: Int,
        selling: Boolean,
        todayDealerBuyPrice: BigDecimal?,
        resaleDeductionPct: BigDecimal,
    ): V2AssetTradePreview {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
        require(unitPrice > BigDecimal.ZERO) { "Unit price must be positive" }
        require(feeMinor >= 0L) { "Fee cannot be negative" }
        require(currencyDecimals >= 0)
        val gross =
            quantity
                .multiply(unitPrice)
                .movePointRight(currencyDecimals)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        val cash = if (selling) Math.subtractExact(gross, feeMinor) else Math.addExact(gross, feeMinor)
        require(cash > 0L) { "Net proceeds must remain positive" }
        val currentValue =
            todayDealerBuyPrice?.let {
                AssetValuation.valueMinor(quantity, it, currencyDecimals, resaleDeductionPct)
            }
        return V2AssetTradePreview(
            grossMinor = gross,
            cashMinor = cash,
            currentDealerValueMinor = currentValue,
            marketImpactMinor = currentValue?.let { Math.subtractExact(it, cash) },
        )
    }
}
