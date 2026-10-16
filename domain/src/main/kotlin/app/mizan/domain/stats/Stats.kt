// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
    val percent = if (previous == 0L || signFlip) null else ((current - previous) * 10_000L / abs(previous)).coerceIn(-99_900L, 99_900L)
    return Change(current, previous, percent, signFlip)
}

object StatPeriods {
    fun previous(period: Period): Period {
        val length = period.days.toLong()
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
}
