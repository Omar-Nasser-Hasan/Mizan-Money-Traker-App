// Copyright (c) 2026 Mizan contributors
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.data.db.RecordRow
import app.mizan.data.db.StatsRecordRow
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetValuation
import app.mizan.domain.assets.AverageCost
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.BudgetMath
import app.mizan.domain.plan.BudgetMathInput
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import app.mizan.domain.plan.Rollover
import app.mizan.domain.plan.budgetWindow
import app.mizan.domain.stats.HomeAccountValue
import app.mizan.domain.stats.HomeBanners
import app.mizan.domain.stats.HomeDebtValue
import app.mizan.domain.stats.HomeSummary
import app.mizan.domain.stats.HomeSummaryCalculator
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatPeriods
import app.mizan.domain.stats.StatRecord
import app.mizan.worker.FxRefreshWorker
import app.mizan.worker.PriceRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private val cairoZone: ZoneId = ZoneId.of("Africa/Cairo")

@Composable
@Suppress("ktlint:standard:function-naming")
fun V2HomeScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onNavigate: (V2Route) -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val balanceRows by repository.observeAccountBalances().collectAsStateWithLifecycle(emptyList())
    val records by repository.observeStatsRows().collectAsStateWithLifecycle(emptyList())
    val recent by repository.observeRecentRecords(5).collectAsStateWithLifecycle(emptyList())
    val assets by repository.observeAssets().collectAsStateWithLifecycle(emptyList())
    val trades by repository.observeAllAssetTransactions().collectAsStateWithLifecycle(emptyList())
    val prices by repository.observeLatestPrices().collectAsStateWithLifecycle(emptyList())
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val rates by repository.observeLatestFxRates().collectAsStateWithLifecycle(emptyList())
    val budgets by repository.observeBudgets().collectAsStateWithLifecycle(emptyList())
    val planned by repository.observePlannedPayments().collectAsStateWithLifecycle(emptyList())
    val debts by repository.observeDebts().collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(cairoZone)
    val data by produceState<HomeUiData?>(
        null,
        accounts,
        balanceRows,
        records,
        recent,
        assets,
        trades,
        prices,
        currencies,
        rates,
        budgets,
        planned,
        debts,
        today,
    ) {
        value =
            withContext(Dispatchers.Default) {
                HomeUiData.from(
                    accounts,
                    balanceRows,
                    records,
                    recent,
                    assets,
                    trades,
                    prices,
                    currencies,
                    rates,
                    budgets,
                    planned,
                    debts,
                    today,
                )
            }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val refresh = {
        val work = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        work.enqueueUniqueWork(
            "home-fx-refresh",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FxRefreshWorker>().setConstraints(network).build(),
        )
        work.enqueueUniqueWork(
            "home-price-refresh",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PriceRefreshWorker>().setConstraints(network).build(),
        )
    }
    val refreshConnection =
        remember(refresh) {
            object : NestedScrollConnection {
                private var downwardDistance = 0f

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f) downwardDistance += available.y
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (downwardDistance >= 120f) refresh()
                    downwardDistance = 0f
                    return Velocity.Zero
                }
            }
        }
    Box(
        Modifier.fillMaxSize().nestedScroll(refreshConnection),
    ) {
        when {
            data == null -> {
                HomeLoading()
            }

            data!!.accounts.isEmpty() -> {
                MzEmptyState(
                    title = "Your money starts here",
                    description = "Add an account, then record your first transaction.",
                    actionLabel = "Add account",
                    onAction = { onNavigate(V2Route.NewAccount) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                HomeContent(data!!, hideAmounts, onNavigate)
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Column(Modifier.fillMaxSize().padding(MzSpacing.sideMargin), verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter)) {
        MzSkeleton(Modifier.fillMaxWidth(), 190.dp)
        MzSkeleton(Modifier.fillMaxWidth(), 72.dp)
        MzSkeleton(Modifier.fillMaxWidth(), 150.dp)
        MzSkeleton(Modifier.fillMaxWidth(), 150.dp)
    }
}

@Composable
private fun HomeContent(
    data: HomeUiData,
    hideAmounts: Boolean,
    onNavigate: (V2Route) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
    ) {
        item { NetWorthHero(data.summary, hideAmounts) }
        if (HomeBanners.showUncategorized(data.summary.uncategorizedExpenseCount)) {
            item {
                MzBanner("${data.summary.uncategorizedExpenseCount} records need a category", onClick = { onNavigate(V2Route.Records) })
            }
        }
        if (HomeBanners.showStaleGold(data.goldAgeHours)) {
            item { MzBanner("Gold prices are ${data.goldAgeHours} h old", onClick = { onNavigate(V2Route.Prices) }) }
        }
        item { SectionTitle("Accounts", "Add account") { onNavigate(V2Route.NewAccount) } }
        item { AccountsCarousel(data.accounts, data.accountBuckets, hideAmounts, onNavigate) }
        if (data.recent.isEmpty()) {
            item {
                MzEmptyState(
                    title = "Ready for your first record?",
                    description = "Your accounts are set up. Add an expense, income, or transfer when it happens.",
                    actionLabel = "Add a record",
                    onAction = { onNavigate(V2Route.ExpenseEntry) },
                )
            }
        }
        item { ThisMonthCard(data.summary, hideAmounts) }
        if (data.budgets.isNotEmpty()) {
            item { SectionTitle("Budgets", "See all") { onNavigate(V2Route.Budgets) } }
            items(data.budgets, key = { it.id }) { budget -> BudgetCard(budget, hideAmounts) }
        }
        if (data.upcoming.isNotEmpty()) {
            item { SectionTitle("Next 7 days") }
            items(data.upcoming, key = { it.id }) { item ->
                MzListRow(
                    iconName = item.icon,
                    iconDescription = item.title,
                    title = item.title,
                    subtitle = item.subtitle,
                    modifier = Modifier.clickable { onNavigate(item.destination) },
                    iconColor = if (item.amountMinor < 0) LocalMzTokens.current.expense else LocalMzTokens.current.income,
                    trailing = {
                        AmountText(
                            item.amountMinor,
                            "EGP",
                            if (item.amountMinor <
                                0
                            ) {
                                AmountStyle.EXPENSE
                            } else {
                                AmountStyle.INCOME
                            },
                            hideAmounts,
                            textStyle = MzTypography.amountSmall,
                        )
                    },
                )
            }
        }
        if (data.recent.isNotEmpty()) {
            item { SectionTitle("Recent", "See all records") { onNavigate(V2Route.Records) } }
            items(
                data.recent,
                key = { it.entryId },
            ) { record -> RecentRow(record, hideAmounts) { onNavigate(V2Route.RecordDetail(record.id)) } }
        }
    }
}

@Composable
private fun NetWorthHero(
    summary: HomeSummary,
    hideAmounts: Boolean,
) {
    val tokens = LocalMzTokens.current
    MzCard(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(tokens.champagne.copy(alpha = 0.20f), Color.Transparent)), MzShapes.exterior),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Text("NET WORTH", color = tokens.textDim, style = MzTypography.labelCaps)
            AmountText(summary.netWorthMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.hero)
            MzSparkline(summary.sparkline, Modifier.fillMaxWidth().height(42.dp))
            AmountText(summary.cashMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.body)
            androidx.compose.material3.Text("Cash", color = tokens.textDim, style = MzTypography.bodySmall)
            androidx.compose.material3.Text(changeText(summary.cashFlowChange), color = tokens.textDim, style = MzTypography.bodySmall)
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(title, style = MzTypography.title, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) MzButton(action, onAction, style = MzButtonStyle.GHOST)
    }
}

