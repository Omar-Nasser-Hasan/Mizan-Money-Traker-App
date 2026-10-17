// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.assets

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AssetType { GOLD, STOCK, FUND, OTHER }

enum class AssetTransactionKind { BUY, SELL, INCOME }

/** The persisted fields are deliberately decimal text, never binary floating point. */
data class Asset(
    val id: String,
    val name: String,
    val type: AssetType,
    val symbolOrKarats: String,
    val quoteCurrency: String,
    val unit: String,
    val resaleDeductionPct: BigDecimal = BigDecimal.ZERO,
    val archived: Boolean = false,
)

data class AssetTransaction(
    val id: String,
    val assetId: String,
    val kind: AssetTransactionKind,
    val date: LocalDate,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val feeMinor: Long,
    /** Cash paid for a buy, or cash received after fees for a sale, in quote-currency minor units. */
    val totalMinor: Long,
    val txnId: String? = null,
    val note: String = "",
)

data class AverageCostPosition(
    val quantity: BigDecimal = BigDecimal.ZERO,
    val costMinor: Long = 0,
    val realizedProfitMinor: Long = 0,
)

object AssetValuation {
    /** Valuation is dealer buy price less the asset-specific resale deduction. */
    fun valueMinor(
        quantity: BigDecimal,
        dealerBuyPrice: BigDecimal,
        currencyDecimals: Int,
        resaleDeductionPct: BigDecimal,
    ): Long {
        require(quantity >= BigDecimal.ZERO && dealerBuyPrice >= BigDecimal.ZERO)
        require(resaleDeductionPct >= BigDecimal.ZERO && resaleDeductionPct <= BigDecimal(100))
        val gross = quantity.multiply(dealerBuyPrice).movePointRight(currencyDecimals).setScale(0, RoundingMode.HALF_UP)
        val deduction = gross.multiply(resaleDeductionPct).divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
        return gross.subtract(deduction).longValueExact()
    }

    fun netWorthMinor(
        cashBaseMinor: Long,
        holdingBaseMinors: Iterable<Long>,
    ): Long = holdingBaseMinors.fold(cashBaseMinor, Math::addExact)
}

/** Moving average cost; sells release cost at the average cost immediately before the sale. */
object AverageCost {
    fun position(transactions: List<AssetTransaction>): AverageCostPosition =
        transactions.sortedWith(compareBy<AssetTransaction> { it.date }.thenBy { it.id }).fold(AverageCostPosition()) { state, txn ->
            require(txn.quantity > BigDecimal.ZERO) { "Asset quantity must be positive" }
            require(txn.totalMinor >= 0) { "Asset total must not be negative" }
            when (txn.kind) {
                AssetTransactionKind.BUY -> {
                    state.copy(
                        quantity = state.quantity.add(txn.quantity),
                        costMinor = Math.addExact(state.costMinor, txn.totalMinor),
                    )
                }

                AssetTransactionKind.SELL -> {
                    require(state.quantity >= txn.quantity) { "Cannot sell more than the holding" }
                    val released =
                        BigDecimal
                            .valueOf(state.costMinor)
                            .multiply(txn.quantity)
                            .divide(state.quantity, 0, RoundingMode.HALF_UP)
                            .longValueExact()
                    state.copy(
                        quantity = state.quantity.subtract(txn.quantity),
                        costMinor = Math.subtractExact(state.costMinor, released),
                        realizedProfitMinor = Math.addExact(state.realizedProfitMinor, Math.subtractExact(txn.totalMinor, released)),
                    )
                }

                AssetTransactionKind.INCOME -> {
                    state.copy(realizedProfitMinor = Math.addExact(state.realizedProfitMinor, txn.totalMinor))
                }
            }
        }
}

data class ResolvedPrice(
    val price: BigDecimal,
    val asOf: LocalDate,
    val source: String,
    val manual: Boolean,
    val stale: Boolean,
    val fallback: Boolean,
)

data class PriceCandidate(
    val price: BigDecimal,
    val asOf: LocalDate,
    val source: String,
    val manual: Boolean,
)

/** Gold fallback order from the spec: dealer, spot-derived, then a manual dealer price. */
object PriceResolver {
    fun resolve(
        dealer: PriceCandidate?,
        spotDerived: PriceCandidate?,
        manual: PriceCandidate?,
        today: LocalDate,
        staleAfterDays: Long,
    ): ResolvedPrice? {
        require(staleAfterDays >= 0)
        val chosen = dealer ?: spotDerived ?: manual ?: return null
        return ResolvedPrice(
            price = chosen.price,
            asOf = chosen.asOf,
            source = chosen.source,
            manual = chosen.manual,
            stale = !chosen.manual && ChronoUnit.DAYS.between(chosen.asOf, today) > staleAfterDays,
            fallback = chosen !== dealer,
        )
    }
}

