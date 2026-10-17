// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.zakat

import java.math.BigDecimal
import java.math.RoundingMode

/** Pure EGP-minor-unit Zakat calculation; currency conversion belongs at the data boundary. */
data class ZakatInput(
    val cashMinor: Long,
    val goldMinor: Long,
    val investmentsMinor: Long,
    val receivablesMinor: Long,
    val debtsDueWithinYearMinor: Long,
    val creditCardDebtMinor: Long,
    val gold24kPricePerGramMinor: Long,
    val nisabGrams: BigDecimal = BigDecimal("85"),
    val rate: BigDecimal = BigDecimal("0.025"),
)

data class ZakatResult(
    val eligibleMinor: Long,
    val nisabMinor: Long,
    val isDue: Boolean,
    val dueMinor: Long,
)

object ZakatCalculator {
    fun calculate(input: ZakatInput): ZakatResult {
        require(input.gold24kPricePerGramMinor >= 0) { "Gold price cannot be negative" }
        require(input.nisabGrams.signum() > 0) { "Nisab grams must be positive" }
        require(input.rate.signum() >= 0) { "Rate cannot be negative" }
        val assets = input.cashMinor + input.goldMinor + input.investmentsMinor + input.receivablesMinor
        val liabilities = input.debtsDueWithinYearMinor + input.creditCardDebtMinor
        val eligible = (assets - liabilities).coerceAtLeast(0)
        val nisab =
            input.nisabGrams
                .multiply(
                    BigDecimal.valueOf(input.gold24kPricePerGramMinor),
                ).setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        val due =
            if (eligible >=
                nisab
            ) {
                BigDecimal
                    .valueOf(eligible)
                    .multiply(input.rate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact()
            } else {
                0L
            }
        return ZakatResult(eligible, nisab, due > 0, due)
    }
}
