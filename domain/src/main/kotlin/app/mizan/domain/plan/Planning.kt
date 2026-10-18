// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.plan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class BudgetScope { ACCOUNT, CATEGORY, ALL }

enum class BudgetPeriod { WEEKLY, MONTHLY, YEARLY, ONE_TIME }

enum class Rollover { NONE, UNDER, BOTH }

enum class PlannedKind { EXPENSE, INCOME, TRANSFER }

enum class Frequency { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

enum class DebtDirection { LENT, BORROWED }

data class BudgetMathInput(
    val limitMinor: Long,
    val spentMinor: Long,
    val priorLimitMinor: Long = 0,
    val priorSpentMinor: Long = 0,
    val rollover: Rollover = Rollover.NONE,
    val plannedRemainingMinor: Long = 0,
    val elapsedDays: Int,
    val remainingDays: Int,
)

data class BudgetMathResult(
    val effectiveLimitMinor: Long,
    val percentBasisPoints: Long,
    val forecastMinor: Long,
)

enum class BudgetStatus { ON_PACE, TRENDING_OVER, OVER }

object BudgetMath {
    fun calculate(input: BudgetMathInput): BudgetMathResult {
        require(input.limitMinor > 0) { "Budget limit must be positive" }
        require(input.spentMinor >= 0 && input.elapsedDays > 0 && input.remainingDays >= 0) { "Budget input is invalid" }
        val difference = input.priorLimitMinor - input.priorSpentMinor
        val carry =
            when (input.rollover) {
                Rollover.NONE -> 0
                Rollover.UNDER -> difference.coerceAtLeast(0)
                Rollover.BOTH -> difference
            }
        val effective = (input.limitMinor + carry).coerceAtLeast(0)
        val percent = if (effective == 0L) 10_000L else input.spentMinor * 10_000L / effective
        val forecast = input.spentMinor + input.plannedRemainingMinor + input.spentMinor / input.elapsedDays * input.remainingDays
        return BudgetMathResult(effective, percent, forecast)
    }

    fun status(
        spentMinor: Long,
        result: BudgetMathResult,
    ): BudgetStatus =
        when {
            spentMinor > result.effectiveLimitMinor -> BudgetStatus.OVER
            result.forecastMinor > result.effectiveLimitMinor -> BudgetStatus.TRENDING_OVER
            else -> BudgetStatus.ON_PACE
        }
}

data class BudgetWindow(
    val start: LocalDate,
    val end: LocalDate,
)

fun budgetWindow(
    period: BudgetPeriod,
    today: LocalDate,
    oneTimeStart: LocalDate? = null,
    oneTimeEnd: LocalDate? = null,
): BudgetWindow =
    when (period) {
        BudgetPeriod.WEEKLY -> BudgetWindow(today.startOfBudgetWeek(), today.startOfBudgetWeek().plusDays(6))
        BudgetPeriod.MONTHLY -> BudgetWindow(today.withDayOfMonth(1), YearMonth.from(today).atEndOfMonth())
        BudgetPeriod.YEARLY -> BudgetWindow(today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31))
        BudgetPeriod.ONE_TIME -> BudgetWindow(requireNotNull(oneTimeStart), requireNotNull(oneTimeEnd))
    }

data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
)

object RecurrenceEngine {
    fun occurrences(
        recurrence: Recurrence,
        from: LocalDate,
        through: LocalDate,
    ): List<LocalDate> {
        require(recurrence.interval > 0) { "Interval must be positive" }
        if (through < from) return emptyList()
        val last = minOf(through, recurrence.endDate ?: through)
        if (last < recurrence.startDate) return emptyList()
        return generateSequence(0L) { it + 1 }
            .map { occurrenceAt(recurrence, it) }
            .takeWhile { it <= last }
            .filter { it >= from }
            .toList()
    }

    private fun occurrenceAt(
        recurrence: Recurrence,
        index: Long,
    ): LocalDate =
        when (recurrence.frequency) {
            Frequency.ONCE -> {
                if (index == 0L) recurrence.startDate else LocalDate.MAX
            }

            Frequency.DAILY -> {
                recurrence.startDate.plusDays(recurrence.interval.toLong() * index)
            }

            Frequency.WEEKLY -> {
                recurrence.startDate.plusWeeks(recurrence.interval.toLong() * index)
            }

            Frequency.MONTHLY -> {
                val target = YearMonth.from(recurrence.startDate).plusMonths(recurrence.interval.toLong() * index)
                target.atDay(minOf(recurrence.startDate.dayOfMonth, target.lengthOfMonth()))
            }

            Frequency.YEARLY -> {
                val target = recurrence.startDate.year + recurrence.interval * index.toInt()
                LocalDate.of(
                    target,
                    recurrence.startDate.month,
                    minOf(recurrence.startDate.dayOfMonth, recurrence.startDate.month.length(java.time.Year.isLeap(target.toLong()))),
                )
            }
        }
}

data class DebtBalance(
    val principalMinor: Long,
    val repaymentsMinor: Long,
) {
    val outstandingMinor: Long get() = (principalMinor - repaymentsMinor).coerceAtLeast(0)
    val isClosed: Boolean get() = outstandingMinor == 0L
}

fun LocalDate.startOfBudgetWeek(): LocalDate {
    val offset = (dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
    return minusDays(offset.toLong())
}
