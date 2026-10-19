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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch

/** The single release shell. Financial destinations share the persisted period and privacy state. */
@Composable
@Suppress("ktlint:standard:function-naming")
fun v2MizanApp(
    repository: MizanRepository,
    quickAction: String? = null,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val hideAmounts by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    val periodKind by repository.observeV2PeriodKind().collectAsStateWithLifecycle("MONTH")
    val periodStart by repository.observeV2PeriodStart().collectAsStateWithLifecycle(null)
    val periodEnd by repository.observeV2PeriodEnd().collectAsStateWithLifecycle(null)
    val accountFilterId by repository.observeV2AccountFilter().collectAsStateWithLifecycle(null)
    val lastAccountId by repository.observeLastUsedAccountId().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var navigation by rememberSaveable(stateSaver = V2NavigationState.Saver) { mutableStateOf(V2NavigationState()) }
    var selectedStatsPage by rememberSaveable { mutableStateOf("Cash flow") }
    var selectedStatsCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var wealthActionsExpanded by rememberSaveable { mutableStateOf(false) }
    val preferences by repository.settings.snapshots.collectAsStateWithLifecycle(
        app.mizan.data.settings
            .SettingsSnapshot(),
    )
    val period = PeriodState.fromPersisted(periodKind, periodStart, periodEnd).copy(weekStart = preferences.weekStart)

    LaunchedEffect(quickAction) {
        quickEntryRoute(quickAction)?.let { navigation = navigation.navigate(it) }
    }

    val activeRoute = navigation.activeRoute
    val selectedTab = navigation.selectedTab
    val isSheetRoute = activeRoute?.presentation == V2Presentation.SHEET
    Scaffold(
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().safeDrawingPadding(),
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
        } else if (activeRoute == V2Route.Zakat) {
            Box(Modifier.padding(padding)) {
                V2ZakatScreen(repository, hideAmounts) {
                    scope.launch {
                        repository.prepareZakatEntry()
                        navigation = navigation.navigate(V2Route.ZakatEntry)
                    }
                }
            }
        } else if (activeRoute == V2Route.WalletImport) {
            Box(Modifier.padding(padding)) { V2WalletImportScreen(repository) }
        } else if (activeRoute == V2Route.Backup) {
            Box(Modifier.padding(padding)) { V2BackupScreen(repository) }
        } else if (activeRoute == V2Route.About) {
            Box(Modifier.padding(padding)) { V2AboutScreen(repository) }
        } else if (activeRoute == V2Route.QuickEntrySettings) {
            Box(Modifier.padding(padding)) { V2QuickSettingsScreen(repository) { navigation = navigation.navigate(it) } }
        } else if (activeRoute == V2Route.VoiceReview) {
            Box(Modifier.padding(padding)) {
                V2VoiceReviewScreen(repository, hideAmounts) { navigation = navigation.back() }
            }
        } else if (activeRoute == V2Route.Export) {
            Box(Modifier.padding(padding)) { V2ExportScreen(repository) }
        } else if (activeRoute == V2Route.Trash) {
            Box(Modifier.padding(padding)) { V2TrashScreen(repository) }
        } else if (activeRoute == V2Route.More) {
            Box(Modifier.padding(padding)) { V2MoreScreen { navigation = navigation.navigate(it) } }
        } else if (activeRoute == V2Route.DataSources) {
            Box(Modifier.padding(padding)) { V2DataSourcesScreen(repository) { navigation = navigation.navigate(it) } }
        } else if (activeRoute == V2Route.HyperOsGuide || activeRoute == V2Route.RestrictedSettingsGuide) {
            Box(Modifier.padding(padding)) { V2PhoneGuideScreen(activeRoute == V2Route.RestrictedSettingsGuide) }
        } else if (activeRoute == V2Route.Templates) {
            Box(Modifier.padding(padding)) { V2TemplatesScreen(repository) { navigation = navigation.navigate(V2Route.NewAccount) } }
        } else if (activeRoute == V2Route.Categories) {
            Box(Modifier.padding(padding)) { V2ReferenceSettingsScreen(repository) }
        } else if (activeRoute == V2Route.Currencies) {
            Box(Modifier.padding(padding)) { V2CurrenciesScreen(repository) }
        } else if (activeRoute == V2Route.Notifications) {
            Box(Modifier.padding(padding)) { V2NotificationsScreen(repository) }
        } else if (activeRoute == V2Route.Settings) {
            Box(Modifier.padding(padding)) { V2SettingsScreen(repository) { navigation = navigation.navigate(it) } }
        } else if (activeRoute == V2Route.SecuritySettings) {
            Box(Modifier.padding(padding)) { V2SecuritySettingsScreen(repository) }
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
                    V2HomeScreen(repository, hideAmounts) { destination -> navigation = navigation.navigate(destination) }
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
        } else if (activeRoute == V2Route.Prices) {
            Box(Modifier.padding(padding)) {
                V2PricesScreen(repository, hideAmounts) { navigation = navigation.navigate(V2Route.AddHolding) }
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
        } else if (activeRoute in setOf(V2Route.ExpenseEntry, V2Route.IncomeEntry, V2Route.TransferEntry, V2Route.ZakatEntry) ||
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
                zakatPayment = activeRoute == V2Route.ZakatEntry,
                modifier = Modifier.padding(padding),
                onOpenVoice = { navigation = navigation.navigate(V2Route.VoiceReview) },
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
                lastAccountId = lastAccountId,
                onConfirm = { draft ->
                    if (draft.kind == "REVERSAL") {
                        scope.launch {
                            val accountId = lastAccountId ?: accounts.firstOrNull { !it.archived }?.id
                            if (accountId != null) {
                                repository.postInboxAdjustment(draft.id, accountId, draft.currency, draft.amountMinor)
                            }
                        }
                    } else {
                        navigation = navigation.navigate(V2Route.InboxDraftEntry(draft.id))
                    }
                },
                onEdit = { id -> navigation = navigation.navigate(V2Route.InboxDraftEntry(id)) },
                onBack = { navigation = navigation.back() },
            )
        } else {
            Box(Modifier.padding(padding)) {
                MzEmptyState(
                    title = stringResource(R.string.route_unavailable),
                    description = stringResource(R.string.route_unavailable_note),
                    actionLabel = stringResource(R.string.return_home),
                    onAction = { navigation = navigation.selectTab(V2Route.Home) },
                )
            }
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