object GoldRatios {
    private val twentyFourOverTwentyOne = BigDecimal(24).divide(BigDecimal(21), 12, RoundingMode.HALF_UP)
    private val eighteenOverTwentyOne = BigDecimal(18).divide(BigDecimal(21), 12, RoundingMode.HALF_UP)

    fun from21K(price21K: BigDecimal): Map<String, BigDecimal> {
        require(price21K >= BigDecimal.ZERO)
        return mapOf(
            "24K" to price21K.multiply(twentyFourOverTwentyOne),
            "21K" to price21K,
            "18K" to price21K.multiply(eighteenOverTwentyOne),
            "POUND" to price21K.multiply(BigDecimal(8)),
        )
    }
}

data class XirrCashFlow(
    val date: LocalDate,
    val amount: BigDecimal,
)

object Xirr {
    private val mc = MathContext.DECIMAL128
    private val minRate = BigDecimal("-0.999999")
    private val maxRate = BigDecimal("1000")

    /** Bisection is deterministic and robust for normal investment cash flows. */
    fun calculate(cashFlows: List<XirrCashFlow>): BigDecimal? {
        if (cashFlows.size < 2 || cashFlows.none { it.amount < BigDecimal.ZERO } ||
            cashFlows.none { it.amount > BigDecimal.ZERO }
        ) {
            return null
        }
        val ordered = cashFlows.sortedBy { it.date }
        if (ChronoUnit.DAYS.between(ordered.first().date, ordered.last().date) < 30) return null
        var low = minRate
        var high = maxRate
        var lowValue = npv(ordered, low)
        val highValue = npv(ordered, high)
        if (lowValue.signum() == highValue.signum()) return null
        repeat(160) {
            val middle = low.add(high, mc).divide(BigDecimal(2), mc)
            val value = npv(ordered, middle)
            if (value.abs() < BigDecimal("0.00000001")) return middle
            if (value.signum() == lowValue.signum()) {
                low = middle
                lowValue = value
            } else {
                high = middle
            }
        }
        return low.add(high, mc).divide(BigDecimal(2), mc)
    }

    private fun npv(
        cashFlows: List<XirrCashFlow>,
        rate: BigDecimal,
    ): BigDecimal {
        val start = cashFlows.first().date
        return cashFlows.fold(BigDecimal.ZERO) { total, flow ->
            val days = ChronoUnit.DAYS.between(start, flow.date)
            val growth = rationalPower(BigDecimal.ONE.add(rate, mc), days, 365)
            total.add(flow.amount.divide(growth, mc), mc)
        }
    }

    private fun rationalPower(
        base: BigDecimal,
        numerator: Long,
        denominator: Int,
    ): BigDecimal {
        if (numerator == 0L) return BigDecimal.ONE
        val exponent = BigDecimal.valueOf(numerator).divide(BigDecimal(denominator), mc)
        return exponential(naturalLog(base).multiply(exponent, mc))
    }

    /** BigDecimal-only logarithm, using repeated square roots to make the atanh series converge quickly. */
    private fun naturalLog(value: BigDecimal): BigDecimal {
        var reduced = value
        var multiplier = BigDecimal.ONE
        var roots = 0
        while ((reduced <= BigDecimal("0.75") || reduced >= BigDecimal("1.5")) && roots < 32) {
            reduced = squareRoot(reduced)
            multiplier = multiplier.multiply(BigDecimal(2))
            roots++
        }
        val y = reduced.subtract(BigDecimal.ONE, mc).divide(reduced.add(BigDecimal.ONE, mc), mc)
        var term = y
        var sum = y
        var divisor = 3L
        repeat(160) {
            term = term.multiply(y, mc).multiply(y, mc)
            sum = sum.add(term.divide(BigDecimal.valueOf(divisor), mc), mc)
            divisor += 2
        }
        return sum.multiply(BigDecimal(2), mc).multiply(multiplier, mc)
    }

    private fun squareRoot(value: BigDecimal): BigDecimal {
        var guess = if (value > BigDecimal.ONE) value.divide(BigDecimal(2), mc) else BigDecimal.ONE
        repeat(80) { guess = guess.add(value.divide(guess, mc), mc).divide(BigDecimal(2), mc) }
        return guess
    }

    private fun exponential(value: BigDecimal): BigDecimal {
        if (value == BigDecimal.ZERO) return BigDecimal.ONE
        val parts =
            value
                .abs()
                .setScale(0, RoundingMode.CEILING)
                .toInt()
                .coerceAtLeast(1)
        val small = value.divide(BigDecimal(parts), mc)
        var sum = BigDecimal.ONE
        var term = BigDecimal.ONE
        for (index in 1..160) {
            term = term.multiply(small, mc).divide(BigDecimal(index), mc)
            sum = sum.add(term, mc)
        }
        return sum.pow(parts, mc)
    }
}
