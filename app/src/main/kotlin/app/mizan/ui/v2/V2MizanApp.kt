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
    val startupRoute = V2StartupGate.destination(accounts.isNotEmpty(), quickAction)
    if (V2StartupGate.next(accounts.isNotEmpty()) != null) {
        V2RouteHost(
            route = startupRoute ?: V2Route.OnboardingAccounts,
            padding = PaddingValues(),
            onBack = {},
            onNavigate = {},
            onDisableNewInterface = {},
            entryAccountId = lastAccountId,
            globalState = V2GlobalState(hideAmounts, period, accountFilterId, baseCurrency, lastAccountId),
            onSelectPeriod = { persisted ->
                scope.launch { repository.setV2Period(persisted.kind, persisted.start, persisted.end) }
            },
        )
        return
    }

    val activeRoute = navigation.activeRoute
    val selectedTab = navigation.selectedTab
    Scaffold(
        containerColor = LocalMzTokens.current.canvas,
        topBar = {
            MzTopBar(
                title = activeRoute?.title ?: selectedTab.title,
                navigationIcon =
                    if (activeRoute == null) {
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
            if (activeRoute == null) {
                MzBottomNav(
                    selectedLabel = selectedTab.title,
                    onSelected = { label ->
                        navigation = navigation.selectTab(V2NavigationState.rootTabs.first { it.title == label })
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
        } else if (activeRoute in setOf(V2Route.ExpenseEntry, V2Route.IncomeEntry, V2Route.TransferEntry)) {
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
                onDone = { navigation = navigation.back() },
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
                actionLabel = if (route == V2Route.PeriodSelector) "Use all time" else "Back",
                onAction = {
                    if (route == V2Route.PeriodSelector) onSelectPeriod(PeriodState(PeriodKind.ALL).persisted()) else onBack()
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
