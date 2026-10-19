// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.DebtRepaymentRow
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.data.db.StatsEntryRow
import app.mizan.data.db.StatsRecordRow
import app.mizan.data.io.StatsImageExport
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetValuation
import app.mizan.domain.assets.AverageCost
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.NetWorthMath
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatPeriods
import app.mizan.domain.stats.StatRecord
import app.mizan.domain.stats.StatsEngine
import app.mizan.domain.stats.compare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private enum class StatsPage(
    val title: String,
) {
    CASH_FLOW("Cash flow"),
    SPENDING("Spending"),
    BALANCE("Balance"),
    OUTLOOK("Outlook"),
    REPORTS("Reports"),
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun V2StatsScreen(
    repository: MizanRepository,
    periodState: PeriodState,
    accountFilterId: String?,
    hideAmounts: Boolean,
    selectedPage: String,
    onPageSelected: (String) -> Unit,
    onOpenPeriod: () -> Unit,
    onAccountSelected: (String?) -> Unit,
    onGoDeeper: (String) -> Unit,
) {
    val page = StatsPage.entries.firstOrNull { it.title == selectedPage } ?: StatsPage.BALANCE
    val period = remember(periodState) { periodState.toStatsPeriod() }
    val rows by remember(repository) { repository.observeStatsRows() }.collectAsStateWithLifecycle(emptyList())
    val entries by remember(repository) { repository.observeStatsEntries() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAllAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val rates by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember(repository) { repository.observeCategories("EXPENSE") }.collectAsStateWithLifecycle(emptyList())
    val labels by remember(repository) { repository.observeLabels() }.collectAsStateWithLifecycle(emptyList())
    val payees by remember(repository) { repository.observePayees() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val trades by remember(repository) { repository.observeAllAssetTransactions() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observeLatestPrices() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val debts by remember(repository) { repository.observeAllDebts() }.collectAsStateWithLifecycle(emptyList())
    val debtRepaymentRows by remember(repository) { repository.observeDebtRepaymentRows() }.collectAsStateWithLifecycle(emptyList())
    val debtRateHistory by remember(repository) { repository.observeFxHistory() }.collectAsStateWithLifecycle(emptyList())
    val debtRepayments by remember(repository) { repository.observeDebtRepaymentTotals() }.collectAsStateWithLifecycle(emptyList())
    val plannedPayments by remember(repository) { repository.observePlannedPayments() }.collectAsStateWithLifecycle(emptyList())
    val plannedOccurrences by remember(repository) { repository.observePlannedOccurrences() }.collectAsStateWithLifecycle(emptyList())
    val incomeCategories by remember(repository) { repository.observeCategories("INCOME") }.collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val selectedPeriod = period.through(today)
    val outlookEnd = periodState.outlookEnd(today)
    val previous = StatPeriods.previous(selectedPeriod).through(today)
    val lastYear = StatPeriods.lastYear(selectedPeriod).through(today)
    val inputKey = "${rows.size}-${entries.size}-${accounts.size}-${balances.size}"
    var snapshotKey by remember { mutableStateOf<String?>(null) }
    var outlookKey by remember { mutableStateOf<String?>(null) }
    var reportsKey by remember { mutableStateOf<String?>(null) }
    var snapshotReady by remember { mutableStateOf(false) }
    val snapshot by produceState(
        StatsSnapshot.empty(),
        page != StatsPage.OUTLOOK && page != StatsPage.REPORTS,
        rows,
        entries,
        balances,
        rates,
        accounts,
        categories,
        labels,
        payees,
        assets,
        trades,
        prices,
        currencies,
        debts,
        debtRepaymentRows,
        debtRateHistory,
        selectedPeriod,
        previous,
        lastYear,
        accountFilterId,
    ) {
        snapshotReady = false
        if (page == StatsPage.OUTLOOK || page == StatsPage.REPORTS) return@produceState
        value =
            withContext(Dispatchers.Default) {
                StatsSnapshot.create(
                    rows,
                    entries,
                    balances,
                    rates,
                    accounts,
                    categories,
                    labels,
                    payees,
                    assets,
                    trades,
                    prices,
                    currencies,
                    debts,
                    selectedPeriod,
                    previous,
                    lastYear,
                    accountFilterId,
                    debtRepaymentRows,
                    debtRateHistory,
                )
            }
        snapshotKey = inputKey
        snapshotReady = true
    }
    val context = LocalContext.current
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    val tokens = LocalMzTokens.current
    var includeTypicalIncome by rememberSaveable { mutableStateOf(false) }
    val outlook by produceState<OutlookStats?>(
        null,
        page,
        rows,
        balances,
        accounts,
        categories,
        plannedPayments,
        plannedOccurrences,
        debts,
        debtRepayments,
        entries,
        currencies,
        rates,
        selectedPeriod,
        outlookEnd,
        today,
        accountFilterId,
        includeTypicalIncome,
    ) {
        value = null
        if (page != StatsPage.OUTLOOK) return@produceState
        value =
            withContext(Dispatchers.Default) {
                OutlookStats.create(
                    rows,
                    balances,
                    accounts,
                    categories,
                    plannedPayments,
                    plannedOccurrences,
                    entries,
                    currencies,
                    rates,
                    selectedPeriod,
                    outlookEnd,
                    today,
                    accountFilterId,
                    includeTypicalIncome,
                    debts,
                    debtRepayments,
                )
            }
        outlookKey = inputKey
    }
    val reports by produceState<ReportsStats?>(
        null,
        page,
        rows,
        entries,
        balances,
        accounts,
        categories,
        incomeCategories,
        selectedPeriod,
        today,
        accountFilterId,
    ) {
        value = null
        if (page != StatsPage.REPORTS) return@produceState
        value =
            withContext(Dispatchers.Default) {
                ReportsStats.create(rows, entries, balances, accounts, categories, incomeCategories, selectedPeriod, today, accountFilterId)
            }
        reportsKey = inputKey
    }
    val share: (String, androidx.compose.ui.geometry.Rect) -> Unit = { title, bounds ->
        scope.launch { shareCard(context, hostView, title, bounds) }
    }

    val ready =
        when (page) {
            StatsPage.OUTLOOK -> outlook != null && outlookKey == inputKey
            StatsPage.REPORTS -> reports != null && reportsKey == inputKey
            else -> snapshotReady && snapshotKey == inputKey
        }
    Column(
        Modifier
            .fillMaxSize()
            .background(
                tokens.canvas,
            ).then(if (ready) Modifier.testTag("stats-ready-${page.title}-$inputKey") else Modifier),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatsPage.entries.forEach { item ->
                Column(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(role = Role.Tab) { onPageSelected(item.title) }
                        .semantics { contentDescription = item.title },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(item.title, color = if (page == item) tokens.champagne else tokens.textDim, style = MzTypography.bodySmall)
                    if (page == item) Spacer(Modifier.padding(top = 4.dp).size(width = 28.dp, height = 2.dp).background(tokens.champagne))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MzChip(label = "${selectedPeriod.start} – ${selectedPeriod.end}", selected = false, onClick = onOpenPeriod)
            AccountFilter(accounts, accountFilterId, onAccountSelected, Modifier.weight(1f))
        }
        when (page) {
            StatsPage.CASH_FLOW -> {
                CashFlowPage(snapshot, hideAmounts, share)
            }

            StatsPage.SPENDING -> {
                SpendingPage(snapshot, hideAmounts, onGoDeeper, share)
            }

            StatsPage.BALANCE -> {
                BalancePage(
                    snapshot,
                    hideAmounts,
                    share,
                )
            }

            StatsPage.OUTLOOK -> {
                outlook?.let { data ->
                    V2StatsOutlookPage(data, hideAmounts, includeTypicalIncome, { includeTypicalIncome = it }, share)
                } ?: StatsLoadingPage("Loading forecast")
            }

            StatsPage.REPORTS -> {
                reports?.let { data -> V2StatsReportsPage(data, hideAmounts, onGoDeeper, share) }
                    ?: StatsLoadingPage("Loading reports")
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AccountFilter(
    accounts: List<AccountEntity>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = accounts.firstOrNull { it.id == selected }?.name ?: "All accounts"
    Box(modifier) {
        MzChip(label = label, selected = false, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All accounts") }, onClick = {
                onSelected(null)
                expanded = false
            })
            accounts.filterNot { it.archived }.forEach { account ->
                DropdownMenuItem(text = { Text(account.name) }, onClick = {
                    onSelected(account.id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun CashFlowPage(
    snapshot: StatsSnapshot,
    hide: Boolean,
    share: (String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    var cumulative by remember { mutableStateOf(false) }
    var metric by remember { mutableStateOf("Cash flow") }
    var compareLastYear by remember { mutableStateOf(false) }
    val tokens = LocalMzTokens.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShareableCard("Cash flow", share) {
                Text("Am I spending less than I make?", color = tokens.textDim, style = MzTypography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (hide) "••••••" else signedAmount(snapshot.current.netMinor),
                    color = tokens.income,
                    style = MzTypography.amountLarge,
                )
                Text(changeText(snapshot.cashFlowChange), color = tokens.income, style = MzTypography.bodySmall)
                Spacer(Modifier.height(14.dp))
                MetricBar("Income", snapshot.current.incomeMinor, snapshot.current.expenseMinor, tokens.income, hide)
                MetricBar("Expenses", snapshot.current.expenseMinor, snapshot.current.incomeMinor, tokens.expense, hide)
            }
        }
        item {
            ShareableCard("Cash flow trend", share) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MzChip("Trend", !cumulative, { cumulative = false })
                    MzChip("Cumulative", cumulative, { cumulative = true })
                }
                Spacer(Modifier.height(8.dp))
                MzBarChart(
                    income = snapshot.daily.map { it.second.incomeMinor },
                    expense = snapshot.daily.map { it.second.expenseMinor },
                    modifier = Modifier.fillMaxWidth().height(155.dp),
                )
                MzAreaChart(
                    if (cumulative) snapshot.cumulative else snapshot.daily.map { it.second.netMinor },
                    Modifier.fillMaxWidth().height(110.dp),
                )
                Text("Day index · 1 to ${snapshot.daily.size}", color = tokens.textDim, style = MzTypography.bodySmall)
            }
        }
        item {
            ShareableCard("Period comparison", share) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash flow", "Expenses", "Income").forEach { MzChip(it, metric == it, { metric = it }) }
                }
                MzMultiLine(
                    current = snapshot.series(metric)[0],
                    previous = snapshot.series(metric)[1],
                    lastYear = if (compareLastYear) snapshot.series(metric)[2] else null,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
                Text("Current · Previous · Last year (day index)", color = tokens.textDim, style = MzTypography.bodySmall)
                MzToggle(compareLastYear, { compareLastYear = it }, "Compare with last year")
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun SpendingPage(
    snapshot: StatsSnapshot,
    hide: Boolean,
    goDeeper: (String) -> Unit,
    share: (String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    var labelsMode by remember { mutableStateOf(false) }
    var selectedSlice by remember { mutableStateOf<String?>(null) }
    val tokens = LocalMzTokens.current
    val values = if (labelsMode) snapshot.labels else snapshot.categories
    val visible = selectedSlice?.let { selected -> values.filter { it.first == selected } } ?: values
    val total = visible.sumOf { it.second }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShareableCard("Spending", share) {
                Text("Where does my money go?", color = tokens.textDim, style = MzTypography.bodySmall)
                Text(if (hide) "••••••" else amount(snapshot.current.expenseMinor), color = tokens.text, style = MzTypography.amountLarge)
                Text(changeText(snapshot.expenseChange), color = tokens.expense, style = MzTypography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MzChip("Categories", !labelsMode, {
                        labelsMode = false
                        selectedSlice = null
                    })
                    MzChip("Labels", labelsMode, {
                        labelsMode = true
                        selectedSlice = null
                    })
                }
                val slices =
                    visible.mapIndexed { index, row ->
                        MzDonutSlice(
                            if (labelsMode) snapshot.labelNames[row.first] ?: row.first else snapshot.categoryNames[row.first] ?: row.first,
                            row.second,
                            listOf(tokens.champagne, tokens.income, tokens.transfer, Color(0xffA67BD8), tokens.expense, Color(0xffd17842))[
                                index %
                                    6,
                            ],
                        )
                    }
                MzDonut(
                    slices,
                    centerText = if (hide) "••••" else amount(total),
                    changeText = "EGP TOTAL",
                    selectedIndex =
                        selectedSlice?.let { key ->
                            visible
                                .indexOfFirst {
                                    it.first ==
                                        key
                                }.takeIf { it >= 0 }
                        },
                    onSliceSelected = { index ->
                        val key = visible.getOrNull(index)?.first
                        selectedSlice =
                            if (selectedSlice == key) null else key
                    },
                )
                if (labelsMode) {
                    Text(
                        "Labels overlap: records with multiple labels count fully in each.",
                        color = tokens.textDim,
                        style = MzTypography.bodySmall,
                    )
                }
            }
        }
        items(visible, key = { it.first }) { (name, value) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedSlice =
                            if (selectedSlice ==
                                name
                            ) {
                                null
                            } else {
                                name
                            }
                    }.padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (labelsMode) snapshot.labelNames[name] ?: name else snapshot.categoryNames[name] ?: name,
                        Modifier.weight(1f),
                        style = MzTypography.body,
                    )
                    Text(if (hide) "••••" else amount(value), style = MzTypography.amountSmall)
                    Text(
                        "  ${changeText(if (labelsMode) snapshot.labelChanges[name] else snapshot.categoryChanges[name])}",
                        color = tokens.textDim,
                        style = MzTypography.bodySmall,
                    )
                }
                Text(
                    "${sharePercent(value, snapshot.current.expenseMinor)}% of spending",
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
            }
        }
        item {
            ShareableCard("Top payees", share) {
                snapshot.payees.forEach {
                    Text(
                        "${snapshot.payeeNames[it.first] ?: it.first} · ${if (hide) "••••" else amount(it.second)}",
                        style = MzTypography.body,
                    )
                }
            }
        }
        item {
            ShareableCard("Weekday profile", share) {
                val max = snapshot.weekdays.maxOfOrNull { it.second } ?: 0L
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    snapshot.weekdays.forEach { (day, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(
                                Modifier.size(22.dp, ((72L * value / max.coerceAtLeast(1L)).toInt()).dp).background(
                                    if (value ==
                                        max
                                    ) {
                                        tokens.champagne
                                    } else {
                                        tokens.raisedSurface
                                    },
                                ),
                            )
                            Text(day.name.take(3), color = tokens.textDim, style = MzTypography.labelCaps)
                        }
                    }
                }
                Text(
                    "Peak: ${snapshot.weekdays.maxByOrNull { it.second }?.first?.name?.lowercase()?.replaceFirstChar(
                        Char::uppercase,
                    ) ?: "—"}",
                    color = tokens.accentText,
                    style = MzTypography.bodySmall,
                )
            }
        }
        item { Text("Largest expenses", style = MzTypography.title) }
        items(snapshot.largest, key = { it.id }) { record ->
            MzListRow(
                "receipt_long",
                "Expense",
                record.categoryId?.let { snapshot.categoryNames[it] ?: it } ?: "Uncategorized",
                record.date.toString(),
                trailing = {
                    Text(
                        if (hide) "••••" else amount(kotlin.math.abs(record.baseMinor)),
                        color = tokens.expense,
                        style = MzTypography.amountSmall,
                    )
                },
                modifier =
                    Modifier.clickable {
                        goDeeper(record.categoryId ?: "uncategorized")
                    },
            )
        }
        item {
            MzButton(
                "Go deeper",
                {
                    goDeeper(
                        if (!labelsMode) {
                            selectedSlice ?: snapshot.categories
                                .firstOrNull()
                                ?.first
                                .orEmpty()
                        } else {
                            snapshot.categories
                                .firstOrNull()
                                ?.first
                                .orEmpty()
                        },
                    )
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun BalancePage(
    snapshot: StatsSnapshot,
    hide: Boolean,
    share: (String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    var netWorth by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val tokens = LocalMzTokens.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShareableCard("Balance trend", share) {
                Text("Do I have more money than before?", color = tokens.textDim, style = MzTypography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MzChip("Cash only", !netWorth, { netWorth = false })
                    MzChip("Net worth", netWorth, { netWorth = true })
                }
                val shownTrend = if (netWorth) snapshot.netWorthTrend else snapshot.balanceTrend
                val shownChange = if (netWorth) snapshot.netWorthChange else snapshot.balanceChange
                Text(if (hide) "••••••" else amount(shownTrend.lastOrNull() ?: 0L), style = MzTypography.amountLarge)
                Text("${changeText(shownChange)} vs same day last month", color = tokens.income, style = MzTypography.bodySmall)
                MzAreaChart(shownTrend, Modifier.fillMaxWidth().height(180.dp))
                Text(
                    "Unrealized exchange gain or loss is shown in wealth, not income.",
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
            }
        }
        item {
            ShareableCard("Balance by account", share) {
                val total = snapshot.accountBalances.sumOf { it.amount }
                Text(
                    snapshot.rateUpdatedAt?.let {
                        "Rates updated ${java.time.Instant.ofEpochMilli(
                            it,
                        ).atZone(ZoneId.of("Africa/Cairo")).toLocalDate()}"
                    }
                        ?: "Rates update pending",
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
                MzCompositionBar(
                    snapshot.accountBalances.mapIndexed { index, row ->
                        MzDonutSlice(
                            row.name,
                            row.amount.coerceAtLeast(0L),
                            listOf(tokens.champagne, tokens.income, tokens.transfer, tokens.investment, tokens.expense)[
                                index %
                                    5,
                            ],
                        )
                    },
                )
                snapshot.accountBalances.forEach { (account, value, buckets) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded =
                                    if (expanded ==
                                        account
                                    ) {
                                        null
                                    } else {
                                        account
                                    }
                            }.padding(vertical = 8.dp),
                    ) {
                        Row {
                            Text(account, Modifier.weight(1f), style = MzTypography.body)
                            Text(if (hide) "••••" else amount(value), style = MzTypography.amountSmall)
                            Text("  ${sharePercent(value, total)}%", color = tokens.textDim, style = MzTypography.bodySmall)
                        }
                        if (expanded ==
                            account
                        ) {
                            buckets.forEach {
                                Text(
                                    "${it.currency}  ${if (hide) {
                                        "••••"
                                    } else {
                                        amount(
                                            it.amountMinor,
                                        )
                                    }}  ≈ ${if (hide) "••••" else amount(it.baseMinor)} EGP",
                                    color = tokens.textDim,
                                    style = MzTypography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun ShareableCard(
    title: String,
    share: (String, androidx.compose.ui.geometry.Rect) -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    MzCard(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MzTypography.title)
            androidx.compose.material3.IconButton(onClick = {
                share(title, bounds)
            }) { MzIcon("ios_share", "Share $title as image", tint = LocalMzTokens.current.textDim) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun MetricBar(
    label: String,
    value: Long,
    other: Long,
    color: Color,
    hide: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MzTypography.body)
        Text(if (hide) "••••" else amount(value), color = color, style = MzTypography.amountSmall)
    }
    Spacer(Modifier.fillMaxWidth().height(8.dp).background(LocalMzTokens.current.raisedSurface))
    Spacer(Modifier.fillMaxWidth((value.toFloat() / maxOf(value, other, 1L)).coerceIn(0.02f, 1f)).height(6.dp).background(color))
    Spacer(Modifier.height(8.dp))
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun StatsLoadingPage(label: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label, color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
                    Spacer(Modifier.fillMaxWidth().height(20.dp).background(LocalMzTokens.current.raisedSurface))
                    Spacer(Modifier.fillMaxWidth(0.7f).height(14.dp).background(LocalMzTokens.current.raisedSurface))
                }
            }
        }
    }
}

private suspend fun shareCard(
    context: android.content.Context,
    view: android.view.View,
    title: String,
    bounds: androidx.compose.ui.geometry.Rect,
) {
    if (bounds.width <= 0f || bounds.height <= 0f) return
    val cropped = captureCardBitmap(view, bounds) ?: return
    val file = StatsImageExport.write(context, cropped)
    cropped.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
    context.startActivity(
        Intent.createChooser(
            Intent(
                Intent.ACTION_SEND,
            ).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share $title",
        ),
    )
}

internal fun captureCardBitmap(
    view: android.view.View,
    bounds: androidx.compose.ui.geometry.Rect,
): Bitmap? {
    if (bounds.width <= 0f || bounds.height <= 0f || view.width <= 0 || view.height <= 0) return null
    val whole = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(whole))
    val left = bounds.left.toInt().coerceIn(0, whole.width - 1)
    val top = bounds.top.toInt().coerceIn(0, whole.height - 1)
    val width =
        bounds.width
            .toInt()
            .coerceAtMost(whole.width - left)
            .coerceAtLeast(1)
    val height =
        bounds.height
            .toInt()
            .coerceAtMost(whole.height - top)
            .coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(whole, left, top, width, height)
    whole.recycle()
    return cropped
}

internal fun amount(minor: Long): String =
    "EGP " +
        java.text
            .DecimalFormat(
                "#,##0.00",
                java.text.DecimalFormatSymbols(java.util.Locale.US),
            ).format(BigDecimal.valueOf(minor).movePointLeft(2))

private fun signedAmount(minor: Long): String = (if (minor >= 0) "+" else "−") + amount(kotlin.math.abs(minor))

private fun sharePercent(
    value: Long,
    total: Long,
): Long = if (total == 0L) 0L else (value * 100 / total).coerceAtLeast(0L)

internal fun changeText(change: app.mizan.domain.stats.Change?): String =
    when {
        change == null -> {
            "— vs previous period"
        }

        change.absoluteChange -> {
            "${amount(kotlin.math.abs(change.currentMinor - change.previousMinor))} absolute change"
        }

        change.percentBasisPoints == null -> {
            "— vs previous period"
        }

        else -> {
            val percent = requireNotNull(change.percentBasisPoints)
            "${if (percent >= 0) "↑" else "↓"} ${kotlin.math.abs(
                percent,
            ) / 100}.${(kotlin.math.abs(percent) % 100).toString().padStart(2, '0')}% vs previous period"
        }
    }

internal data class AccountBalancePresentation(
    val name: String,
    val amount: Long,
    val buckets: List<BucketPresentation>,
)

internal data class BucketPresentation(
    val currency: String,
    val amountMinor: Long,
    val baseMinor: Long,
)

internal data class StatsSnapshot(
    val current: app.mizan.domain.stats.CashFlow,
    val cashFlowChange: app.mizan.domain.stats.Change,
    val expenseChange: app.mizan.domain.stats.Change,
    val daily: List<Pair<LocalDate, app.mizan.domain.stats.CashFlow>>,
    val cumulative: List<Long>,
    val seriesSets: List<List<Long>>,
    val categories: List<Pair<String, Long>>,
    val labels: List<Pair<String, Long>>,
    val categoryNames: Map<String, String>,
    val labelNames: Map<String, String>,
    val payeeNames: Map<String, String>,
    val categoryChanges: Map<String, app.mizan.domain.stats.Change>,
    val labelChanges: Map<String, app.mizan.domain.stats.Change>,
    val payees: List<Pair<String, Long>>,
    val weekdays: List<Pair<DayOfWeek, Long>>,
    val largest: List<StatRecord>,
    val balanceTrend: List<Long>,
    val balanceChange: app.mizan.domain.stats.Change,
    val balanceSeries: List<Long>,
    val accountBalances: List<AccountBalancePresentation>,
    val rateUpdatedAt: Long?,
    val netWorthTrend: List<Long> = emptyList(),
    val netWorthChange: app.mizan.domain.stats.Change = compare(0L, 0L),
) {
    fun series(metric: String): List<List<Long>> =
        when (metric) {
            "Income" -> seriesSets.subList(3, 6)
            "Expenses" -> seriesSets.subList(6, 9)
            else -> seriesSets.subList(0, 3)
        }

    companion object {
        fun empty() =
            StatsSnapshot(
                app.mizan.domain.stats
                    .CashFlow(0, 0),
                compare(0, 0),
                compare(0, 0),
                emptyList(),
                emptyList(),
                List(9) {
                    emptyList()
                },
                emptyList(),
                emptyList(),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                compare(
                    0,
                    0,
                ),
                emptyList(),
                emptyList(),
                null,
            )

        fun create(
            rows: List<StatsRecordRow>,
            entries: List<StatsEntryRow>,
            balances: List<AccountBalanceRow>,
            rates: List<FxRateEntity>,
            accounts: List<AccountEntity>,
            categoriesRef: List<CategoryEntity>,
            labelsRef: List<LabelEntity>,
            payeesRef: List<PayeeEntity>,
            assets: List<AssetEntity>,
            trades: List<AssetTransactionEntity>,
            prices: List<PriceSnapshotEntity>,
            currencies: List<CurrencyEntity>,
            debts: List<DebtEntity>,
            period: Period,
            previous: Period,
            lastYear: Period,
            accountFilter: String?,
            debtRepayments: List<DebtRepaymentRow> = emptyList(),
            debtRates: List<FxRateEntity> = rates,
        ): StatsSnapshot {
            val selectedAccounts = accountFilter?.split('|')?.toSet()
            val entriesByTransaction = entries.groupBy { it.txnId }
            val records =
                rows.mapNotNull { row ->
                    val selectedEntries =
                        entriesByTransaction[row.id].orEmpty().filter {
                            selectedAccounts == null ||
                                it.accountId in selectedAccounts
                        }
                    if (selectedAccounts != null && selectedEntries.isEmpty()) return@mapNotNull null
                    val selectedAmount = if (selectedAccounts == null) row.baseMinor else selectedEntries.sumOf { it.baseMinor }
                    StatRecord(
                        row.id,
                        LocalDate.parse(row.bookedDate),
                        StatKind.fromLedgerKind(row.kind),
                        selectedAmount,
                        selectedEntries.firstOrNull()?.accountId ?: row.accountId,
                        row.categoryId,
                        row.payeeId,
                        row.labels
                            .split('|')
                            .filter(String::isNotBlank)
                            .toSet(),
                    )
                }
            val current = StatsEngine.cashFlow(records, period)
            val previousFlow = StatsEngine.cashFlow(records, previous)
            val lyFlow = StatsEngine.cashFlow(records, lastYear)
            val dayFlows = StatsEngine.dailyCashFlow(records, period).toList()
            val prevDays = StatsEngine.dailyCashFlow(records, previous).values.toList()
            val lyDays = StatsEngine.dailyCashFlow(records, lastYear).values.toList()
            val categories = StatsEngine.byCategory(records, period).toList().sortedByDescending { it.second }
            val previousCategories = StatsEngine.byCategory(records, previous)
            val categoryChanges = categories.associate { it.first to compare(it.second, previousCategories[it.first] ?: 0L) }
            val labelAmounts = StatsEngine.byLabel(records, period)
            val previousLabels = StatsEngine.byLabel(records, previous)
            val labelChanges = labelAmounts.mapValues { (key, value) -> compare(value, previousLabels[key] ?: 0L) }
            val includedAccountIds = accounts.filter { it.includeInTotals && !it.archived }.map { it.id }.toSet()
            val scopedEntries =
                entries.filter {
                    (if (selectedAccounts == null) it.accountId in includedAccountIds else it.accountId in selectedAccounts) &&
                        LocalDate.parse(it.bookedDate) <= period.end
                }
            val balance =
                balances.filter {
                    if (selectedAccounts ==
                        null
                    ) {
                        it.accountId in includedAccountIds
                    } else {
                        it.accountId in selectedAccounts
                    }
                }
            val opening = balance.sumOf { MoneyMath.baseMinor(it.openingMinor, it.decimals, BigDecimal(it.openingEgpPerUnit)) }
            var running = opening + scopedEntries.filter { LocalDate.parse(it.bookedDate) < period.start }.sumOf { it.baseMinor }
            val deltasByDate = scopedEntries.groupBy { it.bookedDate }.mapValues { (_, dayRows) -> dayRows.sumOf { it.baseMinor } }
            val dailyBalance =
                (0 until period.days).map { offset ->
                    val date = period.start.plusDays(offset.toLong())
                    running += deltasByDate[date.toString()] ?: 0L
                    running
                }
            val priorBalanceIndex =
                (previous.start.plusDays((period.days - 1).coerceAtLeast(0).toLong())).let { date ->
                    val priorOpening = balance.sumOf { MoneyMath.baseMinor(it.openingMinor, it.decimals, BigDecimal(it.openingEgpPerUnit)) }
                    priorOpening + scopedEntries.filter { LocalDate.parse(it.bookedDate) <= date }.sumOf { it.baseMinor }
                }
            val byAccount =
                balance
                    .groupBy { it.accountId }
                    .mapNotNull { (id, buckets) ->
                        val name = buckets.firstOrNull()?.accountName ?: return@mapNotNull null
                        val bucketList =
                            buckets.map {
                                BucketPresentation(
                                    it.currency,
                                    it.openingMinor + it.entryMinor,
                                    MoneyMath.baseMinor(it.openingMinor + it.entryMinor, it.decimals, BigDecimal(it.egpPerUnit)),
                                )
                            }
                        AccountBalancePresentation(name, bucketList.sumOf { it.baseMinor }, bucketList)
                    }.sortedByDescending { it.amount }

            fun cumulative(
                flows: List<app.mizan.domain.stats.CashFlow>,
                selector: (app.mizan.domain.stats.CashFlow) -> Long,
            ) = flows
                .runningFold(0L) { total, flow ->
                    total +
                        selector(flow)
                }.drop(1)
            val series =
                listOf(
                    cumulative(dayFlows.map { it.second }, { it.netMinor }),
                    cumulative(prevDays, { it.netMinor }),
                    cumulative(lyDays, { it.netMinor }),
                    cumulative(
                        dayFlows.map {
                            it.second
                        },
                        { it.incomeMinor },
                    ),
                    cumulative(prevDays, { it.incomeMinor }),
                    cumulative(lyDays, { it.incomeMinor }),
                    cumulative(
                        dayFlows.map {
                            it.second
                        },
                        { it.expenseMinor },
                    ),
                    cumulative(prevDays, { it.expenseMinor }),
                    cumulative(lyDays, { it.expenseMinor }),
                )
            val tradesByAsset = trades.filter { it.deletedAt == null }.groupBy { it.assetId }

            fun netWorthAt(
                date: LocalDate,
                cash: Long,
            ): Long {
                val holdings =
                    assets.filterNot { it.archived }.sumOf { asset ->
                        val history = tradesByAsset[asset.id].orEmpty().filter { LocalDate.parse(it.date) <= date }
                        val key = if (asset.type == "GOLD") "GOLD_${asset.symbolOrKarats.uppercase()}" else "MANUAL:${asset.id}"
                        val price =
                            prices
                                .filter {
                                    it.key == key &&
                                        java.time.Instant
                                            .ofEpochMilli(it.asOf)
                                            .atZone(ZoneId.of("Africa/Cairo"))
                                            .toLocalDate() <= date
                                }.maxByOrNull { it.asOf } ?: return@sumOf 0L
                        val decimals = currencies.firstOrNull { it.code == price.currency }?.decimals ?: return@sumOf 0L
                        val position =
                            AverageCost.position(
                                history.map { trade ->
                                    AssetTransaction(
                                        trade.id,
                                        trade.assetId,
                                        AssetTransactionKind.valueOf(trade.kind),
                                        LocalDate.parse(trade.date),
                                        BigDecimal(trade.quantity),
                                        BigDecimal(trade.unitPrice),
                                        trade.feeMinor,
                                        trade.totalMinor,
                                        trade.txnId,
                                        trade.note,
                                    )
                                },
                            )
                        val quoteMinor =
                            AssetValuation.valueMinor(
                                position.quantity,
                                BigDecimal(price.price),
                                decimals,
                                BigDecimal(asset.resaleDeductionPct),
                            )
                        val rate =
                            if (price.currency ==
                                "EGP"
                            ) {
                                BigDecimal.ONE
                            } else {
                                rates.firstOrNull { it.currency == price.currency }?.egpPerUnit?.let(::BigDecimal)
                                    ?: return@sumOf 0L
                            }
                        MoneyMath.baseMinor(quoteMinor, decimals, rate)
                    }
                val valuedDebts =
                    valuedDebtBalances(
                        debts.filter {
                            selectedAccounts == null || it.accountId in selectedAccounts
                        },
                        debtRepayments,
                        currencies,
                        debtRates,
                        date,
                    )
                val receivables = valuedDebts.filter { it.direction == "LENT" }.sumOf { it.principalMinor }
                val payables = valuedDebts.filter { it.direction == "BORROWED" }.sumOf { it.principalMinor }
                return NetWorthMath.total(cash, holdings, receivables, payables)
            }
            val netWorthTrend = dailyBalance.mapIndexed { index, cash -> netWorthAt(period.start.plusDays(index.toLong()), cash) }
            val priorNetWorth =
                netWorthAt(
                    previous.start.plusDays((period.days - 1).coerceAtLeast(0).toLong()),
                    priorBalanceIndex,
                )
            return StatsSnapshot(
                current,
                compare(current.netMinor, previousFlow.netMinor, true),
                compare(current.expenseMinor, previousFlow.expenseMinor),
                dayFlows,
                StatsEngine.cumulativeTrend(records, period).values.toList(),
                series,
                categories,
                labelAmounts.toList().sortedByDescending {
                    it.second
                },
                categoriesRef.associate { it.id to it.name },
                labelsRef.associate { it.id to it.name },
                payeesRef.associate { it.id to it.name },
                categoryChanges,
                labelChanges,
                StatsEngine.topPayees(records, period),
                StatsEngine.weekdayProfile(records, period.end).entries.sortedBy { it.key.value }.map {
                    it.key to
                        it.value
                },
                StatsEngine.largestExpenses(records, period),
                dailyBalance,
                compare(dailyBalance.lastOrNull() ?: 0L, priorBalanceIndex),
                dailyBalance,
                byAccount,
                rates.maxOfOrNull {
                    it.fetchedAt
                },
                netWorthTrend,
                compare(netWorthTrend.lastOrNull() ?: 0L, priorNetWorth),
            )
        }
    }
}

private fun PeriodState.toStatsPeriod(): Period {
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val pair =
        when (kind) {
            PeriodKind.DAY -> {
                (start ?: today) to (start ?: today)
            }

            PeriodKind.WEEK -> {
                val anchor =
                    start ?: today
                val weekStart = startOfWeek(anchor)
                weekStart to
                    weekStart.plusDays(6)
            }

            PeriodKind.YEAR -> {
                today.withDayOfYear(1) to today
            }

            PeriodKind.ALL -> {
                LocalDate.of(2000, 1, 1) to today
            }

            PeriodKind.CUSTOM -> {
                (start ?: today.withDayOfMonth(1)) to (end ?: today)
            }

            PeriodKind.MONTH -> {
                today.withDayOfMonth(1) to today
            }
        }
    return Period(pair.first, pair.second)
}

private fun PeriodState.outlookEnd(today: LocalDate): LocalDate =
    when (kind) {
        PeriodKind.DAY -> {
            start ?: today
        }

        PeriodKind.WEEK -> {
            val anchor = start ?: today
            startOfWeek(anchor).plusDays(6)
        }

        PeriodKind.MONTH, PeriodKind.ALL -> {
            today.withDayOfMonth(today.lengthOfMonth())
        }

        PeriodKind.YEAR -> {
            today.withMonth(12).withDayOfMonth(31)
        }

        PeriodKind.CUSTOM -> {
            end ?: today
        }
    }
