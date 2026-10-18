// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.PlannedOccurrenceEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.db.StatsEntryRow
import app.mizan.data.db.StatsRecordRow
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.CreditCardBalance
import app.mizan.domain.plan.CreditMath
import app.mizan.domain.plan.Forecast
import app.mizan.domain.plan.ForecastInput
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.OutlookMath
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import app.mizan.domain.plan.SavingsMath
import app.mizan.domain.stats.CashFlowReport
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatPeriods
import app.mizan.domain.stats.StatRecord
import app.mizan.domain.stats.StatsEngine
import app.mizan.domain.stats.compare
import java.math.BigDecimal
import java.time.LocalDate

internal data class OutlookTimelineItem(
    val date: LocalDate,
    val name: String,
    val kind: String,
    val signedMinor: Long,
    val balanceAfterMinor: Long,
    val outMinor: Long,
    val inMinor: Long,
)

internal data class OutlookTimelineDay(
    val date: LocalDate,
    val items: List<OutlookTimelineItem>,
    val outMinor: Long,
    val inMinor: Long,
)

internal data class OutlookStats(
    val forecast: Forecast,
    val timeline: List<OutlookTimelineDay>,
    val projectedThrough: LocalDate,
    val unplannedDailyMinor: Long,
) {
    companion object {
        fun create(
            rows: List<StatsRecordRow>,
            balances: List<AccountBalanceRow>,
            accounts: List<AccountEntity>,
            categories: List<CategoryEntity>,
            payments: List<PlannedPaymentEntity>,
            occurrences: List<PlannedOccurrenceEntity>,
            entries: List<StatsEntryRow>,
            currencies: List<CurrencyEntity>,
            rates: List<FxRateEntity>,
            period: Period,
            forecastEnd: LocalDate,
            today: LocalDate,
            accountFilter: String?,
            includeTypicalIncome: Boolean,
        ): OutlookStats {
            val selectedIds = accountFilter?.split('|')?.filter(String::isNotBlank)?.toSet()
            val scopedIds =
                selectedIds ?: accounts.filter { it.includeInTotals && !it.archived }.map { it.id }.toSet()
            val end = maxOf(today, forecastEnd)
            val start = today.plusDays(1)
            val timelineStart = maxOf(start, period.start)
            val applied =
                payments
                    .filter { it.active }
                    .flatMap { payment ->
                        val recurrence =
                            Recurrence(
                                Frequency.valueOf(payment.frequency),
                                payment.interval,
                                LocalDate.parse(payment.startDate),
                                payment.endDate?.let(LocalDate::parse),
                            )
                        RecurrenceEngine.occurrences(recurrence, start, end).map { date ->
                            payment to date
                        }
                    }.filterNot { (payment, date) ->
                        occurrences.any {
                            it.plannedId == payment.id &&
                                it.dueDate == date.toString() &&
                                it.status in setOf("PAID", "SKIPPED")
                        }
                    }.sortedWith(
                        compareBy<Pair<PlannedPaymentEntity, LocalDate>> { it.second }
                            .thenBy { it.first.name },
                    )

            fun baseAmount(payment: PlannedPaymentEntity): Long? {
                val currency = currencies.firstOrNull { it.code == payment.currency } ?: return null
                val rate =
                    if (payment.currency == "EGP") {
                        BigDecimal.ONE
                    } else {
                        rates
                            .filter { it.currency == payment.currency }
                            .maxByOrNull { it.date }
                            ?.egpPerUnit
                            ?.let(::BigDecimal)
                            ?: return null
                    }
                return MoneyMath.baseMinor(payment.amountMinor, currency.decimals, rate)
            }

            fun delta(payment: PlannedPaymentEntity): Long {
                val amount = baseAmount(payment) ?: return 0L
                val fromIncluded = payment.accountId in scopedIds
                val toIncluded = payment.toAccountId in scopedIds
                return when (payment.kind) {
                    "INCOME" -> if (fromIncluded) amount else 0L
                    "EXPENSE" -> if (fromIncluded) -amount else 0L
                    "TRANSFER" -> (if (toIncluded) amount else 0L) - (if (fromIncluded) amount else 0L)
                    else -> 0L
                }
            }

            fun inAmount(payment: PlannedPaymentEntity): Long =
                when (payment.kind) {
                    "INCOME" -> if (payment.accountId in scopedIds) baseAmount(payment) ?: 0L else 0L
                    "TRANSFER" -> if (payment.toAccountId in scopedIds) baseAmount(payment) ?: 0L else 0L
                    else -> 0L
                }

            fun outAmount(payment: PlannedPaymentEntity): Long =
                when (payment.kind) {
                    "EXPENSE" -> if (payment.accountId in scopedIds) baseAmount(payment) ?: 0L else 0L
                    "TRANSFER" -> if (payment.accountId in scopedIds) baseAmount(payment) ?: 0L else 0L
                    else -> 0L
                }

            val upcoming = applied.filter { it.second <= end && baseAmount(it.first) != null }
            val plannedNet = upcoming.sumOf { (payment, _) -> delta(payment) }
            val plannedIn = plannedNet.coerceAtLeast(0L)
            val plannedOut = (-plannedNet).coerceAtLeast(0L)
            val startBalance =
                balances.filter { it.accountId in scopedIds }.sumOf { row ->
                    MoneyMath.baseMinor(
                        row.openingMinor + row.entryMinor,
                        row.decimals,
                        BigDecimal(row.egpPerUnit),
                    )
                }
            val excludedCategories = categories.filter { it.excludeFromForecast }.map { it.id }.toSet()
            val ninetiethDay = today.minusDays(89)
            val dailyExpense =
                rows
                    .filter {
                        it.kind == "EXPENSE" && it.plannedId == null && it.categoryId !in excludedCategories &&
                            LocalDate.parse(it.bookedDate) in ninetiethDay..today && it.accountId in scopedIds
                    }.groupBy { it.bookedDate }
                    .mapValues { (_, records) -> records.sumOf { kotlin.math.abs(it.baseMinor) } }
            val dailySeries =
                (0L..89L).map { offset ->
                    dailyExpense[today.minusDays(89L - offset).toString()] ?: 0L
                }
            val unplannedDaily = OutlookMath.cappedDailyRunRate(dailySeries)
            val statRecords = rows.asStatRecords(accountFilter, entries)
            val monthStart = today.withDayOfMonth(1)
            val typicalPeriods =
                (1L..3L).map { offset ->
                    val month = monthStart.minusMonths(offset)
                    Period(month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth()))
                }
            val typicalIncome =
                if (typicalPeriods.isEmpty()) {
                    0L
                } else {
                    typicalPeriods.sumOf { StatsEngine.cashFlow(statRecords, it).incomeMinor } / typicalPeriods.size
                }
            val forecast =
                OutlookMath.forecast(
                    ForecastInput(
                        startingMinor = startBalance,
                        plannedIncomeMinor = plannedIn,
                        plannedExpenseMinor = plannedOut,
                        unplannedDailyMinor = unplannedDaily,
                        remainingDays =
                            java.time.temporal.ChronoUnit.DAYS
                                .between(today, end)
                                .toInt()
                                .coerceAtLeast(0),
                        typicalIncomeMinor = typicalIncome,
                        includeTypicalIncome = includeTypicalIncome,
                    ),
                )

            var running = startBalance
            val timelineItems =
                applied.filter { it.second <= end && baseAmount(it.first) != null }.mapNotNull { (payment, date) ->
                    running += delta(payment)
                    if (date < timelineStart) {
                        null
                    } else {
                        OutlookTimelineItem(
                            date,
                            payment.name,
                            payment.kind,
                            delta(payment),
                            running,
                            outAmount(payment),
                            inAmount(payment),
                        )
                    }
                }
            val timeline =
                timelineItems.groupBy { it.date }.toSortedMap().map { (date, items) ->
                    OutlookTimelineDay(
                        date,
                        items,
                        items.sumOf { it.outMinor },
                        items.sumOf { it.inMinor },
                    )
                }
            return OutlookStats(forecast, timeline, end, unplannedDaily)
        }
    }
}

