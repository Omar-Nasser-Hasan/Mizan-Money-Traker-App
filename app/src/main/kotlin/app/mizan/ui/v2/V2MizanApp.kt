// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AccountEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.ui.onboardingScreen
import kotlinx.coroutines.launch

/**
 * The V2 host deliberately contains only shell/navigation at this milestone. Each destination is a
 * real route now, and later prompt-specific screen work replaces [V2Placeholder] in place.
 */
@Composable
@Suppress("ktlint:standard:function-naming")
fun v2MizanApp(
    repository: MizanRepository,
    quickAction: String? = null,
) {
    val accountSnapshot by produceState<List<AccountEntity>?>(null, repository) {
        repository.observeAccounts().collect { value = it }
    }
    val accounts = accountSnapshot.orEmpty()
    val hideAmounts by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    val periodKind by repository.observeV2PeriodKind().collectAsStateWithLifecycle("MONTH")
    val periodStart by repository.observeV2PeriodStart().collectAsStateWithLifecycle(null)
    val periodEnd by repository.observeV2PeriodEnd().collectAsStateWithLifecycle(null)
    val accountFilterId by repository.observeV2AccountFilter().collectAsStateWithLifecycle(null)
    val baseCurrency by repository.observeV2BaseCurrency().collectAsStateWithLifecycle("EGP")
    val lastAccountId by repository.observeLastUsedAccountId().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var navigation by rememberSaveable(stateSaver = V2NavigationState.Saver) { mutableStateOf(V2NavigationState()) }
    var selectedStatsPage by rememberSaveable { mutableStateOf("Balance") }
    var selectedStatsCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var wealthActionsExpanded by rememberSaveable { mutableStateOf(false) }
    val period = PeriodState.fromPersisted(periodKind, periodStart, periodEnd)

    LaunchedEffect(quickAction) {
        quickEntryRoute(quickAction)?.let { navigation = navigation.navigate(it) }
    }

    // The legacy lock is an Activity gate, so it completes before either shell is composed. Empty
    // accounts retain the existing first-run gate before root navigation is exposed.
    when (V2StartupGate.destinationForAccounts(accountSnapshot != null, accounts.isNotEmpty())) {
        StartupDestination.LOADING -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MzSkeleton(Modifier.size(48.dp), 48.dp)
            }
            return
        }

        StartupDestination.ONBOARDING -> {
            onboardingScreen(repository, Modifier.fillMaxSize(), onDone = {})
            return
        }

        StartupDestination.SHELL -> {}
    }

    val activeRoute = navigation.activeRoute
    val selectedTab = navigation.selectedTab
    val isSheetRoute = activeRoute?.presentation == V2Presentation.SHEET
    Scaffold(
        containerColor = LocalMzTokens.current.canvas,
        topBar = {
            MzTopBar(
                title =
                    if (isSheetRoute) {
                        selectedTab.title
                    } else {
                        activeRoute?.title
                            ?: if (selectedTab ==
                                V2Route.BalanceStats
                            ) {
                                "Stats"
                            } else if (selectedTab == V2Route.Budgets) {
                                "Plan"
                            } else {
                                selectedTab.title
                            }
                    },
                navigationIcon =
                    if (activeRoute == null || isSheetRoute) {
                        null
                    } else {
                        {
                            MzIconButton("arrow_back", "Back") { navigation = navigation.back() }
                        }
                    },
                actions = {
                    MzIconButton(if (hideAmounts) "visibility_off" else "visibility", "Hide amounts") {
                        scope.launch { repository.setHideAmounts(!hideAmounts) }
                    }
                    MzIconButton("settings", "Settings") { navigation = navigation.navigate(V2Route.Settings) }
                },
            )
        },
        bottomBar = {
            if (activeRoute == null || isSheetRoute) {
                MzBottomNav(
                    selectedLabel = V2NavigationState.labelForRootTab(selectedTab),
                    onSelected = { label ->
                        V2NavigationState.rootTabForLabel(label)?.let { navigation = navigation.selectTab(it) }
                    },
                )
            }
        },
        floatingActionButton = {
            if (activeRoute ==
                null
            ) {
                if (selectedTab == V2Route.Wealth) {
                    Box {
                        MzFab(onClick = { wealthActionsExpanded = true }, contentDescription = "Wealth actions")
                        DropdownMenu(expanded = wealthActionsExpanded, onDismissRequest = { wealthActionsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Buy or sell") },
                                onClick = {
                                    wealthActionsExpanded = false
                                    navigation = navigation.navigate(V2Route.BuyAsset)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add holding") },
                                onClick = {
                                    wealthActionsExpanded = false
                                    navigation = navigation.navigate(V2Route.AddHolding)
                                },
                            )
                        }
                    }
                } else {
                    MzFab(onClick = {
                        navigation =
                            navigation.navigate(if (selectedTab == V2Route.Budgets) V2Route.NewBudget else V2Route.ExpenseEntry)
                    })
                }
            }
        },
    ) { padding ->
        if (activeRoute == null && selectedTab == V2Route.Home) {
            Box(Modifier.padding(padding)) {
                V2HomeScreen(repository, hideAmounts) { destination -> navigation = navigation.navigate(destination) }
            }
        } else if (activeRoute == null && selectedTab == V2Route.Records) {
            Box(Modifier.padding(padding)) {
                V2RecordsScreen(
                    repository = repository,
                    period = period,
                    selectedAccountId = accountFilterId,
                    hideAmounts = hideAmounts,
                    onOpenPeriod = { navigation = navigation.navigate(V2Route.PeriodSelector) },
                    onOpenDetail = { id -> navigation = navigation.navigate(V2Route.RecordDetail(id)) },
                    onEdit = { id -> navigation = navigation.navigate(V2Route.EditEntry(id)) },
                    onOpenInbox = { navigation = navigation.navigate(V2Route.Inbox) },
                    onAddExpense = { navigation = navigation.navigate(V2Route.ExpenseEntry) },
                    onManageLabels = { navigation = navigation.navigate(V2Route.Categories) },
                    onAccountSelected = { id -> scope.launch { repository.setV2AccountFilter(id) } },
                    initialCategoryId = selectedStatsCategory,
                )
            }
        } else if ((activeRoute == null && selectedTab == V2Route.Budgets) ||
            activeRoute in setOf(V2Route.Budgets, V2Route.PlannedPayments)
        ) {
            val section = activeRoute ?: selectedTab
            Box(Modifier.padding(padding)) {
                V2PlanScreen(
                    repository = repository,
                    section = section,
                    hideAmounts = hideAmounts,
                    onOpenPeriod = { navigation = navigation.navigate(V2Route.PeriodSelector) },
                    onNavigate = { navigation = navigation.navigate(it) },
                    onSelectPlanTab = { route ->
                        if (route ==
                            V2Route.Budgets
                        ) {
                            navigation = navigation.selectTab(V2Route.Budgets)
                        } else {
                            navigation = navigation.navigate(route)
                        }
                    },
                )
            }
        } else if (activeRoute == V2Route.Goals) {
            Box(Modifier.padding(padding)) { V2GoalsScreen(repository, hideAmounts) { navigation = navigation.navigate(it) } }
        } else if (activeRoute == V2Route.NewGoal || activeRoute is V2Route.EditGoal) {
            Box(Modifier.padding(padding)) {
                V2GoalForm(repository, (activeRoute as? V2Route.EditGoal)?.id, hideAmounts, onSaved = {
                    navigation =
                        navigation.back()
                }, onBack = { navigation = navigation.back() })
            }
        } else if (activeRoute == V2Route.Debts) {
            Box(Modifier.padding(padding)) { V2DebtsScreen(repository, hideAmounts) { navigation = navigation.navigate(it) } }
        } else if (activeRoute == V2Route.NewDebt || activeRoute is V2Route.EditDebt) {
            Box(Modifier.padding(padding)) {
                V2DebtForm(repository, (activeRoute as? V2Route.EditDebt)?.id, onSaved = {
                    navigation =
                        navigation.back()
                }, onBack = { navigation = navigation.back() })
            }
        } else if (activeRoute == V2Route.NewBudget || activeRoute is V2Route.EditBudget) {
            Box(Modifier.padding(padding)) {
                V2BudgetForm(repository, (activeRoute as? V2Route.EditBudget)?.id, hideAmounts = hideAmounts, onSaved = {
                    navigation =
                        navigation.back()
                }, onBack = {
                    navigation =
                        navigation.back()
                })
            }
        } else if (activeRoute == V2Route.NewPlannedPayment || activeRoute is V2Route.EditPlannedPayment) {
            Box(Modifier.padding(padding)) {
                V2PlannedPaymentForm(repository, (activeRoute as? V2Route.EditPlannedPayment)?.id, onSaved = {
                    navigation =
                        navigation.back()
                }, onBack = { navigation = navigation.back() })
            }
        } else if (activeRoute is V2Route.PlannedEntry) {
            V2EntryScreen(
                repository = repository,
                initialKind = "EXPENSE",
                lastAccountId = lastAccountId,
                hideAmounts = hideAmounts,
                plannedPaymentId = activeRoute.id,
                plannedDueDate = activeRoute.dueDate,
                modifier = Modifier.padding(padding),
                onDone = { navigation = navigation.back() },
            )
        } else if (activeRoute == null && selectedTab == V2Route.BalanceStats) {
            Box(Modifier.padding(padding)) {
                V2StatsScreen(
                    repository = repository,
                    periodState = period,
                    accountFilterId = accountFilterId,
                    hideAmounts = hideAmounts,
                    selectedPage = selectedStatsPage,
                    onPageSelected = { selectedStatsPage = it },
                    onOpenPeriod = { navigation = navigation.navigate(V2Route.PeriodSelector) },
                    onAccountSelected = { id -> scope.launch { repository.setV2AccountFilter(id) } },
                    onGoDeeper = { category ->
                        selectedStatsCategory = category
                        navigation = navigation.selectTab(V2Route.Records)
                    },
                )
            }
        } else if (activeRoute == null && selectedTab == V2Route.Wealth) {
            Box(Modifier.padding(padding)) {
                V2WealthScreen(
                    repository = repository,
                    hideAmounts = hideAmounts,
                    onOpenAccount = { id -> navigation = navigation.navigate(V2Route.AccountDetail(id)) },
                    onOpenHolding = { id -> navigation = navigation.navigate(V2Route.AssetDetail(id)) },
                    onUpdatePrices = { navigation = navigation.navigate(V2Route.Prices) },
                )
            }
        } else if (activeRoute == V2Route.PeriodSelector) {
            Box(Modifier.padding(padding)) {
                if (selectedTab == V2Route.Records) {
                    V2RecordsScreen(
                        repository = repository,
                        period = period,
                        selectedAccountId = accountFilterId,
                        hideAmounts = hideAmounts,
                        onOpenPeriod = {},
                        onOpenDetail = { id -> navigation = navigation.navigate(V2Route.RecordDetail(id)) },
                        onEdit = { id -> navigation = navigation.navigate(V2Route.EditEntry(id)) },
                        onOpenInbox = { navigation = navigation.navigate(V2Route.Inbox) },
                        onAddExpense = { navigation = navigation.navigate(V2Route.ExpenseEntry) },
                        onManageLabels = { navigation = navigation.navigate(V2Route.Categories) },
                        onAccountSelected = { id -> scope.launch { repository.setV2AccountFilter(id) } },
                    )
                } else if (selectedTab == V2Route.BalanceStats) {
                    V2StatsScreen(
                        repository = repository,
                        periodState = period,
                        accountFilterId = accountFilterId,
                        hideAmounts = hideAmounts,
                        selectedPage = selectedStatsPage,
                        onPageSelected = { selectedStatsPage = it },
                        onOpenPeriod = {},
                        onAccountSelected = { id -> scope.launch { repository.setV2AccountFilter(id) } },
                        onGoDeeper = { category ->
                            selectedStatsCategory = category
                            navigation = navigation.selectTab(V2Route.Records)
                        },
                    )
                } else {
                    V2Placeholder(
                        route = selectedTab,
                        entryAccountId = lastAccountId,
                        globalState = V2GlobalState(hideAmounts, period, accountFilterId, baseCurrency, lastAccountId),
                        onSelectPeriod = { persisted ->
                            scope.launch { repository.setV2Period(persisted.kind, persisted.start, persisted.end) }
                        },
                        onBack = { navigation = navigation.back() },
                        onNavigate = { destination -> navigation = navigation.navigate(destination) },
                        onDisableNewInterface = { scope.launch { repository.setNewInterface(false) } },
                    )
                }
            }
            V2PeriodSelectorSheet(
                initialPeriod = period,
                accounts = accounts,
                selectedAccountIds =
                    accountFilterId
                        ?.split('|')
                        ?.filter(String::isNotBlank)
                        ?.toSet()
                        .orEmpty(),
                onApply = { selected, selectedAccounts ->
                    scope.launch {
                        repository.setV2Period(selected.kind.name, selected.start?.toString(), selected.end?.toString())
                        repository.setV2AccountFilter(selectedAccounts.sorted().takeIf { it.isNotEmpty() }?.joinToString("|"))
                        navigation = navigation.back()
                    }
                },
                onDismiss = { navigation = navigation.back() },
            )
        } else if (activeRoute in setOf(V2Route.BalanceStats, V2Route.CashFlowStats, V2Route.SpendingStats)) {
            val routePage =
                when (activeRoute) {
                    V2Route.CashFlowStats -> "Cash flow"
                    V2Route.SpendingStats -> "Spending"
                    else -> "Balance"
                }
            Box(Modifier.padding(padding)) {
                V2StatsScreen(
                    repository = repository,
                    periodState = period,
                    accountFilterId = accountFilterId,
                    hideAmounts = hideAmounts,
                    selectedPage = routePage,
                    onPageSelected = { selectedStatsPage = it },
                    onOpenPeriod = { navigation = navigation.navigate(V2Route.PeriodSelector) },
                    onAccountSelected = { id -> scope.launch { repository.setV2AccountFilter(id) } },
                    onGoDeeper = { category ->
                        selectedStatsCategory = category
                        navigation = navigation.navigate(V2Route.Records)
                    },
                )
            }
        } else if (activeRoute is V2Route.AccountDetail) {
            Box(Modifier.padding(padding)) {
                V2AccountDetailScreen(repository, activeRoute.id, hideAmounts, onNavigate = { destination ->
                    when (destination) {
                        V2Route.Records -> {
                            scope.launch {
                                repository.setV2AccountFilter(activeRoute.id)
                                navigation =
                                    navigation.navigate(destination)
                            }
                        }

                        V2Route.ExpenseEntry, V2Route.IncomeEntry -> {
                            scope.launch {
                                repository.setLastUsedAccountId(activeRoute.id)
                                navigation =
                                    navigation.navigate(destination)
                            }
                        }

                        V2Route.TransferEntry -> {
                            scope.launch {
                                val cashAccount =
                                    accounts
                                        .firstOrNull {
                                            it.id != activeRoute.id &&
                                                it.type in setOf("CASH", "BANK", "EWALLET")
                                        }?.id
                                if (cashAccount != null) repository.setLastUsedAccountId(cashAccount)
                                navigation = navigation.navigate(V2Route.CardPayment(activeRoute.id))
                            }
                        }

                        else -> {
                            navigation = navigation.navigate(destination)
                        }
                    }
                }, onBack = { navigation = navigation.back() })
            }
        } else if (activeRoute is V2Route.AssetDetail) {
            Box(Modifier.padding(padding)) {
                V2AssetDetailScreen(
                    repository = repository,
                    assetId = activeRoute.id,
                    hideAmounts = hideAmounts,
                    onTrade = { id, selling -> navigation = navigation.navigate(V2Route.AssetTrade(id, selling)) },
                )
            }
        } else if (activeRoute == V2Route.BuyAsset || activeRoute is V2Route.AssetTrade) {
            val tradeRoute = activeRoute as? V2Route.AssetTrade
            Box(Modifier.padding(padding)) {
                V2AssetTradeScreen(
                    repository = repository,
                    initialAssetId = tradeRoute?.id,
                    initialSelling = tradeRoute?.selling ?: false,
                    hideAmounts = hideAmounts,
                    onSaved = { id ->
                        navigation =
                            if (tradeRoute != null) {
                                navigation.back().navigate(V2Route.AssetDetail(id))
                            } else {
                                navigation.back()
                            }
                    },
                )
            }
        } else if (activeRoute == V2Route.AddHolding) {
            Box(Modifier.padding(padding)) {
                V2AddHoldingScreen(repository) { navigation = navigation.back() }
            }
        } else if (activeRoute == V2Route.Wealth) {
            Box(Modifier.padding(padding)) {
                V2WealthAccountsScreen(repository, hideAmounts) { id -> navigation = navigation.navigate(V2Route.AccountDetail(id)) }
            }
        } else if (activeRoute == V2Route.NewAccount || activeRoute is V2Route.EditAccount) {
            Box(Modifier.padding(padding)) {
                V2AccountForm(repository, (activeRoute as? V2Route.EditAccount)?.id, onSaved = { id ->
                    navigation =
                        navigation.back().navigate(V2Route.AccountDetail(id))
                }, onBack = { navigation = navigation.back() })
            }
        } else if (activeRoute in setOf(V2Route.ExpenseEntry, V2Route.IncomeEntry, V2Route.TransferEntry) ||
            activeRoute is V2Route.CardPayment ||
            activeRoute is V2Route.EditEntry || activeRoute is V2Route.InboxDraftEntry
        ) {
            V2EntryScreen(
                repository = repository,
                initialKind =
                    when (activeRoute) {
                        V2Route.IncomeEntry -> "INCOME"
                        V2Route.TransferEntry -> "TRANSFER"
                        is V2Route.CardPayment -> "TRANSFER"
                        else -> "EXPENSE"
                    },
                lastAccountId = lastAccountId,
                hideAmounts = hideAmounts,
                editTransactionId = (activeRoute as? V2Route.EditEntry)?.id,
                inboxDraftId = (activeRoute as? V2Route.InboxDraftEntry)?.id,
                presetTransferTargetId = (activeRoute as? V2Route.CardPayment)?.cardId,
                modifier = Modifier.padding(padding),
                onDone = { navigation = navigation.back() },
            )
        } else if (activeRoute is V2Route.RecordDetail) {
            V2RecordDetailScreen(
                repository = repository,
                transactionId = activeRoute.id,
                hideAmounts = hideAmounts,
                onEdit = { navigation = navigation.navigate(V2Route.EditEntry(activeRoute.id)) },
                onBack = { navigation = navigation.back() },
                onDuplicated = { id -> navigation = navigation.navigate(V2Route.EditEntry(id)) },
            )
        } else if (activeRoute == V2Route.Inbox) {
            V2InboxScreen(
                repository = repository,
                hideAmounts = hideAmounts,
                onOpen = { id -> navigation = navigation.navigate(V2Route.InboxDraftEntry(id)) },
            )
        } else {
            V2RouteHost(
                route = activeRoute ?: selectedTab,
                padding = padding,
                onBack = { navigation = navigation.back() },
                onNavigate = { destination -> navigation = navigation.navigate(destination) },
                onDisableNewInterface = { scope.launch { repository.setNewInterface(false) } },
                entryAccountId = lastAccountId,
                globalState = V2GlobalState(hideAmounts, period, accountFilterId, baseCurrency, lastAccountId),
                onSelectPeriod = { persisted ->
                    scope.launch { repository.setV2Period(persisted.kind, persisted.start, persisted.end) }
                },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun MzIconButton(
    icon: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(MzSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        MzIcon(icon, description, tint = LocalMzTokens.current.text)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun V2RouteHost(
    route: V2Route,
    padding: PaddingValues,
    onBack: () -> Unit,
    onNavigate: (V2Route) -> Unit,
    onDisableNewInterface: () -> Unit,
    entryAccountId: String?,
    globalState: V2GlobalState,
    onSelectPeriod: (PersistedPeriod) -> Unit,
) {
    if (route.presentation == V2Presentation.SHEET) {
        Box(modifier = Modifier.fillMaxSize().background(LocalMzTokens.current.canvas).padding(padding))
        MzSheet(onDismissRequest = onBack) {
            V2Placeholder(route, entryAccountId, globalState, onSelectPeriod, onBack, onNavigate, onDisableNewInterface)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(LocalMzTokens.current.canvas).padding(padding)) {
            V2Placeholder(route, entryAccountId, globalState, onSelectPeriod, onBack, onNavigate, onDisableNewInterface)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun V2Placeholder(
    route: V2Route,
    entryAccountId: String?,
    globalState: V2GlobalState,
    onSelectPeriod: (PersistedPeriod) -> Unit,
    onBack: () -> Unit,
    onNavigate: (V2Route) -> Unit,
    onDisableNewInterface: () -> Unit,
) {
    var showRouteCatalog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val description =
        buildString {
            append("Not built yet. This destination is registered and ready for its real screen.")
            if (route in setOf(V2Route.ExpenseEntry, V2Route.IncomeEntry, V2Route.TransferEntry)) {
                append(" Last used account: ")
                append(entryAccountId ?: "none")
                append('.')
            }
            if (route == V2Route.PeriodSelector) append(" Shared period: ${globalState.period.kind.name.lowercase()}.")
        }
    if (showRouteCatalog) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MzSpacing.sideMargin),
        ) {
            item { Text("All V2 destinations", style = MzTypography.title) }
            items(V2Route.placeholders, key = V2Route::path) { destination ->
                MzListRow(
                    iconName = "arrow_forward",
                    iconDescription = destination.title,
                    title = destination.title,
                    subtitle = destination.path,
                    modifier = Modifier.clickable { onNavigate(destination) },
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            MzEmptyState(
                title = route.title,
                description = description,
                actionLabel =
                    when (route) {
                        V2Route.PeriodSelector -> "Use all time"
                        V2Route.BalanceStats -> "Period · ${globalState.period.kind.name.lowercase()}"
                        else -> "Back"
                    },
                onAction = {
                    when (route) {
                        V2Route.PeriodSelector -> onSelectPeriod(PeriodState(PeriodKind.ALL).persisted())
                        V2Route.BalanceStats -> onNavigate(V2Route.PeriodSelector)
                        else -> onBack()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (route == V2Route.About) {
                MzToggle(
                    checked = true,
                    onCheckedChange = { enabled -> if (!enabled) onDisableNewInterface() },
                    label = "New interface",
                    modifier = Modifier.padding(horizontal = MzSpacing.sideMargin),
                )
            }
            MzButton(
                label = if (route == V2Route.Settings) "About" else "Browse destinations",
                onClick = { if (route == V2Route.Settings) onNavigate(V2Route.About) else showRouteCatalog = true },
                modifier = Modifier.padding(horizontal = MzSpacing.sideMargin),
            )
        }
    }
}
