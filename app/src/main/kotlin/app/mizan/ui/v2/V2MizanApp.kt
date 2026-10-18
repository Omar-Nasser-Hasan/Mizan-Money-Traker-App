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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val hideAmounts by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    val periodKind by repository.observeV2PeriodKind().collectAsStateWithLifecycle("MONTH")
    val periodStart by repository.observeV2PeriodStart().collectAsStateWithLifecycle(null)
    val periodEnd by repository.observeV2PeriodEnd().collectAsStateWithLifecycle(null)
    val accountFilterId by repository.observeV2AccountFilter().collectAsStateWithLifecycle(null)
    val baseCurrency by repository.observeV2BaseCurrency().collectAsStateWithLifecycle("EGP")
    val lastAccountId by repository.observeLastUsedAccountId().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var navigation by rememberSaveable(stateSaver = V2NavigationState.Saver) { mutableStateOf(V2NavigationState()) }
    val period = PeriodState.fromPersisted(periodKind, periodStart, periodEnd)

    LaunchedEffect(quickAction) {
        quickEntryRoute(quickAction)?.let { navigation = navigation.navigate(it) }
    }

    // The legacy lock is an Activity gate, so it completes before either shell is composed. Empty
    // accounts retain the existing first-run gate before root navigation is exposed.
    if (V2StartupGate.next(accounts.isNotEmpty()) != null) {
        onboardingScreen(repository, Modifier.fillMaxSize(), onDone = {})
        return
    }

    val activeRoute = navigation.activeRoute
    val selectedTab = navigation.selectedTab
    val isSheetRoute = activeRoute?.presentation == V2Presentation.SHEET
    Scaffold(
        containerColor = LocalMzTokens.current.canvas,
        topBar = {
            MzTopBar(
                title = if (isSheetRoute) selectedTab.title else activeRoute?.title ?: selectedTab.title,
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
            if (activeRoute == null) MzFab(onClick = { navigation = navigation.navigate(V2Route.ExpenseEntry) })
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
        } else if (activeRoute in setOf(V2Route.ExpenseEntry, V2Route.IncomeEntry, V2Route.TransferEntry) ||
            activeRoute is V2Route.EditEntry || activeRoute is V2Route.InboxDraftEntry
        ) {
            V2EntryScreen(
                repository = repository,
                initialKind =
                    when (activeRoute) {
                        V2Route.IncomeEntry -> "INCOME"
                        V2Route.TransferEntry -> "TRANSFER"
                        else -> "EXPENSE"
                    },
                lastAccountId = lastAccountId,
                hideAmounts = hideAmounts,
                editTransactionId = (activeRoute as? V2Route.EditEntry)?.id,
                inboxDraftId = (activeRoute as? V2Route.InboxDraftEntry)?.id,
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
