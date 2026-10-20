// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.GoalLinkEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.plan.GoalMath
import app.mizan.domain.plan.GoalProgress
import app.mizan.domain.plan.GoalStatus
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val goalTones =
    listOf(
        "#D8AF5A",
        "#8BBAA0",
        "#6DA9C8",
        "#9B8AC8",
        "#D98274",
        "#D99B65",
        "#76B9AE",
        "#B58FA9",
        "#91A76B",
        "#7F98C8",
        "#D6C169",
        "#E7C89A",
    )
private val goalIcons =
    listOf(
        "flag",
        "flight",
        "shield",
        "laptop",
        "home",
        "school",
        "savings",
        "directions_car",
        "favorite",
        "star",
        "diamond",
        "account_balance",
    )

@Composable
fun V2GoalsScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onNavigate: (V2Route) -> Unit,
) {
    val goals by remember(repository) { repository.observeGoals() }.collectAsStateWithLifecycle(emptyList())
    val links by remember(repository) { repository.observeAllGoalLinks() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val trades by remember(repository) { repository.observeAllAssetTransactions() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observeLatestPrices() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val rates by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    val cashFlows by remember(repository) { repository.observeStatsEntries() }.collectAsStateWithLifecycle(emptyList())
    val allContributions by remember(repository) { repository.observeAllGoalContributions() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val cards =
        remember(goals, links, balances, accounts, assets, trades, prices, currencies, rates, cashFlows, today) {
            GoalDebtCalculations.goals(
                goals,
                links,
                allContributions,
                balances,
                accounts,
                assets,
                trades,
                prices,
                currencies,
                rates,
                today,
                cashFlows,
            )
        }
    var tab by rememberSaveable { mutableStateOf("ACTIVE") }
    var selected by remember { mutableStateOf<String?>(null) }
    val selectedContributionsFlow = remember(selected) { selected?.let(repository::observeGoalContributions) }
    val selectedContributionsState = selectedContributionsFlow?.collectAsStateWithLifecycle(emptyList())
    val contributions = selectedContributionsState?.value.orEmpty()
    val visible = cards.filter { it.goal.status == tab }
    Column(Modifier.fillMaxSize()) {
        MzSegmented(listOf("Budgets", "Planned", "Goals", "Debts"), 2, { index ->
            onNavigate(
                if (index ==
                    0
                ) {
                    V2Route.Budgets
                } else if (index ==
                    1
                ) {
                    V2Route.PlannedPayments
                } else if (index ==
                    2
                ) {
                    V2Route.Goals
                } else {
                    V2Route.Debts
                },
            )
        }, Modifier.padding(horizontal = MzSpacing.sideMargin, vertical = 8.dp))
        MzSegmented(listOf("Active", "Paused", "Reached"), listOf("ACTIVE", "PAUSED", "REACHED").indexOf(tab), {
            tab =
                listOf("ACTIVE", "PAUSED", "REACHED")[it]
        }, Modifier.padding(horizontal = MzSpacing.sideMargin))
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = MzSpacing.sideMargin),
            verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(vertical = 12.dp),
        ) {
            val active = cards.filter { it.goal.status == "ACTIVE" }
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved of target", style = MzTypography.labelCaps)
                        GoalAmountLine("Saved", active.sumOf { it.progress.progressMinor }, hideAmounts)
                        GoalAmountLine("Target", active.sumOf { it.goal.targetMinor }, hideAmounts)
                        val pct =
                            active.sumOf { it.progress.progressMinor }.takeIf { active.isNotEmpty() }?.let { saved ->
                                val target = active.sumOf { it.goal.targetMinor }
                                if (target > 0) (saved * 100 / target).coerceAtMost(100) else 0
                            } ?: 0
                        MzBudgetBar(pct / 100f)
                        Text("${if (hideAmounts) "•••" else "$pct%"} across ${active.size} active goals", style = MzTypography.bodySmall)
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    MzCard(Modifier.fillMaxWidth()) {
                        Text(
                            "No ${tab.lowercase()} goals yet. Use + to add one.",
                            Modifier.padding(18.dp),
                            style = MzTypography.bodySmall,
                        )
                    }
                }
            }
            items(visible, key = { it.goal.id }) { card ->
                MzCard(Modifier.fillMaxWidth().clickable { selected = card.goal.id }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(
                                        38.dp,
                                    ).clip(
                                        CircleShape,
                                    ).background(
                                        androidx.compose.ui.graphics
                                            .Color(android.graphics.Color.parseColor(card.goal.color)),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                MzIcon(card.goal.icon, card.goal.name, tint = LocalMzTokens.current.canvas)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(card.goal.name, style = MzTypography.title)
                                Text(
                                    card.sources.firstOrNull()?.let { "Backed by ${it.name}" } ?: "Manual contributions",
                                    style = MzTypography.bodySmall,
                                )
                            }
                            Text(
                                if (hideAmounts) {
                                    "•••"
                                } else {
                                    "${card.progress.progressMinor
                                        .takeIf { card.goal.targetMinor > 0 }
                                        ?.let { it * 100 / card.goal.targetMinor } ?: 0}%"
                                },
                                style = MzTypography.title,
                            )
                        }
                        MzBudgetBar(if (card.goal.targetMinor > 0) card.progress.progressMinor.toFloat() / card.goal.targetMinor else 0f)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AmountText(card.progress.progressMinor, "EGP", hideAmounts = hideAmounts)
                            AmountText(card.goal.targetMinor, "EGP", hideAmounts = hideAmounts)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                card.goal.targetDate?.let { target -> "Target $target · ETA ${card.estimatedFinish ?: "—"}" }
                                    ?: "No target date",
                                style = MzTypography.bodySmall,
                            )
                            Text("Monthly needed", style = MzTypography.bodySmall)
                            AmountText(
                                card.progress.monthlyNeeded(today),
                                "EGP",
                                hideAmounts = hideAmounts,
                                textStyle = MzTypography.bodySmall,
                            )
                        }
                        if (card.goal.status == "ACTIVE" && card.progress.needsReachedPrompt) {
                            MzButton(
                                "Mark as reached",
                                { scope.launch { repository.setGoalStatus(card.goal.id, "REACHED") } },
                                Modifier.fillMaxWidth(),
                            )
                        } else if (card.goal.status == "ACTIVE" && card.monthsBehind > 0L) {
                            Text(
                                "Behind by ${card.monthsBehind} months",
                                style = MzTypography.bodySmall,
                                color = LocalMzTokens.current.warning,
                            )
                        } else if (card.goal.status == "ACTIVE") {
                            Text("On track", style = MzTypography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    selected?.let { id ->
        val card = cards.firstOrNull { it.goal.id == id }
        if (card != null) {
            MzSheet(onDismissRequest = { selected = null }) {
                Text(card.goal.name, style = MzTypography.title)
                Text("Linked sources", style = MzTypography.labelCaps)
                if (card.sources.isEmpty()) Text("No linked sources", style = MzTypography.bodySmall)
                card.sources.forEach { source ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${source.name} · ${if (hideAmounts) "•••" else "${source.allocationPct}%"}", style = MzTypography.bodySmall)
                        AmountText(
                            source.valueMinor * source.allocationPct / 100L,
                            "EGP",
                            hideAmounts = hideAmounts,
                            textStyle = MzTypography.bodySmall,
                        )
                    }
                }
                Text("Manual contributions", style = MzTypography.labelCaps)
                contributions.forEach { contribution ->
                    Text("${contribution.date} · ${contribution.note}", style = MzTypography.bodySmall)
                    AmountText(contribution.amountMinor, "EGP", hideAmounts = hideAmounts)
                }
                var amount by rememberSaveable(id) { mutableStateOf("") }
                var note by rememberSaveable(id) { mutableStateOf("") }
                MzInput(amount, { amount = it }, "Add contribution (EGP)")
                MzInput(note, { note = it }, "Note")
                MzButton("Add contribution", {
                    val minor = minorUnits(amount)
                    if (minor >
                        0L
                    ) {
                        scope.launch {
                            repository.saveGoalContribution(
                                GoalContributionEntity(UUID.randomUUID().toString(), id, today.toString(), minor, note),
                            )
                            amount =
                                ""
                            note = ""
                        }
                    }
                }, Modifier.fillMaxWidth(), enabled = minorUnits(amount) > 0)
                MzButton("Edit goal", {
                    selected = null
                    onNavigate(V2Route.EditGoal(id))
                }, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
                MzButton(
                    if (card.goal.status ==
                        "PAUSED"
                    ) {
                        "Resume goal"
                    } else {
                        "Pause goal"
                    },
                    {
                        scope.launch {
                            repository.setGoalStatus(
                                id,
                                if (card.goal.status ==
                                    "PAUSED"
                                ) {
                                    "ACTIVE"
                                } else {
                                    "PAUSED"
                                },
                            )
                            ; selected = null
                        }
                    },
                    Modifier.fillMaxWidth(),
                    MzButtonStyle.GHOST,
                )
                var deleted by rememberSaveable(id) { mutableStateOf(card.goal.status == "DELETED") }
                var restoreStatus by rememberSaveable(id) { mutableStateOf("ACTIVE") }
                if (deleted) {
                    Text("Goal deleted", style = MzTypography.bodySmall)
                    MzButton("Undo", {
                        scope.launch {
                            repository.setGoalStatus(id, restoreStatus)
                            deleted = false
                            selected = null
                        }
                    }, Modifier.fillMaxWidth())
                } else {
                    MzButton("Delete goal", {
                        restoreStatus = card.goal.status
                        scope.launch {
                            repository.setGoalStatus(id, "DELETED")
                            deleted = true
                        }
                    }, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
                }
            }
        }
    }
}

@Composable
fun V2GoalForm(
    repository: MizanRepository,
    goalId: String?,
    hideAmounts: Boolean = false,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val trades by remember(repository) { repository.observeAllAssetTransactions() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observeLatestPrices() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val rates by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    val cashFlows by remember(repository) { repository.observeStatsEntries() }.collectAsStateWithLifecycle(emptyList())
    val allLinks by remember(repository) { repository.observeAllGoalLinks() }.collectAsStateWithLifecycle(emptyList())
    val existingLinks by goalId?.let(repository::observeGoalLinks)?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(goalId) { mutableStateOf("") }
    var icon by rememberSaveable(goalId) { mutableStateOf("flag") }
    var color by rememberSaveable(goalId) { mutableStateOf(goalTones.first()) }
    var target by rememberSaveable(goalId) { mutableStateOf("") }
    var targetDate by rememberSaveable(goalId) { mutableStateOf("") }
    var deposit by rememberSaveable(goalId) { mutableStateOf("") }
    var status by rememberSaveable(goalId) { mutableStateOf("ACTIVE") }
    var selectedSources by rememberSaveable(goalId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(goalId, existingLinks) {
        if (goalId != null) {
            val goal = repository.goal(goalId) ?: return@LaunchedEffect
            name = goal.name
            icon = goal.icon
            color = goal.color
            target = BigDecimal.valueOf(goal.targetMinor, 2).toPlainString()
            targetDate = goal.targetDate.orEmpty()
            status = goal.status
            selectedSources =
                existingLinks.associate {
                    (if (it.assetId.isNotBlank()) "asset:${it.assetId}" else "account:${it.accountId}") to
                        it.allocationPct.toString()
                }
        }
    }
    val targets = minorUnits(target)
    val validTargetDate = targetDate.isBlank() || runCatching { LocalDate.parse(targetDate) }.isSuccess
    val amount = minorUnits(deposit)
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val linked =
        GoalDebtCalculations
            .goals(
                listOf(
                    GoalEntity(
                        "preview",
                        name,
                        targets,
                        targetDate.ifBlank {
                            null
                        },
                        icon,
                        color,
                        createdAt = 0,
                    ),
                ),
                existingLinks,
                emptyList(),
                balances,
                accounts,
                assets,
                trades,
                prices,
                currencies,
                rates,
                today,
                cashFlows,
            ).firstOrNull()
            ?.progress
            ?.linkedMinor
            ?: 0L
    val preview =
        GoalProgress(
            targets,
            linked,
            amount,
            targetDate.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    val otherAllocations =
        allLinks
            .filter {
                it.goalId != goalId
            }.groupBy { if (it.assetId.isNotBlank()) "asset:${it.assetId}" else "account:${it.accountId}" }
            .mapValues { (_, values) ->
                values.sumOf { it.allocationPct }
            }

    fun validAllocations() =
        selectedSources.all { (key, value) ->
            GoalMath.allocationIsValid(
                otherAllocations[key] ?: 0L,
                value.toLongOrNull() ?: -1L,
            )
        }
    val sources = accounts.map { "account:${it.id}" to it.name } + assets.map { "asset:${it.id}" to it.name }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 12.dp),
    ) {
        item { MzInput(name, { name = it }, "Goal name") }
        item {
            Text("Emblem", style = MzTypography.labelCaps)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                goalIcons.forEach { candidate ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (candidate ==
                                    icon
                                ) {
                                    LocalMzTokens.current.champagne
                                } else {
                                    LocalMzTokens.current.raisedSurface
                                },
                            ).clickable { icon = candidate },
                        contentAlignment = Alignment.Center,
                    ) {
                        MzIcon(
                            candidate,
                            "Choose $candidate emblem",
                            tint =
                                if (candidate ==
                                    icon
                                ) {
                                    LocalMzTokens.current.onChampagne
                                } else {
                                    LocalMzTokens.current.textDim
                                },
                        )
                    }
                }
            }
            Text("Palette tone", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                goalTones.forEach { tone ->
                    Box(
                        Modifier
                            .size(
                                34.dp,
                            ).clip(
                                CircleShape,
                            ).background(
                                androidx.compose.ui.graphics
                                    .Color(android.graphics.Color.parseColor(tone)),
                            ).clickable {
                                color =
                                    tone
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tone ==
                            color
                        ) {
                            Text("✓", color = androidx.compose.ui.graphics.Color.Black)
                        }
                    }
                }
            }
        }
        item {
            MzInput(target, { target = it }, "Target amount (EGP)")
            MzInput(targetDate, { targetDate = it }, "Target date (YYYY-MM-DD)")
            Text("A target a few years ahead spreads the monthly amount over a longer horizon.", style = MzTypography.bodySmall)
        }
        item { Text("Funding sources", style = MzTypography.labelCaps) }
        items(sources, key = { it.first }) { (key, label) ->
            val selected = key in selectedSources
            Column {
                MzChip(if (selected) "✓  $label" else label, selected, {
                    selectedSources =
                        if (selected) selectedSources - key else selectedSources + (key to "100")
                })
                if (selected) {
                    MzInput(selectedSources[key].orEmpty(), { value ->
                        selectedSources =
                            selectedSources + (key to value.filter(Char::isDigit))
                    }, "Allocation % · other goals use ${otherAllocations[key] ?: 0}%")
                }
            }
        }
        if (!validAllocations()) {
            item {
                Text(
                    "A source can be allocated at most 100% across goals.",
                    color = LocalMzTokens.current.warning,
                    style = MzTypography.bodySmall,
                )
            }
        }
        item { MzInput(deposit, { deposit = it }, "One-time initial deposit (EGP)") }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Projection", style = MzTypography.title)
                    GoalAmountLine("Monthly needed", preview.monthlyNeeded(today), hideAmounts)
                    Text(
                        "Estimated finish · ${preview.eta(
                            today,
                            preview.monthlyNeeded(today),
                        ) ?: targetDate.ifBlank { "Set a target date" }}",
                        style = MzTypography.bodySmall,
                    )
                }
            }
        }
        item {
            MzButton(if (goalId == null) "Create goal" else "Save changes", {
                if (name.isBlank() || targets <= 0L || !validTargetDate || !validAllocations()) return@MzButton
                val id = goalId ?: UUID.randomUUID().toString()
                val entity =
                    GoalEntity(
                        id,
                        name.trim(),
                        targets,
                        targetDate.takeIf(String::isNotBlank),
                        icon.ifBlank {
                            "flag"
                        },
                        color,
                        status,
                        System.currentTimeMillis(),
                    )
                val links =
                    selectedSources.map { (key, pct) ->
                        val value = key.removePrefix("account:").takeIf { key.startsWith("account:") }
                        val asset = key.removePrefix("asset:").takeIf { key.startsWith("asset:") }
                        GoalLinkEntity(id, value.orEmpty(), asset.orEmpty(), pct.toLongOrNull() ?: 0L)
                    }
                scope.launch {
                    repository.saveGoal(entity)
                    repository.replaceGoalLinks(id, links)
                    if (goalId == null &&
                        amount > 0L
                    ) {
                        repository.saveGoalContribution(
                            GoalContributionEntity(UUID.randomUUID().toString(), id, today.toString(), amount, "Initial deposit"),
                        )
                    }
                    onSaved()
                }
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && targets > 0L && validTargetDate && validAllocations())
            MzButton("Cancel", onBack, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
        }
    }
}

private fun minorUnits(value: String): Long = runCatching { BigDecimal(value).movePointRight(2).longValueExact() }.getOrDefault(0L)

@Composable
private fun GoalAmountLine(
    label: String,
    amount: Long,
    hideAmounts: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MzTypography.bodySmall)
        AmountText(amount, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
    }
}
