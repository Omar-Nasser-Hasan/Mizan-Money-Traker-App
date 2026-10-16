// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/** A monetary value is always stored in minor units. */
data class Money(
    val minor: Long,
    val currency: String,
)

data class CurrencyDefinition(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val enabled: Boolean = true,
)

object MoneyMath {
    private val egpMinorScale = BigDecimal.valueOf(100L)

    fun baseMinor(
        amountMinor: Long,
        currencyDecimals: Int,
        egpPerUnit: BigDecimal,
    ): Long {
        require(currencyDecimals >= 0) { "Currency decimals must be non-negative" }
        require(egpPerUnit > BigDecimal.ZERO) { "Rate must be positive" }
        return BigDecimal
            .valueOf(amountMinor)
            .movePointLeft(currencyDecimals)
            .multiply(egpPerUnit)
            .multiply(egpMinorScale)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun impliedRate(
        sentMinor: Long,
        sentDecimals: Int,
        receivedMinor: Long,
        receivedDecimals: Int,
    ): BigDecimal {
        require(sentMinor > 0 && receivedMinor > 0) { "Transfer amounts must be positive" }
        return BigDecimal
            .valueOf(receivedMinor)
            .movePointLeft(receivedDecimals)
            .divide(
                BigDecimal.valueOf(sentMinor).movePointLeft(sentDecimals),
                12,
                RoundingMode.HALF_UP,
            )
    }
}
