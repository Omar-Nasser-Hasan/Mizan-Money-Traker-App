// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import app.mizan.domain.plan.NetWorthMath
import java.time.LocalDate

/** Inputs are already converted to EGP minor units by the data layer. */
data class HomeAccountValue(
    val accountId: String,
    val name: String,
    val egpMinor: Long,
)

data class HomeDebtValue(
    val direction: String,
    val principalMinor: Long,
    val repaymentsMinor: Long,
)

data class HomeSummary(
    val netWorthMinor: Long,
    val cashMinor: Long,
    val holdingsMinor: Long,
    val receivablesMinor: Long,
    val payablesMinor: Long,
    val cashFlow: CashFlow,
    val cashFlowChange: Change,
    val investedMinor: Long,
    val uncategorizedExpenseCount: Int,
    val accountTotals: List<HomeAccountValue>,
    val sparkline: List<Long>,
)

object HomeSummaryCalculator {
    fun calculate(
        records: Iterable<StatRecord>,
        accounts: Iterable<HomeAccountValue>,
        holdingsMinor: Long,
        debts: Iterable<HomeDebtValue>,
        investedMinor: Long,
        month: Period,
        comparison: Period,
        thirtyDays: Period,
    ): HomeSummary {
        val rows = records.filterNot { it.deleted }.toList()
        val totals = accounts.toList()
        val cash = totals.sumOf { it.egpMinor }
        val receivables = debts.filter { it.direction == "LENT" }.sumOf { (it.principalMinor - it.repaymentsMinor).coerceAtLeast(0) }
        val payables = debts.filter { it.direction == "BORROWED" }.sumOf { (it.principalMinor - it.repaymentsMinor).coerceAtLeast(0) }
        val current = StatsEngine.cashFlow(rows, month)
        val prior = StatsEngine.cashFlow(rows, comparison)
        val uncategorized = rows.count { it.kind == StatKind.EXPENSE && it.categoryId == null }
        val openingCash = cash - rows.filter { it.date >= thirtyDays.start }.sumOf { it.baseMinor }
        var runningCash = openingCash
        val trend = generateSequence(thirtyDays.start) { it.plusDays(1).takeIf { next -> next <= thirtyDays.end } }.map { date ->
            runningCash += rows.filter { it.date == date }.sumOf { it.baseMinor }
            NetWorthMath.total(runningCash, holdingsMinor, receivables, payables)
        }.toList()
        return HomeSummary(
            netWorthMinor = NetWorthMath.total(cash, holdingsMinor, receivables, payables),
            cashMinor = cash,
            holdingsMinor = holdingsMinor,
            receivablesMinor = receivables,
            payablesMinor = payables,
            cashFlow = current,
            cashFlowChange = compare(current.netMinor, prior.netMinor, cashFlow = true),
            investedMinor = investedMinor.coerceAtLeast(0L),
            uncategorizedExpenseCount = uncategorized,
            accountTotals = totals.sortedByDescending { it.egpMinor },
            sparkline = trend,
        )
    }
}