@Composable
private fun AccountsCarousel(
    accounts: List<AccountEntity>,
    buckets: Map<String, List<AccountBucket>>,
    hideAmounts: Boolean,
    onNavigate: (V2Route) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MzSpacing.gutter)) {
        items(accounts, key = { it.id }) { account ->
            val accountBuckets = buckets[account.id].orEmpty()
            MzCard(Modifier.width(244.dp).clickable { onNavigate(V2Route.AccountDetail(account.id)) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Text(account.name, style = MzTypography.title)
                    AmountText(accountBuckets.sumOf { it.egpMinor }, "EGP", hideAmounts = hideAmounts)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accountBuckets.drop(1).forEach { MzChip(it.currency, false, {}) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThisMonthCard(
    summary: HomeSummary,
    hideAmounts: Boolean,
) {
    MzCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Text("This month", style = MzTypography.title)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HomeAmount("Income", summary.cashFlow.incomeMinor, AmountStyle.INCOME, hideAmounts)
                HomeAmount("Expenses", summary.cashFlow.expenseMinor, AmountStyle.EXPENSE, hideAmounts)
                HomeAmount("Net", summary.cashFlow.netMinor, AmountStyle.NEUTRAL, hideAmounts)
            }
            MzCompositionBar(
                listOf(
                    MzDonutSlice("Income", summary.cashFlow.incomeMinor, LocalMzTokens.current.income),
                    MzDonutSlice("Expenses", summary.cashFlow.expenseMinor, LocalMzTokens.current.expense),
                ),
            )
            androidx.compose.material3.Text(
                changeText(summary.cashFlowChange),
                color = LocalMzTokens.current.textDim,
                style = MzTypography.bodySmall,
            )
            if (summary.investedMinor > 0) {
                MzChip("Invested", false, {})
                AmountText(summary.investedMinor, "EGP", AmountStyle.INVESTMENT, hideAmounts, textStyle = MzTypography.amountSmall)
            }
        }
    }
}

@Composable
private fun HomeAmount(
    label: String,
    minor: Long,
    style: AmountStyle,
    hideAmounts: Boolean,
) {
    Column {
        androidx.compose.material3.Text(label, color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
        AmountText(minor, "EGP", style, hideAmounts, textStyle = MzTypography.amountSmall)
    }
}

@Composable
private fun BudgetCard(
    budget: HomeBudget,
    hideAmounts: Boolean,
) {
    MzCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text(
                    budget.name,
                    modifier = Modifier.weight(1f),
                    style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold),
                )
                androidx.compose.material3.Text(budget.state, color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
            }
            MzBudgetBar(budget.percentBasisPoints / 10_000f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AmountText(budget.spentMinor, "EGP", AmountStyle.EXPENSE, hideAmounts, textStyle = MzTypography.amountSmall)
                AmountText(budget.limitMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.amountSmall)
            }
        }
    }
}

