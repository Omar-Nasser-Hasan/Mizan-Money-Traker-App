// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.io.StatsImageExport
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun statsScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    var tab by remember { mutableStateOf(StatsTab.BALANCE) }
    var range by remember { mutableStateOf(StatsRange.MONTH) }
    var comparison by remember { mutableStateOf(Comparison.PREVIOUS) }
    val rows by repository.observeStatsRows().collectAsStateWithLifecycle(emptyList())
    val balances by repository.observeAccountBalances().collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val period = range.period(today)
    val comparisonPeriod = if (comparison == Comparison.PREVIOUS) StatPeriods.previous(period) else StatPeriods.lastYear(period)
    val data by produceState(StatsUiData.empty(period), rows, balances, period, comparisonPeriod, comparison) {
        value = withContext(Dispatchers.Default) { StatsUiData.from(rows, balances, period, comparisonPeriod, comparison.label) }
    }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                Comparison.entries.forEach { option ->
                    FilterChip(comparison == option, { comparison = option }, label = { Text(option.label) })
                }
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        val file =
                            StatsImageExport.write(
                                context,
                                "${tab.label} · ${period.start} to ${period.end}",
                                listOf(
                                    "Income: ${data.current.incomeMinor} EGP minor",
                                    "Expenses: ${data.current.expenseMinor} EGP minor",
                                    "Cash flow: ${data.current.netMinor} EGP minor",
                                ),
                            )
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
                        context.startActivity(
                            Intent(Intent.ACTION_SEND)
                                .setType("image/png")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }
                },
            ) { Text("Share as image") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsTab.entries.forEach { option ->
                    FilterChip(tab == option, { tab = option }, label = { Text(option.label) })
                }
            }
        }
        when (tab) {
            StatsTab.BALANCE -> {
                balanceTab(data)
            }

            StatsTab.CASH_FLOW -> {
                cashFlowTab(
                    data,
                )
            }

            StatsTab.SPENDING -> {
                spendingTab(data)
            }

            StatsTab.REPORTS -> {
                reportsTab(data)
            }
        }
    }
}

private fun LazyListScope.balanceTab(data: StatsUiData) {
    item { Text("Balance trend", style = MaterialTheme.typography.titleLarge) }
    item {
        lineAreaChart(
            data.balanceTrend.map { ChartPoint(it.key.toString(), it.value) },
            { "$it EGP minor" },
            Modifier.fillMaxWidth(),
        )
    }
    item { Text("By account", style = MaterialTheme.typography.titleLarge) }
    items(
        data.accountTotals,
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

private fun LazyListScope.cashFlowTab(data: StatsUiData) {
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Income")
                amountText(data.current.incomeMinor, "EGP")
                Text("Expenses")
                amountText(data.current.expenseMinor, "EGP")
                Text("Cash flow")
                amountText(data.current.netMinor, "EGP")
                Text(
                    when {
                        data.change.absoluteChange -> "vs ${data.comparisonLabel}: absolute change"
                        data.change.percentBasisPoints == null -> "vs ${data.comparisonLabel}: —"
                        else -> "vs ${data.comparisonLabel}: ${formatPercent(requireNotNull(data.change.percentBasisPoints))}%"
                    },
                )
            }
        }
    }
    item {
        lineAreaChart(
            data.cashFlowTrend.map { ChartPoint(it.key.toString(), it.value) },
            { "$it EGP minor" },
        )
    }
}

private fun LazyListScope.spendingTab(data: StatsUiData) {
    item { Text("Spending by category", style = MaterialTheme.typography.titleLarge) }
    item { spendingDonut(data.categories) }
    item { Text("Spending by label", style = MaterialTheme.typography.titleLarge) }
    item { spendingDonut(data.labels) }
    item { Text("Labels overlap: a record with several labels counts fully in each label.") }
    item { Text("Top payees", style = MaterialTheme.typography.titleLarge) }
    items(data.topPayees, key = { it.first }) { (payee, amount) -> Text("$payee · $amount EGP minor") }
    item { Text("Largest expenses", style = MaterialTheme.typography.titleLarge) }
    items(data.largestExpenses, key = {
        it.id
    }) { record -> Text("${record.date} · ${record.categoryId ?: "Uncategorized"} · ${-record.baseMinor} EGP minor") }
}

