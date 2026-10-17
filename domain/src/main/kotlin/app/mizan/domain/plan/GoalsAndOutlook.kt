// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.plan

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GoalStatus { ACTIVE, PAUSED, REACHED }

data class GoalProgress(
    val targetMinor: Long,
    val linkedMinor: Long,
    val manualMinor: Long,
    val targetDate: LocalDate? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
) {
    val progressMinor: Long get() = Math.addExact(linkedMinor, manualMinor)
    val remainingMinor: Long get() = (targetMinor - progressMinor).coerceAtLeast(0)
    val needsReachedPrompt: Boolean get() = status == GoalStatus.ACTIVE && progressMinor >= targetMinor

    fun isOverdue(today: LocalDate): Boolean = status == GoalStatus.ACTIVE && targetDate?.isBefore(today) == true && remainingMinor > 0

    fun monthlyNeeded(today: LocalDate): Long {
        if (remainingMinor == 0L) return 0L
        val months = targetDate?.let { ChronoUnit.MONTHS.between(today.withDayOfMonth(1), it.withDayOfMonth(1)).coerceAtLeast(1) } ?: 1L
        return BigDecimal.valueOf(remainingMinor).divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING).longValueExact()
    }

    fun eta(
        today: LocalDate,
        averageMonthlyGainMinor: Long,
    ): LocalDate? {
        if (remainingMinor == 0L) return today
        if (averageMonthlyGainMinor <= 0L) return null
        val months =
            BigDecimal
                .valueOf(
                    remainingMinor,
                ).divide(BigDecimal.valueOf(averageMonthlyGainMinor), 0, RoundingMode.CEILING)
                .longValueExact()
        return today.plusMonths(months)
    }
}

object GoalMath {
    /** A source may support several goals, but its combined allocation can never exceed 100%. */
    fun allocationIsValid(
        otherGoalAllocationPct: Long,
        requestedAllocationPct: Long,
    ): Boolean =
        otherGoalAllocationPct in 0..100 &&
            requestedAllocationPct in 0..100 &&
            otherGoalAllocationPct <= 100 - requestedAllocationPct
}

data class ForecastInput(
    val startingMinor: Long,
    val plannedIncomeMinor: Long,
    val plannedExpenseMinor: Long,
    val unplannedDailyMinor: Long,
    val remainingDays: Int,
    val typicalIncomeMinor: Long = 0L,
    val includeTypicalIncome: Boolean = false,
)

data class Forecast(
    val startMinor: Long,
    val plannedMinor: Long,
    val unplannedMinor: Long,
    val expectedIncomeMinor: Long,
    val endMinor: Long,
)

object OutlookMath {
    /** Planned income is counted exactly once; toggling typical income never fabricates it by default. */
    fun forecast(input: ForecastInput): Forecast {
        require(input.remainingDays >= 0 && input.unplannedDailyMinor >= 0)
        val planned = Math.subtractExact(input.plannedIncomeMinor, input.plannedExpenseMinor)
        val unplanned = Math.multiplyExact(input.unplannedDailyMinor, input.remainingDays.toLong())
        val expected = if (input.includeTypicalIncome) input.typicalIncomeMinor else 0L
        val end = Math.addExact(Math.subtractExact(Math.addExact(input.startingMinor, planned), unplanned), expected)
        return Forecast(input.startingMinor, planned, unplanned, expected, end)
    }

    /** Winsorizes daily spending at its 95th percentile before deriving the 90-day daily run rate. */
    fun cappedDailyRunRate(dailyExpenses: List<Long>): Long {
        if (dailyExpenses.isEmpty()) return 0L
        require(dailyExpenses.all { it >= 0 })
        val ordered = dailyExpenses.sorted()
        val cap = ordered[((ordered.size - 1) * 95) / 100]
        return dailyExpenses.sumOf { minOf(it, cap) } / dailyExpenses.size
    }
}

data class CreditCardBalance(
    val limitMinor: Long,
    val balanceMinor: Long,
)

object CreditMath {
    /** Basis points, or null when a card has no usable credit limit. */
    fun perCardUtilization(card: CreditCardBalance): Long? =
        card.limitMinor
            .takeIf { it > 0 }
            ?.let { limit ->
                BigDecimal
                    .valueOf((-card.balanceMinor).coerceAtLeast(0))
                    .multiply(BigDecimal(10_000))
                    .divide(BigDecimal.valueOf(limit), 0, RoundingMode.HALF_UP)
                    .longValueExact()
            }

    fun utilization(cards: Iterable<CreditCardBalance>): Long? {
        val values = cards.toList()
        val limit = values.sumOf { it.limitMinor.coerceAtLeast(0) }
        if (limit == 0L) return null
        val owed = values.sumOf { (-it.balanceMinor).coerceAtLeast(0) }
        return BigDecimal
            .valueOf(
                owed,
            ).multiply(BigDecimal(10_000))
            .divide(BigDecimal.valueOf(limit), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun averageUtilization(series: Iterable<Long?>): Long? {
        val values = series.filterNotNull().toList()
        return values.takeIf { it.isNotEmpty() }?.let { values -> values.sum() / values.size }
    }

    fun maximumUtilization(series: Iterable<Long?>): Long? = series.filterNotNull().maxOrNull()

    fun debtToIncome(
        debtServiceExpenseMinor: Long,
        incomeMinor: Long,
    ): Long? =
        incomeMinor
            .takeIf {
                it > 0
            }?.let {
                BigDecimal
                    .valueOf(
                        debtServiceExpenseMinor,
                    ).multiply(BigDecimal(10_000))
                    .divide(BigDecimal.valueOf(it), 0, RoundingMode.HALF_UP)
                    .longValueExact()
            }
}

object SavingsMath {
    fun savingsRate(
        cashFlowMinor: Long,
        incomeMinor: Long,
    ): Long? =
        incomeMinor
            .takeIf {
                it > 0
            }?.let {
                BigDecimal
                    .valueOf(
                        cashFlowMinor,
                    ).multiply(BigDecimal(10_000))
                    .divide(BigDecimal.valueOf(it), 0, RoundingMode.HALF_UP)
                    .longValueExact()
            }

    fun investRate(
        buysMinor: Long,
        sellsMinor: Long,
        incomeMinor: Long,
    ): Long? =
        incomeMinor
            .takeIf {
                it > 0
            }?.let {
                BigDecimal
                    .valueOf(
                        (buysMinor - sellsMinor).coerceAtLeast(0),
                    ).multiply(BigDecimal(10_000))
                    .divide(BigDecimal.valueOf(it), 0, RoundingMode.HALF_UP)
                    .longValueExact()
            }
}