@Composable
private fun RecentRow(
    record: RecordRow,
    hideAmounts: Boolean,
    onClick: () -> Unit,
) {
    val style =
        when (record.kind) {
            "EXPENSE" -> AmountStyle.EXPENSE
            "INCOME", "ASSET_INCOME" -> AmountStyle.INCOME
            "TRANSFER" -> AmountStyle.TRANSFER
            "ASSET_BUY", "ASSET_SELL" -> AmountStyle.INVESTMENT
            else -> AmountStyle.NEUTRAL
        }
    MzListRow(
        iconName = if (record.kind == "TRANSFER") "swap_horiz" else "receipt_long",
        iconDescription = record.kind,
        title = record.categoryName ?: record.payeeName ?: record.kind.replace('_', ' '),
        subtitle = "${record.accountName} · ${record.bookedDate}",
        modifier = Modifier.clickable(onClick = onClick),
        trailing = { AmountText(record.amountMinor, record.currency, style, hideAmounts, textStyle = MzTypography.amountSmall) },
    )
}

private fun changeText(change: app.mizan.domain.stats.Change): String =
    when {
        change.absoluteChange -> {
            "vs same days last month · absolute change"
        }

        change.percentBasisPoints == null -> {
            "vs same days last month · —"
        }

        else -> {
            val percentage = requireNotNull(change.percentBasisPoints)
            val sign = if (percentage >= 0) "+" else "−"
            val value = abs(percentage)
            "vs same days last month · $sign${value / 100}.${(value % 100).toString().padStart(2, '0')}%"
        }
    }

private data class AccountBucket(
    val currency: String,
    val egpMinor: Long,
)

private data class HomeBudget(
    val id: String,
    val name: String,
    val spentMinor: Long,
    val limitMinor: Long,
    val percentBasisPoints: Long,
) {
    val state: String get() =
        when {
            percentBasisPoints >= 10_000L -> "Over"
            percentBasisPoints >= 8_000L -> "Trending over"
            else -> "On pace"
        }
}