private fun LazyListScope.reportsTab(data: StatsUiData) {
    item { Text("Cash-flow table", style = MaterialTheme.typography.titleLarge) }
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Income records: ${data.report.incomeCount} · average/day ${data.report.averageIncomePerDay} minor")
                Text("Expense records: ${data.report.expenseCount} · average/day ${data.report.averageExpensePerDay} minor")
                Text("Net: ${data.report.cashFlow.netMinor} EGP minor")
            }
        }
    }
    item { Text("Expense book", style = MaterialTheme.typography.titleLarge) }
    items(
        data.categories,
        key = { it.first },
    ) { (category, amount) -> Text("$category · $amount EGP minor") }
}

private data class StatsUiData(
    val current: app.mizan.domain.stats.CashFlow,
    val change: app.mizan.domain.stats.Change,
    val comparisonLabel: String,
    val balanceTrend: Map<LocalDate, Long>,
    val accountTotals: List<Pair<String, Long>>,
    val cashFlowTrend: Map<LocalDate, Long>,
    val categories: List<Pair<String, Long>>,
    val labels: List<Pair<String, Long>>,
    val topPayees: List<Pair<String, Long>>,
    val largestExpenses: List<StatRecord>,
    val report: app.mizan.domain.stats.CashFlowReport,
) {
    companion object {
        fun empty(period: Period) = from(emptyList(), emptyList(), period, StatPeriods.previous(period), "Previous period")

        fun from(
            rows: List<app.mizan.data.db.StatsRecordRow>,
            balanceRows: List<app.mizan.data.db.AccountBalanceRow>,
            period: Period,
            comparisonPeriod: Period,
            comparisonLabel: String,
        ): StatsUiData {
            val records =
                rows.map {
                    StatRecord(
                        it.id,
                        LocalDate.parse(it.bookedDate),
                        runCatching {
                            StatKind.valueOf(it.kind)
                        }.getOrDefault(
                            StatKind.OTHER,
                        ),
                        it.baseMinor,
                        it.accountId,
                        it.categoryId,
                        it.payeeId,
                        it.labels
                            .split('|')
                            .filter(String::isNotBlank)
                            .toSet(),
                    )
                }
            val current = StatsEngine.cashFlow(records, period)
            val accountTotals =
                balanceRows.groupBy { it.accountName }.map { (name, buckets) ->
                    name to
                        buckets.sumOf { bucket ->
                            app.mizan.domain.money.MoneyMath.baseMinor(
                                bucket.openingMinor + bucket.entryMinor,
                                bucket.decimals,
                                BigDecimal(bucket.egpPerUnit),
                            )
                        }
                }
            val opening =
                balanceRows.sumOf {
                    app.mizan.domain.money.MoneyMath
                        .baseMinor(it.openingMinor, it.decimals, BigDecimal(it.openingEgpPerUnit))
                }
            var balance = opening + records.filter { it.date < period.start }.sumOf { it.baseMinor }
            val balanceTrend =
                generateSequence(period.start) { it.plusDays(1).takeIf { next -> next <= period.end } }.associateWith { date ->
                    balance += records.filter { it.date == date }.sumOf { it.baseMinor }
                    balance
                }
            return StatsUiData(
                current,
                compare(current.netMinor, StatsEngine.cashFlow(records, comparisonPeriod).netMinor, cashFlow = true),
                comparisonLabel,
                balanceTrend,
                accountTotals.sortedByDescending { it.second },
                StatsEngine.dailyCashFlow(records, period).mapValues { it.value.netMinor },
                StatsEngine.byCategory(records, period).toList().sortedByDescending { it.second },
                StatsEngine.byLabel(records, period).toList().sortedByDescending { it.second },
                StatsEngine.topPayees(records, period),
                StatsEngine.largestExpenses(records, period),
                StatsEngine.report(records, period),
            )
        }
    }
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

private enum class Comparison(
    val label: String,
) {
    PREVIOUS("Previous period"),
    LAST_YEAR("Last year"),
}

private fun formatPercent(basisPoints: Long): String {
    val sign = if (basisPoints < 0) "-" else ""
    val absolute = kotlin.math.abs(basisPoints)
    return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
}
