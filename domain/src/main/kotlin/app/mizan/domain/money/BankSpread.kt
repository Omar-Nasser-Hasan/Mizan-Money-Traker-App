// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.money

import java.math.BigDecimal

/** A bank spread is a cost-side premium over the published mid-market quote. Manual pins are exact. */
fun bankAdjustedRate(
    midMarket: BigDecimal,
    spreadPercent: BigDecimal,
    pin: BigDecimal? = null,
): BigDecimal {
    require(midMarket > BigDecimal.ZERO)
    require(spreadPercent >= BigDecimal.ZERO && spreadPercent <= BigDecimal("100"))
    require(pin == null || pin > BigDecimal.ZERO)
    return pin ?: midMarket.multiply(BigDecimal.ONE.add(spreadPercent.movePointLeft(2)))
}
