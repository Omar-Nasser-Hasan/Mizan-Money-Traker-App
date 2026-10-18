// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.assets

import app.mizan.domain.money.MoneyMath
import app.mizan.domain.stats.Change
import app.mizan.domain.stats.compare
import java.math.BigDecimal
import java.math.RoundingMode

data class WealthCashBucket(
    val amountMinor: Long,
    val currencyDecimals: Int,
    val egpPerUnit: BigDecimal,
)

data class WealthHoldingInput(
    val assetId: String,
    val type: AssetType,
    val name: String,
    val quantity: BigDecimal,
    val pricePerUnit: BigDecimal,
    val currency: String,
    val currencyDecimals: Int,
    val egpPerUnit: BigDecimal,
    val resaleDeductionPct: BigDecimal,
    val costMinor: Long,
    val realizedProfitMinor: Long,
    val source: String,
    val staleHours: Long?,
)

data class WealthHoldingValue(
    val assetId: String,
    val type: AssetType,
    val name: String,
    val quantity: BigDecimal,
    val currency: String,
    val valueEgpMinor: Long,
    val costEgpMinor: Long,
    val unrealizedProfitEgpMinor: Long,
    val realizedProfitEgpMinor: Long,
    val weightBasisPoints: Long,
    val source: String,
    val staleHours: Long?,
)

data class WealthSummary(
    val cashEgpMinor: Long,
    val holdingsEgpMinor: Long,
    val netWorthEgpMinor: Long,
    val investedBasisPoints: Long,
    val typeValuesEgpMinor: Map<AssetType, Long>,
    val holdings: List<WealthHoldingValue>,
    val monthChange: Change?,
)

/** Converts current cash buckets and market-valued positions into the shared Wealth totals. */
object WealthCalculator {
    fun calculate(
        cash: Iterable<WealthCashBucket>,
        holdings: Iterable<WealthHoldingInput>,
        previousNetWorthEgpMinor: Long? = null,
    ): WealthSummary {
        val cashValue =
            cash.fold(0L) { total, bucket ->
                Math.addExact(total, MoneyMath.baseMinor(bucket.amountMinor, bucket.currencyDecimals, bucket.egpPerUnit))
            }
        val valued =
            holdings.map { holding ->
                val valueMinor =
                    AssetValuation.valueMinor(
                        holding.quantity,
                        holding.pricePerUnit,
                        holding.currencyDecimals,
                        holding.resaleDeductionPct,
                    )
                val valueEgp = MoneyMath.baseMinor(valueMinor, holding.currencyDecimals, holding.egpPerUnit)
                val costEgp = MoneyMath.baseMinor(holding.costMinor, holding.currencyDecimals, holding.egpPerUnit)
                val realizedEgp = MoneyMath.baseMinor(holding.realizedProfitMinor, holding.currencyDecimals, holding.egpPerUnit)
                WealthHoldingValue(
                    assetId = holding.assetId,
                    type = holding.type,
                    name = holding.name,
                    quantity = holding.quantity,
                    currency = holding.currency,
                    valueEgpMinor = valueEgp,
                    costEgpMinor = costEgp,
                    unrealizedProfitEgpMinor = Math.subtractExact(valueEgp, costEgp),
                    realizedProfitEgpMinor = realizedEgp,
                    weightBasisPoints = 0L,
                    source = holding.source,
                    staleHours = holding.staleHours,
                )
            }
        val holdingsValue = valued.fold(0L) { total, position -> Math.addExact(total, position.valueEgpMinor) }
        val netWorth = Math.addExact(cashValue, holdingsValue)
        val netWorthDenominator = netWorth.coerceAtLeast(1L)
        val holdingsDenominator = holdingsValue.coerceAtLeast(1L)
        val weighted =
            valued.map { position ->
                position.copy(
                    weightBasisPoints =
                        BigDecimal
                            .valueOf(position.valueEgpMinor)
                            .multiply(BigDecimal(10_000))
                            .divide(BigDecimal.valueOf(holdingsDenominator), 0, RoundingMode.HALF_UP)
                            .longValueExact(),
                )
            }
        val typeTotals =
            weighted.groupBy { it.type }.mapValues { (_, rows) ->
                rows.fold(0L) { total, row -> Math.addExact(total, row.valueEgpMinor) }
            }
        return WealthSummary(
            cashEgpMinor = cashValue,
            holdingsEgpMinor = holdingsValue,
            netWorthEgpMinor = netWorth,
            investedBasisPoints =
                BigDecimal
                    .valueOf(holdingsValue)
                    .multiply(BigDecimal(10_000))
                    .divide(BigDecimal.valueOf(netWorthDenominator), 0, RoundingMode.HALF_UP)
                    .longValueExact(),
            typeValuesEgpMinor = typeTotals,
            holdings = weighted,
            monthChange = previousNetWorthEgpMinor?.let { compare(netWorth, it) },
        )
    }
}
