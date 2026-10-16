// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.math.BigInteger
import kotlin.math.abs

enum class StatKind { INCOME, ASSET_INCOME, EXPENSE, TRANSFER, OTHER }

data class StatRecord(
    val id: String,
    val date: LocalDate,
    val kind: StatKind,
    val baseMinor: Long,
    val accountId: String,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val labels: Set<String> = emptySet(),
    val deleted: Boolean = false,
)

data class Period(
    val start: LocalDate,
    val end: LocalDate,
) {
    fun through(today: LocalDate): Period = copy(end = minOf(end, today))

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
}

data class Change(
    val currentMinor: Long,
    val previousMinor: Long,
    val percentBasisPoints: Long?,
    val absoluteChange: Boolean,
)

fun compare(
    current: Long,
    previous: Long,
    cashFlow: Boolean = false,
): Change {
    val signFlip = cashFlow && current != 0L && previous != 0L && (current < 0) != (previous < 0)
    val percent =
        if (previous == 0L || signFlip) {
            null
        } else {
            BigInteger
                .valueOf(current)
                .subtract(BigInteger.valueOf(previous))
                .multiply(BigInteger.valueOf(10_000L))
                .divide(BigInteger.valueOf(previous).abs())
                .coerceToLong()
                .coerceIn(-99_900L, 99_900L)
        }
    return Change(current, previous, percent, signFlip)
}

object StatPeriods {
    fun previous(period: Period): Period {
        val length = period.days.toLong()
        if (period.start.dayOfMonth == 1) {
            val previousMonth = period.start.minusMonths(1).withDayOfMonth(1)
            return Period(previousMonth, minOf(previousMonth.plusDays(length - 1), previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())))
        }
        val end = period.start.minusDays(1)
        return Period(end.minusDays(length - 1), end)
    }

    fun lastYear(period: Period): Period = Period(period.start.minusYears(1), period.end.minusYears(1))
}

data class CashFlow(
    val incomeMinor: Long,
    val expenseMinor: Long,
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

data class CashFlowReport(
    val cashFlow: CashFlow,
    val incomeCount: Int,
    val expenseCount: Int,
    val elapsedDays: Int,
) {
    val averageIncomePerDay: Long get() = cashFlow.incomeMinor / elapsedDays.coerceAtLeast(1)

    val averageExpensePerDay: Long get() = cashFlow.expenseMinor / elapsedDays.coerceAtLeast(1)

    val averageIncomePerRecord: Long get() = cashFlow.incomeMinor / incomeCount.coerceAtLeast(1)

    val averageExpensePerRecord: Long get() = cashFlow.expenseMinor / expenseCount.coerceAtLeast(1)
}

data class CreditUtilization(
    val owedMinor: Long,
    val limitMinor: Long,
) {
    val basisPoints: Long? get() = limitMinor.takeIf { it > 0 }?.let { owedMinor * 10_000L / it }
}

object StatsEngine {
    fun cashFlow(
        records: Iterable<StatRecord>,
        period: Period,
    ): CashFlow {
        val valid = records.filter { !it.deleted && it.date in period.start..period.end }
        return CashFlow(
            valid.filter { it.kind == StatKind.INCOME || it.kind == StatKind.ASSET_INCOME }.sumOf { it.baseMinor },
            valid
                .filter {
                    it.kind ==
                        StatKind.EXPENSE
                }.sumOf { abs(it.baseMinor) },
        )
    }

    fun dailyCashFlow(
        records: Iterable<StatRecord>,
        period: Period,
    ): Map<LocalDate, CashFlow> =
        generateSequence(period.start) {
            it.plusDays(1).takeIf { next ->
                next <= period.end
            }
        }.associateWith { cashFlow(records, Period(it, it)) }

    fun byCategory(
        records: Iterable<StatRecord>,
        period: Period,
    ): Map<String, Long> =
        records
            .filter { !it.deleted && it.kind == StatKind.EXPENSE && it.date in period.start..period.end }
            .groupBy {
                it.categoryId
                    ?: "uncategorized"
            }.mapValues { (_, rows) -> rows.sumOf { abs(it.baseMinor) } }

    fun byLabel(
        records: Iterable<StatRecord>,
        period: Period,
    ): Map<String, Long> =
        records
            .filter { !it.deleted && it.kind == StatKind.EXPENSE && it.date in period.start..period.end }
            .flatMap { record ->
                record.labels.map {
                    it to
                        abs(record.baseMinor)
                }
            }.groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }

    fun topPayees(
        records: Iterable<StatRecord>,
        period: Period,
    ): List<Pair<String, Long>> =
        records
            .filter {
                !it.deleted && it.kind == StatKind.EXPENSE && it.date in period.start..period.end && it.payeeId != null
            }.groupBy { it.payeeId!! }
            .mapValues {
                it.value.sumOf { row ->
                    abs(row.baseMinor)
                }
            }.toList()
            .sortedByDescending { it.second }
            .take(10)

    fun report(
        records: Iterable<StatRecord>,
        period: Period,
    ): CashFlowReport {
        val valid = records.filter { !it.deleted && it.date in period.start..period.end }
        val income = valid.filter { it.kind == StatKind.INCOME || it.kind == StatKind.ASSET_INCOME }
        val expense = valid.filter { it.kind == StatKind.EXPENSE }
        return CashFlowReport(
            cashFlow = CashFlow(income.sumOf { it.baseMinor }, expense.sumOf { abs(it.baseMinor) }),
            incomeCount = income.size,
            expenseCount = expense.size,
            elapsedDays = period.days,
        )
    }

    fun cumulativeTrend(
        records: Iterable<StatRecord>,
        period: Period,
    ): Map<LocalDate, Long> {
        var cumulative = 0L
        return dailyCashFlow(records, period).mapValues { (_, value) ->
            cumulative += value.netMinor
            cumulative
        }
    }

    fun weekdayProfile(
        records: Iterable<StatRecord>,
        through: LocalDate,
    ): Map<java.time.DayOfWeek, Long> {
        val from = through.minusDays(89)
        return records
            .filter { !it.deleted && it.kind == StatKind.EXPENSE && it.date in from..through }
            .groupBy { it.date.dayOfWeek }
            .mapValues { (_, values) -> values.sumOf { abs(it.baseMinor) } / 13L }
    }

    fun largestExpenses(
        records: Iterable<StatRecord>,
        period: Period,
    ): List<StatRecord> =
        records
            .filter { !it.deleted && it.kind == StatKind.EXPENSE && it.date in period.start..period.end }
            .sortedByDescending { abs(it.baseMinor) }
            .take(10)
}

private fun BigInteger.coerceToLong(): Long =
    when {
        this > BigInteger.valueOf(Long.MAX_VALUE) -> Long.MAX_VALUE
        this < BigInteger.valueOf(Long.MIN_VALUE) -> Long.MIN_VALUE
        else -> toLong()
    }