private data class UpcomingHomeItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountMinor: Long,
    val icon: String,
    val destination: V2Route,
)

private data class HomeUiData(
    val summary: HomeSummary,
    val accounts: List<AccountEntity>,
    val accountBuckets: Map<String, List<AccountBucket>>,
    val budgets: List<HomeBudget>,
    val upcoming: List<UpcomingHomeItem>,
    val recent: List<RecordRow>,
    val goldAgeHours: Long?,
) {
    companion object {
        fun from(
            accounts: List<AccountEntity>,
            balanceRows: List<AccountBalanceRow>,
            rows: List<StatsRecordRow>,
            recent: List<RecordRow>,
            assets: List<AssetEntity>,
            trades: List<AssetTransactionEntity>,
            prices: List<PriceSnapshotEntity>,
            currencies: List<CurrencyEntity>,
            rates: List<FxRateEntity>,
            budgets: List<BudgetEntity>,
            planned: List<PlannedPaymentEntity>,
            debts: List<DebtEntity>,
            today: LocalDate,
        ): HomeUiData {
            val accountBuckets =
                balanceRows.groupBy { it.accountId }.mapValues { (_, values) ->
                    values.map {
                        AccountBucket(
                            it.currency,
                            MoneyMath.baseMinor(it.openingMinor + it.entryMinor, it.decimals, BigDecimal(it.egpPerUnit)),
                        )
                    }
                }
            val statRows =
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
            val holdings = holdings(assets, trades, prices, currencies, rates)
            val debtValues =
                debts.map { debt ->
                    HomeDebtValue(debt.direction, debt.principalMinor, rows.filter { it.debtId == debt.id }.sumOf { abs(it.baseMinor) })
                }
            val month = Period(today.withDayOfMonth(1), today)
            val summary =
                HomeSummaryCalculator.calculate(
                    statRows,
                    accounts.map { account ->
                        HomeAccountValue(account.id, account.name, accountBuckets[account.id].orEmpty().sumOf { it.egpMinor })
                    },
                    holdings,
                    debtValues,
                    rows
                        .filter {
                            it.kind ==
                                "ASSET_BUY" &&
                                LocalDate.parse(it.bookedDate) in month.start..month.end
                        }.sumOf { abs(it.baseMinor) },
                    month,
                    StatPeriods.previous(month),
                    Period(today.minusDays(29), today),
                )
            val homeBudgets =
                budgets
                    .mapNotNull { budget -> homeBudget(budget, statRows, today) }
                    .sortedWith(
                        compareBy<HomeBudget> {
                            it.state !=
                                "Over"
                        }.thenByDescending { it.percentBasisPoints },
                    ).take(3)
            val upcoming = upcoming(planned, debts, rows, today)
            val gold = prices.firstOrNull { it.key == "GOLD_24K" }
            val age = gold?.let { ((Instant.now().toEpochMilli() - it.asOf).coerceAtLeast(0L) / 3_600_000L) }
            return HomeUiData(summary, accounts, accountBuckets, homeBudgets, upcoming, recent, age)
        }

        private fun homeBudget(
            budget: BudgetEntity,
            rows: List<StatRecord>,
            today: LocalDate,
        ): HomeBudget? =
            runCatching {
                val window =
                    budgetWindow(
                        BudgetPeriod.valueOf(budget.period),
                        today,
                        budget.startDate?.let(LocalDate::parse),
                        budget.endDate?.let(LocalDate::parse),
                    )
                if (today !in window.start..window.end) return null
                val spent =
                    rows
                        .filter {
                            it.kind == StatKind.EXPENSE && it.date in window.start..today &&
                                (
                                    budget.scope == "ALL" || (budget.scope == "ACCOUNT" && it.accountId in budget.scopeIds.split(',')) ||
                                        (budget.scope == "CATEGORY" && it.categoryId in budget.scopeIds.split(','))
                                )
                        }.sumOf { abs(it.baseMinor) }
                val result =
                    BudgetMath.calculate(
                        BudgetMathInput(
                            budget.limitMinor,
                            spent,
                            rollover = Rollover.valueOf(budget.rollover),
                            elapsedDays =
                                java.time.temporal.ChronoUnit.DAYS
                                    .between(window.start, today)
                                    .toInt() + 1,
                            remainingDays =
                                java.time.temporal.ChronoUnit.DAYS
                                    .between(today, window.end)
                                    .toInt(),
                        ),
                    )
                HomeBudget(budget.id, budget.name, spent, result.effectiveLimitMinor, result.percentBasisPoints)
            }.getOrNull()

        private fun upcoming(
            planned: List<PlannedPaymentEntity>,
            debts: List<DebtEntity>,
            rows: List<StatsRecordRow>,
            today: LocalDate,
        ): List<UpcomingHomeItem> {
            val through = today.plusDays(7)
            val payments =
                planned.flatMap { item ->
                    RecurrenceEngine
                        .occurrences(
                            Recurrence(
                                Frequency.valueOf(item.frequency),
                                item.interval,
                                LocalDate.parse(item.startDate),
                                item.endDate?.let(LocalDate::parse),
                            ),
                            today,
                            through,
                        ).map { date ->
                            UpcomingHomeItem(
                                "${item.id}:$date",
                                item.name,
                                date.toString(),
                                if (item.kind ==
                                    "EXPENSE"
                                ) {
                                    -item.amountMinor
                                } else {
                                    item.amountMinor
                                },
                                "event",
                                V2Route.PlannedPayments,
                            )
                        }
                }
            val debtRows =
                debts.mapNotNull { debt ->
                    debt.dueDate?.let(LocalDate::parse)?.takeIf { it in today..through }?.let { date ->
                        val repaid =
                            rows
                                .filter {
                                    it.debtId ==
                                        debt.id
                                }.sumOf { abs(it.baseMinor) }
                        ; UpcomingHomeItem(
                            debt.id,
                            debt.person,
                            "Due $date",
                            if (debt.direction ==
                                "BORROWED"
                            ) {
                                -(debt.principalMinor - repaid).coerceAtLeast(0L)
                            } else {
                                (debt.principalMinor - repaid).coerceAtLeast(0L)
                            },
                            "person",
                            V2Route.Debts,
                        )
                    }
                }
            return (payments + debtRows).sortedBy { it.subtitle }.take(7)
        }

        private fun holdings(
            assets: List<AssetEntity>,
            trades: List<AssetTransactionEntity>,
            prices: List<PriceSnapshotEntity>,
            currencies: List<CurrencyEntity>,
            rates: List<FxRateEntity>,
        ): Long =
            assets.sumOf { asset ->
                val position =
                    AverageCost.position(
                        trades
                            .filter {
                                it.assetId == asset.id
                            }.map {
                                AssetTransaction(
                                    it.id,
                                    it.assetId,
                                    AssetTransactionKind.valueOf(it.kind),
                                    LocalDate.parse(it.date),
                                    BigDecimal(it.quantity),
                                    BigDecimal(it.unitPrice),
                                    it.feeMinor,
                                    it.totalMinor,
                                    it.txnId,
                                    it.note,
                                )
                            },
                    )
                val key = if (asset.type == "GOLD") "GOLD_${asset.symbolOrKarats.uppercase()}" else "MANUAL:${asset.id}"
                val price = prices.firstOrNull { it.key == key } ?: return@sumOf 0L
                val decimals = currencies.firstOrNull { it.code == price.currency }?.decimals ?: return@sumOf 0L
                val quote =
                    AssetValuation.valueMinor(
                        position.quantity.coerceAtLeast(BigDecimal.ZERO),
                        BigDecimal(price.price),
                        decimals,
                        BigDecimal(asset.resaleDeductionPct),
                    )
                MoneyMath.baseMinor(
                    quote,
                    decimals,
                    if (price.currency ==
                        "EGP"
                    ) {
                        BigDecimal.ONE
                    } else {
                        rates.firstOrNull { it.currency == price.currency }?.egpPerUnit?.let(::BigDecimal)
                            ?: return@sumOf 0L
                    },
                )
            }
    }
}
