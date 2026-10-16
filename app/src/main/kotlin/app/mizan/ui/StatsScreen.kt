// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatPeriods
import app.mizan.domain.stats.StatRecord
import app.mizan.domain.stats.StatsEngine
import app.mizan.domain.stats.compare
import app.mizan.ui.charts.ChartPoint
import app.mizan.ui.charts.lineAreaChart
import app.mizan.ui.charts.spendingDonut
import java.time.LocalDate

@Composable
fun statsScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    var tab by remember { mutableStateOf(StatsTab.BALANCE) }
    var range by remember { mutableStateOf(StatsRange.MONTH) }
    val rows by repository.observeStatsRows().collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now()
    val period = range.period(today)
    val records =
        rows.map {
            StatRecord(
                it.id,
                LocalDate.parse(it.bookedDate),
                runCatching { StatKind.valueOf(it.kind) }.getOrDefault(StatKind.OTHER),
                it.baseMinor,
                it.accountId,
                it.categoryId,
                it.payeeId,
            )
        }
    val current = StatsEngine.cashFlow(records, period)
    val previous = StatsEngine.cashFlow(records, StatPeriods.previous(period))
    val netChange = compare(current.netMinor, previous.netMinor, cashFlow = true)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Stats", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { option ->
                    FilterChip(range == option, { range = option }, label = { Text(option.label) })
                }
            }
        }
        item { Text("${period.start} to ${period.end}", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsTab.entries.forEach { option ->
                    FilterChip(tab == option, { tab = option }, label = { Text(option.label) })
                }
            }
        }
        when (tab) {
            StatsTab.BALANCE -> {
                balanceTab(records, period)
            }

            StatsTab.CASH_FLOW -> {
                cashFlowTab(
                    records,
                    period,
                    current.incomeMinor,
                    current.expenseMinor,
                    current.netMinor,
                    netChange.percentBasisPoints,
                    netChange.absoluteChange,
                )
            }

            StatsTab.SPENDING -> {
                spendingTab(records, period)
            }

            StatsTab.REPORTS -> {
                reportsTab(records, period)
            }
        }
    }
}

private fun LazyListScope.balanceTab(
    records: List<StatRecord>,
    period: Period,
) {
    val cumulative = StatsEngine.cumulativeTrend(records, period)
    item { Text("Recorded balance movement", style = MaterialTheme.typography.titleLarge) }
    item {
        lineAreaChart(
            cumulative.map { ChartPoint(it.key.toString(), it.value) },
            { "$it EGP minor" },
            Modifier.fillMaxWidth(),
        )
    }
    item { Text("By account", style = MaterialTheme.typography.titleLarge) }
    items(
        records
            .filter { !it.deleted }
            .groupBy { it.accountId }
            .mapValues { (_, values) ->
                values.sumOf { it.baseMinor }
            }.toList(),
        key = { it.first },
    ) { (account, amount) ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(account)
                amountText(amount, "EGP")
            }
        }
    }
}

private fun LazyListScope.cashFlowTab(
    records: List<StatRecord>,
    period: Period,
    income: Long,
    expenses: Long,
    net: Long,
    percentBasisPoints: Long?,
    absoluteChange: Boolean,
) {
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Income")
                amountText(income, "EGP")
                Text("Expenses")
                amountText(expenses, "EGP")
                Text("Cash flow")
                amountText(net, "EGP")
                Text(
                    when {
                        absoluteChange -> "vs previous period: absolute change"
                        percentBasisPoints == null -> "vs previous period: —"
                        else -> "vs previous period: ${formatPercent(percentBasisPoints)}%"
                    },
                )
            }
        }
    }
    item {
        lineAreaChart(
            StatsEngine.dailyCashFlow(records, period).map { ChartPoint(it.key.toString(), it.value.netMinor) },
            { "$it EGP minor" },
        )
    }
}

private fun LazyListScope.spendingTab(
    records: List<StatRecord>,
    period: Period,
) {
    val categories = StatsEngine.byCategory(records, period).toList().sortedByDescending { it.second }
    item { Text("Spending by category", style = MaterialTheme.typography.titleLarge) }
    item { spendingDonut(categories) }
    item { Text("Top payees", style = MaterialTheme.typography.titleLarge) }
    items(StatsEngine.topPayees(records, period), key = { it.first }) { (payee, amount) -> Text("$payee · $amount EGP minor") }
    item { Text("Largest expenses", style = MaterialTheme.typography.titleLarge) }
    items(StatsEngine.largestExpenses(records, period), key = {
        it.id
    }) { record -> Text("${record.date} · ${record.categoryId ?: "Uncategorized"} · ${-record.baseMinor} EGP minor") }
}

private fun LazyListScope.reportsTab(
    records: List<StatRecord>,
    period: Period,
) {
    val report = StatsEngine.report(records, period)
    item { Text("Cash-flow table", style = MaterialTheme.typography.titleLarge) }
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Income records: ${report.incomeCount} · average/day ${report.averageIncomePerDay} minor")
                Text("Expense records: ${report.expenseCount} · average/day ${report.averageExpensePerDay} minor")
                Text("Net: ${report.cashFlow.netMinor} EGP minor")
            }
        }
    }
    item { Text("Expense book", style = MaterialTheme.typography.titleLarge) }
    items(
        StatsEngine.byCategory(records, period).toList().sortedByDescending {
            it.second
        },
        key = { it.first },
    ) { (category, amount) -> Text("$category · $amount EGP minor") }
}

private enum class StatsTab(
    val label: String,
) {
    BALANCE("Balance"),
    CASH_FLOW("Cash-flow"),
    SPENDING("Spending"),
    REPORTS("Reports"),
}

private enum class StatsRange(
    val label: String,
) {
    MONTH("This month"),
    DAYS_30("30 days"),
    YEAR("This year"),
    ;

    fun period(today: LocalDate): Period =
        when (this) {
            MONTH -> Period(today.withDayOfMonth(1), today)
            DAYS_30 -> Period(today.minusDays(29), today)
            YEAR -> Period(today.withDayOfYear(1), today)
        }
}

private fun formatPercent(basisPoints: Long): String {
    val sign = if (basisPoints < 0) "-" else ""
    val absolute = kotlin.math.abs(basisPoints)
    return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
}