internal data class ReportCategory(
    val id: String,
    val name: String,
    val parentId: String?,
    val amountMinor: Long,
    val previousMinor: Long,
    val change: app.mizan.domain.stats.Change,
    val children: List<ReportCategory>,
)

internal data class ReportsStats(
    val report: CashFlowReport,
    val incomeCategories: List<ReportCategory>,
    val expenseCategories: List<ReportCategory>,
    val savingsRate: Long?,
    val investRate: Long?,
    val savingsSeries: List<Long>,
    val investSeries: List<Long>,
    val debtServiceMinor: Long,
    val debtToIncome: Long?,
    val creditUtilization: Long?,
    val hasCreditCards: Boolean,
) {
    companion object {
        fun create(
            rows: List<StatsRecordRow>,
            entries: List<StatsEntryRow>,
            balances: List<AccountBalanceRow>,
            accounts: List<AccountEntity>,
            expenseCategories: List<CategoryEntity>,
            incomeCategories: List<CategoryEntity>,
            period: Period,
            today: LocalDate,
            accountFilter: String?,
        ): ReportsStats {
            val records = rows.asStatRecords(accountFilter, entries)
            val report = StatsEngine.report(records, period)
            val expenses = categoryTree(records, expenseCategories, StatKind.EXPENSE, period, StatPeriods.previous(period))
            val income = categoryTree(records, incomeCategories, StatKind.INCOME, period, StatPeriods.previous(period))
            val incomeMinor = report.cashFlow.incomeMinor
            val net = report.cashFlow.netMinor
            val selectedIds = accountFilter?.split('|')?.filter(String::isNotBlank)?.toSet()
            val entriesByTransaction = entries.groupBy { it.txnId }

            fun selected(row: StatsRecordRow): Boolean =
                selectedIds == null || entriesByTransaction[row.id].orEmpty().any { it.accountId in selectedIds }
            val buys =
                rows
                    .filter {
                        selected(it) && it.kind == "ASSET_BUY" && LocalDate.parse(it.bookedDate) in period.start..period.end
                    }.sumOf { kotlin.math.abs(it.baseMinor) }
            val sells =
                rows
                    .filter {
                        selected(it) && it.kind == "ASSET_SELL" && LocalDate.parse(it.bookedDate) in period.start..period.end
                    }.sumOf { kotlin.math.abs(it.baseMinor) }
            val sixPeriods =
                (5L downTo 0L).map { offset ->
                    val month = today.withDayOfMonth(1).minusMonths(offset)
                    Period(month, month.withDayOfMonth(month.lengthOfMonth()).let { minOf(it, today) })
                }
            val savingsSeries =
                sixPeriods.map { range ->
                    val flow = StatsEngine.cashFlow(records, range)
                    SavingsMath.savingsRate(flow.netMinor, flow.incomeMinor) ?: 0L
                }
            val investSeries =
                sixPeriods.map { range ->
                    val periodBuys =
                        rows
                            .filter {
                                selected(it) && it.kind == "ASSET_BUY" && LocalDate.parse(it.bookedDate) in range.start..range.end
                            }.sumOf { kotlin.math.abs(it.baseMinor) }
                    val periodSells =
                        rows
                            .filter {
                                selected(it) && it.kind == "ASSET_SELL" && LocalDate.parse(it.bookedDate) in range.start..range.end
                            }.sumOf { kotlin.math.abs(it.baseMinor) }
                    val flow = StatsEngine.cashFlow(records, range)
                    SavingsMath.investRate(periodBuys, periodSells, flow.incomeMinor) ?: 0L
                }
            val debtServiceIds = expenseCategories.filter { it.isDebtService }.map { it.id }.toSet()
            val debtService =
                records
                    .filter {
                        it.kind == StatKind.EXPENSE && it.date in period.start..period.end && it.categoryId in debtServiceIds
                    }.sumOf { kotlin.math.abs(it.baseMinor) }
            val cards = accounts.filter { it.type == "CREDIT_CARD" && !it.archived }
            val cardBalances =
                cards.map { account ->
                    CreditCardBalance(
                        account.creditLimitMinor ?: 0L,
                        balances.filter { it.accountId == account.id }.sumOf { row ->
                            MoneyMath.baseMinor(row.openingMinor + row.entryMinor, row.decimals, BigDecimal(row.egpPerUnit))
                        },
                    )
                }
            return ReportsStats(
                report,
                income,
                expenses,
                SavingsMath.savingsRate(net, incomeMinor),
                SavingsMath.investRate(buys, sells, incomeMinor),
                savingsSeries,
                investSeries,
                debtService,
                CreditMath.debtToIncome(debtService, incomeMinor),
                CreditMath.utilization(cardBalances).takeIf { cards.isNotEmpty() },
                cards.isNotEmpty(),
            )
        }

        private fun categoryTree(
            records: List<StatRecord>,
            categories: List<CategoryEntity>,
            kind: StatKind,
            current: Period,
            previous: Period,
        ): List<ReportCategory> {
            fun categoryAmounts(period: Period) =
                records
                    .filter { it.kind == kind && it.date in period.start..period.end }
                    .groupBy { it.categoryId ?: "uncategorized" }
                    .mapValues { (_, values) -> values.sumOf { kotlin.math.abs(it.baseMinor) } }
            val currentById = categoryAmounts(current)
            val previousById = categoryAmounts(previous)
            val childrenByParent = categories.filter { it.parentId != null }.groupBy { it.parentId }
            val visiting = mutableSetOf<String>()

            fun build(category: CategoryEntity): ReportCategory? {
                if (!visiting.add(category.id)) return null
                val children = childrenByParent[category.id].orEmpty().mapNotNull(::build)
                visiting.remove(category.id)
                val amount = (currentById[category.id] ?: 0L) + children.sumOf { it.amountMinor }
                val previousAmount = (previousById[category.id] ?: 0L) + children.sumOf { it.previousMinor }
                if (amount == 0L && previousAmount == 0L) return null
                return ReportCategory(
                    category.id,
                    category.name,
                    category.parentId,
                    amount,
                    previousAmount,
                    compare(amount, previousAmount),
                    children,
                )
            }
            return categories.filter { it.parentId == null }.mapNotNull(::build)
        }
    }
}

internal fun List<StatsRecordRow>.asStatRecords(
    accountFilter: String?,
    entries: List<StatsEntryRow> = emptyList(),
): List<StatRecord> {
    val selected = accountFilter?.split('|')?.filter(String::isNotBlank)?.toSet()
    val byTransaction = entries.groupBy { it.txnId }
    return mapNotNull { row ->
        val chosenEntries = byTransaction[row.id].orEmpty().filter { selected == null || it.accountId in selected }
        if (selected != null && chosenEntries.isEmpty()) return@mapNotNull null
        val amount = if (selected == null) row.baseMinor else chosenEntries.sumOf { it.baseMinor }
        StatRecord(
            row.id,
            LocalDate.parse(row.bookedDate),
            StatKind.fromLedgerKind(row.kind),
            amount,
            chosenEntries.firstOrNull()?.accountId ?: row.accountId,
            row.categoryId,
            row.payeeId,
            row.labels
                .split('|')
                .filter(String::isNotBlank)
                .toSet(),
        )
    }
}
