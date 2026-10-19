// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.mizan.ui.v2

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.BudgetStatus
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.Rollover
import app.mizan.notify.ReminderActionReceiver
import app.mizan.worker.PlanningReminderScheduler
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
fun V2PlanScreen(
    repository: MizanRepository,
    section: V2Route,
    hideAmounts: Boolean,
    onOpenPeriod: () -> Unit,
    onNavigate: (V2Route) -> Unit,
    onSelectPlanTab: (V2Route) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val budgets by remember(repository) { repository.observeBudgets() }.collectAsStateWithLifecycle(emptyList())
    val payments by remember(repository) { repository.observePlannedPayments() }.collectAsStateWithLifecycle(emptyList())
    val occurrences by remember(repository) { repository.observePlannedOccurrences() }.collectAsStateWithLifecycle(emptyList())
    val debts by remember(repository) { repository.observeDebts() }.collectAsStateWithLifecycle(emptyList())
    val debtRepayments by remember(repository) { repository.observeDebtRepaymentTotals() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val rows by remember(repository) { repository.observeStatsRows() }.collectAsStateWithLifecycle(emptyList())
    val entries by remember(repository) { repository.observeStatsEntries() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember(repository, "EXPENSE") { repository.observeCategories("EXPENSE") }.collectAsStateWithLifecycle(emptyList())
    val payees by remember(repository) { repository.observePayees() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val rates by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    var budgetPeriod by rememberSaveable { mutableStateOf(BudgetPeriod.MONTHLY.name) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).toString()) }
    val today = LocalDate.now(java.time.ZoneId.of("Africa/Cairo"))
    val page =
        remember(budgets, rows, entries, payments, occurrences, categories, payees, currencies, rates, budgetPeriod, anchor, today) {
            PlanCalculations.budgets(
                budgets,
                rows,
                entries,
                payments,
                occurrences,
                categories,
                payees,
                currencies,
                rates,
                BudgetPeriod.valueOf(budgetPeriod),
                LocalDate.parse(anchor),
                today,
            )
        }
    val plannedPage =
        remember(payments, occurrences, debts, debtRepayments, balances, accounts, rows, entries, categories, currencies, rates, today) {
            PlanCalculations.planned(
                payments,
                occurrences,
                debts,
                balances,
                accounts,
                rows,
                entries,
                categories,
                currencies,
                rates,
                today,
                debtRepayments,
            )
        }

    Column(Modifier.fillMaxSize()) {
        MzSegmented(
            options = listOf("Budgets", "Planned", "Goals", "Debts"),
            selectedIndex =
                listOf(
                    V2Route.Budgets,
                    V2Route.PlannedPayments,
                    V2Route.Goals,
                    V2Route.Debts,
                ).indexOf(section).coerceAtLeast(0),
            onSelected = { onSelectPlanTab(listOf(V2Route.Budgets, V2Route.PlannedPayments, V2Route.Goals, V2Route.Debts)[it]) },
            modifier = Modifier.padding(horizontal = MzSpacing.sideMargin, vertical = 8.dp),
        )
        if (section == V2Route.Budgets) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BudgetPeriod.entries.forEach { period ->
                            MzChip(period.name.lowercase().replaceFirstChar(Char::uppercase), budgetPeriod == period.name, {
                                budgetPeriod =
                                    period.name
                            })
                        }
                    }
                }
                item {
                    MzCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${page.percentBasisPoints / 100}% spent", style = MzTypography.title)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AmountLine("Spent", page.spentMinor, hideAmounts)
                                AmountLine("Left", page.leftMinor, hideAmounts)
                            }
                            MzBudgetBar(if (page.limitMinor == 0L) 0f else page.percentBasisPoints / 10_000f)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Pace", style = MzTypography.bodySmall)
                                AmountText(page.paceMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
                                Text("· ${page.remainingDays} days to go", style = MzTypography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MzChip("‹", false, {
                                    anchor =
                                        stepAnchor(LocalDate.parse(anchor), BudgetPeriod.valueOf(budgetPeriod), -1).toString()
                                })
                                Text(
                                    anchorCaption(LocalDate.parse(anchor), BudgetPeriod.valueOf(budgetPeriod)),
                                    style = MzTypography.bodySmall,
                                )
                                MzChip("›", false, {
                                    anchor =
                                        stepAnchor(LocalDate.parse(anchor), BudgetPeriod.valueOf(budgetPeriod), 1).toString()
                                })
                            }
                        }
                    }
                }
                if (page.cards.isEmpty()) {
                    item {
                        MzEmptyState(
                            "No budgets yet",
                            "Set a limit and keep spending on track.",
                            "New budget",
                            { onNavigate(V2Route.NewBudget) },
                        )
                    }
                }
                items(page.cards, key = { it.budget.id }) { card ->
                    var expanded by rememberSaveable(card.budget.id) { mutableStateOf(false) }
                    MzCard(Modifier.fillMaxWidth().clickable { onNavigate(V2Route.EditBudget(card.budget.id)) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(card.budget.name, style = MzTypography.title)
                                Text(statusLabel(card.status), style = MzTypography.labelCaps)
                            }
                            AmountLine("Spent", card.spentMinor, hideAmounts)
                            AmountLine("Limit", card.result.effectiveLimitMinor, hideAmounts)
                            AmountLine("Forecast", card.result.forecastMinor, hideAmounts)
                            MzBudgetBar(card.result.percentBasisPoints / 10_000f)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AmountText(
                                    (card.result.effectiveLimitMinor - card.spentMinor).coerceAtLeast(0),
                                    "EGP",
                                    hideAmounts = hideAmounts,
                                    textStyle = MzTypography.bodySmall,
                                )
                                Text("· ${card.remainingDays} days left", style = MzTypography.bodySmall)
                            }
                            MzButton(if (expanded) "Hide what it was spent on" else "What it was spent on", {
                                expanded = !expanded
                            }, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
                            if (expanded) {
                                Text("Categories", style = MzTypography.labelCaps)
                                if (card.categories.isEmpty()) Text("No category spending yet", style = MzTypography.bodySmall)
                                card.categories.forEach {
                                    AmountLine(it.name, it.amountMinor, hideAmounts, MzTypography.bodySmall)
                                }
                                Text("Payees", style = MzTypography.labelCaps)
                                if (card.payees.isEmpty()) Text("No payee spending yet", style = MzTypography.bodySmall)
                                card.payees.forEach {
                                    AmountLine(it.name, it.amountMinor, hideAmounts, MzTypography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { MzButton("＋  New budget", { onNavigate(V2Route.NewBudget) }, Modifier.fillMaxWidth()) }
            }
        } else if (section == V2Route.PlannedPayments) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
            ) {
                item {
                    MzCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Next 7 days", style = MzTypography.title)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AmountLine("Out", plannedPage.next7OutMinor, hideAmounts)
                                AmountLine("In", plannedPage.next7InMinor, hideAmounts)
                            }
                            Text("${plannedPage.overdueCount} overdue", style = MzTypography.bodySmall)
                        }
                    }
                }
                item { Text("Forecasted liquidity after scheduled commitments", style = MzTypography.title) }
                item {
                    MzCard(
                        Modifier.fillMaxWidth(),
                    ) {
                        AmountText(
                            plannedPage.forecastEndMinor,
                            "EGP",
                            hideAmounts = hideAmounts,
                            modifier = Modifier.padding(16.dp),
                            textStyle = MzTypography.title,
                        )
                    }
                }
                listOf("Overdue" to plannedPage.overdue, "Next 7 days" to plannedPage.next7, "Later" to plannedPage.later).forEach { (title, occurrencesInGroup) ->
                    item { Text(title, style = MzTypography.title) }
                    if (occurrencesInGroup.isEmpty()) item { Text("Nothing scheduled", style = MzTypography.bodySmall) }
                    items(occurrencesInGroup, key = { "${it.payment?.id ?: it.debt?.id}:${it.dueDate}" }) { occurrence ->
                        MzCard(
                            Modifier.fillMaxWidth().clickable(enabled = occurrence.payment != null) {
                                occurrence.payment?.let { onNavigate(V2Route.EditPlannedPayment(it.id)) }
                            },
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(occurrence.name, style = MzTypography.body)
                                    AmountText(occurrence.amountMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.body)
                                }
                                Text(
                                    "${occurrence.dueDate} · ${if (occurrence.isIncome) "In" else "Out"}${if (occurrence.isReadOnly) " · Expected debt payment" else ""}",
                                    style = MzTypography.bodySmall,
                                )
                                if (!occurrence.isReadOnly && occurrence.payment != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MzChip(
                                            "Mark paid",
                                            false,
                                            { onNavigate(V2Route.PlannedEntry(occurrence.payment.id, occurrence.dueDate.toString())) },
                                        )
                                        MzChip("Skip", false, {
                                            scope.launch {
                                                repository.markPlannedOccurrence(
                                                    occurrence.payment.id,
                                                    occurrence.dueDate.toString(),
                                                    "SKIPPED",
                                                )
                                            }
                                        })
                                        MzChip("Snooze 1h", false, {
                                            val plannedId = occurrence.payment.id
                                            val dueDate = occurrence.dueDate.toString()
                                            val intent =
                                                Intent(context, ReminderActionReceiver::class.java).apply {
                                                    action = ReminderActionReceiver.ACTION_SNOOZE
                                                    putExtra(
                                                        ReminderActionReceiver.EXTRA_PLANNED_ID,
                                                        plannedId,
                                                    )
                                                    putExtra(
                                                        ReminderActionReceiver.EXTRA_DUE_DATE,
                                                        dueDate,
                                                    )
                                                }
                                            context.sendBroadcast(intent)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    MzButton("＋  New planned payment", {
                        onNavigate(V2Route.NewPlannedPayment)
                    }, Modifier.fillMaxWidth())
                }
            }
        } else {
            MzEmptyState(
                if (section ==
                    V2Route.Goals
                ) {
                    "Goals arrive in P10"
                } else {
                    "Debts arrive in P10"
                },
                "This Plan tab is ready for the next planning screen.",
                "Budgets",
                { onSelectPlanTab(V2Route.Budgets) },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun V2BudgetForm(
    repository: MizanRepository,
    budgetId: String?,
    hideAmounts: Boolean = false,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val categories by remember(repository, "EXPENSE") { repository.observeCategories("EXPENSE") }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(budgetId) { mutableStateOf("") }
    var budgetScope by rememberSaveable(budgetId) { mutableStateOf("ALL") }
    var scopeIds by rememberSaveable(budgetId) { mutableStateOf(setOf<String>()) }
    var period by rememberSaveable(budgetId) { mutableStateOf(BudgetPeriod.MONTHLY.name) }
    var amount by rememberSaveable(budgetId) { mutableStateOf("") }
    var start by rememberSaveable(budgetId) { mutableStateOf(LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).toString()) }
    var end by rememberSaveable(budgetId) {
        mutableStateOf(
            LocalDate
                .now(
                    java.time.ZoneId.of("Africa/Cairo"),
                ).withDayOfMonth(LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).lengthOfMonth())
                .toString(),
        )
    }
    var rollover by rememberSaveable(budgetId) { mutableStateOf(Rollover.NONE.name) }
    var alert80 by rememberSaveable(budgetId) { mutableStateOf(true) }
    var alert100 by rememberSaveable(budgetId) { mutableStateOf(true) }
    var archived by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(budgetId) {
        val existing = budgetId?.let { repository.activeBudgets().firstOrNull { budget -> budget.id == it } } ?: return@LaunchedEffect
        name = existing.name
        budgetScope = existing.scope
        scopeIds =
            existing.scopeIds
                .split(',')
                .filter(String::isNotBlank)
                .toSet()
        period =
            existing.period
        amount = BigDecimal.valueOf(existing.limitMinor, 2).toPlainString()
        start = existing.startDate ?: start
        end =
            existing.endDate ?: end
        rollover = existing.rollover
        alert80 = "80" in existing.alertPcts.split(',')
        alert100 = "100" in existing.alertPcts.split(',')
    }
    val cents = runCatching { BigDecimal(amount).movePointRight(2).longValueExact() }.getOrDefault(0L)
    val selectedPeriod = BudgetPeriod.valueOf(period)
    val days =
        when (selectedPeriod) {
            BudgetPeriod.WEEKLY -> {
                7
            }

            BudgetPeriod.MONTHLY -> {
                30
            }

            BudgetPeriod.YEARLY -> {
                365
            }

            BudgetPeriod.ONE_TIME -> {
                runCatching {
                    ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)).toInt() +
                        1
                }.getOrDefault(1).coerceAtLeast(1)
            }
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 12.dp),
    ) {
        item { MzInput(name, { name = it }, "Name") }
        item {
            Text("Applies to", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("CATEGORY" to "Categories", "ACCOUNT" to "Accounts", "ALL" to "Everything").forEach { (value, title) ->
                    MzChip(
                        title,
                        budgetScope == value,
                        {
                            budgetScope = value
                            scopeIds = emptySet()
                        },
                    )
                }
            }
        }
        if (budgetScope ==
            "CATEGORY"
        ) {
            items(categories, key = { it.id }) { item ->
                ScopePicker(item.name, item.id in scopeIds) {
                    scopeIds =
                        scopeIds.toggle(item.id)
                }
            }
        }
        if (budgetScope ==
            "ACCOUNT"
        ) {
            items(accounts, key = { it.id }) { item ->
                ScopePicker(item.name, item.id in scopeIds) {
                    scopeIds =
                        scopeIds.toggle(item.id)
                }
            }
        }
        item {
            Text("Period", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BudgetPeriod.entries.forEach { candidate ->
                    MzChip(
                        candidate.name.lowercase().replaceFirstChar(Char::uppercase),
                        period == candidate.name,
                        { period = candidate.name },
                    )
                }
            }
        }
        if (period ==
            BudgetPeriod.ONE_TIME.name
        ) {
            item {
                MzInput(start, { start = it }, "Start date (YYYY-MM-DD)")
                MzInput(end, { end = it }, "End date (YYYY-MM-DD)")
            }
        }
        item {
            MzInput(amount, { amount = it }, "Limit (EGP)")
            AmountLine("Daily allowance", if (days > 0) cents / days else 0, hideAmounts, MzTypography.bodySmall)
        }
        item {
            Text("Rollover", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Rollover.NONE to "None", Rollover.UNDER to "Unused only", Rollover.BOTH to "Over and under").forEach { (value, label) ->
                    MzChip(
                        label,
                        rollover == value.name,
                        { rollover = value.name },
                    )
                }
            }
            Text(
                when (rollover) {
                    "NONE" -> "The budget resets to its limit each period."
                    "UNDER" -> "Unused budget carries forward; overspending does not."
                    else -> "Both unused budget and overspending carry forward."
                },
                style = MzTypography.bodySmall,
            )
        }
        item {
            MzToggle(alert80, { alert80 = it }, "Alert at 80%")
            AmountLine("Amount", cents * 80 / 100, hideAmounts, MzTypography.bodySmall)
            MzToggle(alert100, { alert100 = it }, "Alert at 100%")
            AmountLine("Amount", cents, hideAmounts, MzTypography.bodySmall)
        }
        item {
            MzButton("Save budget", {
                if (name.isBlank() || cents <= 0 || (budgetScope != "ALL" && scopeIds.isEmpty())) return@MzButton
                val id = budgetId ?: UUID.randomUUID().toString()
                val entity =
                    BudgetEntity(
                        id,
                        name.trim(),
                        budgetScope,
                        scopeIds.sorted().joinToString(","),
                        period,
                        start.takeIf {
                            period ==
                                "ONE_TIME"
                        },
                        end.takeIf {
                            period == "ONE_TIME"
                        },
                        cents,
                        rollover,
                        listOfNotNull(if (alert80) "80" else null, if (alert100) "100" else null).joinToString(","),
                        false,
                    )
                scope.launch {
                    repository.saveBudget(entity)
                    onSaved(id)
                }
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && cents > 0)
            if (budgetId !=
                null
            ) {
                MzButton("Delete budget", {
                    scope.launch {
                        repository.setBudgetArchived(budgetId, true)
                        archived = true
                    }
                }, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
            }
        }
    }
    if (archived &&
        budgetId != null
    ) {
        MzSheet(onDismissRequest = { onBack() }) {
            Text("Budget deleted", style = MzTypography.title)
            MzButton("Undo", {
                scope.launch {
                    repository.setBudgetArchived(budgetId, false)
                    archived =
                        false
                    onBack()
                }
            }, Modifier.fillMaxWidth())
            MzButton("Done", onBack, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
        }
    }
}

@Composable
fun V2PlannedPaymentForm(
    repository: MizanRepository,
    paymentId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember(repository, "EXPENSE") { repository.observeCategories("EXPENSE") }.collectAsStateWithLifecycle(emptyList())
    val incomeCategories by remember(
        repository,
        "INCOME",
    ) { repository.observeCategories("INCOME") }.collectAsStateWithLifecycle(emptyList())
    val payees by remember(repository) { repository.observePayees() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable(paymentId) { mutableStateOf("EXPENSE") }
    var name by rememberSaveable(paymentId) { mutableStateOf("") }
    var amount by rememberSaveable(paymentId) { mutableStateOf("") }
    var currency by rememberSaveable(paymentId) { mutableStateOf("EGP") }
    var accountId by rememberSaveable(paymentId) { mutableStateOf("") }
    var targetId by rememberSaveable(paymentId) { mutableStateOf("") }
    var categoryId by rememberSaveable(paymentId) { mutableStateOf<String?>(null) }
    var payeeId by rememberSaveable(paymentId) { mutableStateOf<String?>(null) }
    var paymentType by rememberSaveable(paymentId) { mutableStateOf("CASH") }
    var frequency by rememberSaveable(paymentId) { mutableStateOf(Frequency.MONTHLY.name) }
    var interval by rememberSaveable(paymentId) { mutableStateOf("1") }
    var firstDate by rememberSaveable(paymentId) { mutableStateOf(LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).toString()) }
    var endDate by rememberSaveable(paymentId) { mutableStateOf("") }
    var lead by rememberSaveable(paymentId) { mutableStateOf("0") }
    var archived by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(paymentId, accounts, currencies, categories, payees) {
        val existing = paymentId?.let { repository.activePlannedPayment(it) } ?: return@LaunchedEffect
        kind = existing.kind
        name = existing.name
        amount =
            BigDecimal.valueOf(existing.amountMinor, currencies.firstOrNull { it.code == existing.currency }?.decimals ?: 2).toPlainString()
        currency = existing.currency
        accountId = existing.accountId
        targetId = existing.toAccountId.orEmpty()
        categoryId =
            existing.categoryId
        payeeId = existing.payeeId
        paymentType = existing.paymentType
        frequency = existing.frequency
        interval = existing.interval.toString()
        firstDate = existing.startDate
        endDate =
            existing.endDate.orEmpty()
        lead = existing.remindDaysBefore.toString()
    }
    LaunchedEffect(accounts) {
        if (accountId.isBlank()) accountId = accounts.firstOrNull()?.id.orEmpty()
        if (targetId.isBlank()) {
            targetId =
                accounts.firstOrNull { it.id != accountId }?.id.orEmpty()
        }
    }
    val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
    val minor = runCatching { BigDecimal(amount).movePointRight(decimals).longValueExact() }.getOrDefault(0L)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 12.dp),
    ) {
        item {
            MzSegmented(listOf("Income", "Expense", "Transfer"), listOf("INCOME", "EXPENSE", "TRANSFER").indexOf(kind), {
                kind =
                    listOf("INCOME", "EXPENSE", "TRANSFER")[it]
            })
        }
        item {
            MzInput(name, { name = it }, "Name")
            MzInput(amount, { amount = it }, "Amount")
        }
        item {
            Text("Currency", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                currencies.filter { it.enabled }.forEach { candidate ->
                    MzChip(
                        candidate.code,
                        currency == candidate.code,
                        { currency = candidate.code },
                    )
                }
            }
        }
        item {
            Text("Account", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                accounts.forEach { candidate ->
                    MzChip(
                        candidate.name,
                        accountId == candidate.id,
                        { accountId = candidate.id },
                    )
                }
            }
        }
        if (kind ==
            "TRANSFER"
        ) {
            item {
                Text("To account", style = MzTypography.labelCaps)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts
                        .filter {
                            it.id !=
                                accountId
                        }.forEach { candidate -> MzChip(candidate.name, targetId == candidate.id, { targetId = candidate.id }) }
                }
            }
        }
        if (kind !=
            "TRANSFER"
        ) {
            item {
                Text("Category", style = MzTypography.labelCaps)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (
                        if (kind ==
                            "INCOME"
                        ) {
                            incomeCategories
                        } else {
                            categories
                        }
                    ).forEach { candidate ->
                        MzChip(candidate.name, categoryId == candidate.id, {
                            categoryId =
                                candidate.id
                        })
                    }
                }
            }
        }
        item {
            Text("Payee", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MzChip(
                    "None",
                    payeeId == null,
                    { payeeId = null },
                )
                ; payees.forEach { candidate ->
                    MzChip(candidate.name, payeeId == candidate.id, {
                        payeeId =
                            candidate.id
                    })
                }
            }
        }
        item {
            Text("Payment method", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("CASH", "CARD", "BANK_TRANSFER", "OTHER").forEach { candidate ->
                    MzChip(
                        candidate.replace('_', ' ').lowercase(),
                        paymentType == candidate,
                        { paymentType = candidate },
                    )
                }
            }
        }
        item {
            Text("Recurrence", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Frequency.entries.forEach { candidate ->
                    MzChip(
                        candidate.name.lowercase().replaceFirstChar(Char::uppercase),
                        frequency == candidate.name,
                        { frequency = candidate.name },
                    )
                }
            }
            MzInput(interval, {
                interval =
                    it.filter(Char::isDigit).ifEmpty { "1" }
            }, "Every (interval)")
        }
        item {
            MzInput(firstDate, { firstDate = it }, "First date (YYYY-MM-DD)")
            MzInput(endDate, { endDate = it }, "End date (optional)")
            MzInput(lead, {
                lead =
                    it.filter(Char::isDigit)
            }, "Notify days before")
        }
        item { Text("Planned payments never post automatically", style = MzTypography.bodySmall) }
        item {
            MzButton("Save planned payment", {
                if (name.isBlank() || minor <= 0L || accountId.isBlank()) return@MzButton
                val entity =
                    PlannedPaymentEntity(
                        paymentId ?: UUID.randomUUID().toString(),
                        kind,
                        name.trim(),
                        categoryId.takeIf {
                            kind !=
                                "TRANSFER"
                        },
                        accountId,
                        targetId.takeIf { kind == "TRANSFER" },
                        currency,
                        minor,
                        payeeId,
                        paymentType,
                        frequency,
                        interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        firstDate,
                        endDate.ifBlank { null },
                        lead.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        autoPost = false,
                        active = true,
                    )
                scope.launch {
                    repository.savePlannedPayment(entity)
                    PlanningReminderScheduler.refresh(context)
                    onSaved()
                }
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && minor > 0 && accountId.isNotBlank())
            if (paymentId !=
                null
            ) {
                MzButton("Delete planned payment", {
                    scope.launch {
                        repository.setPlannedActive(paymentId, false)
                        archived = true
                    }
                }, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
            }
        }
    }
    if (archived &&
        paymentId != null
    ) {
        MzSheet(onDismissRequest = onBack) {
            Text("Planned payment deleted", style = MzTypography.title)
            MzButton("Undo", {
                scope.launch {
                    repository.setPlannedActive(paymentId, true)
                    archived =
                        false
                    onBack()
                }
            }, Modifier.fillMaxWidth())
            MzButton("Done", onBack, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
        }
    }
}

@Composable
private fun ScopePicker(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MzChip(name, selected, onClick)
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

@Composable
private fun AmountLine(
    label: String,
    minor: Long,
    hideAmounts: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle = MzTypography.body,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = textStyle)
        AmountText(minor, "EGP", hideAmounts = hideAmounts, textStyle = textStyle)
    }
}

private fun statusLabel(status: BudgetStatus): String =
    when (status) {
        BudgetStatus.ON_PACE -> "On pace"
        BudgetStatus.TRENDING_OVER -> "Trending over"
        BudgetStatus.OVER -> "Over"
    }

private fun stepAnchor(
    date: LocalDate,
    period: BudgetPeriod,
    amount: Long,
): LocalDate =
    when (period) {
        BudgetPeriod.WEEKLY -> date.plusWeeks(amount)
        BudgetPeriod.MONTHLY -> date.plusMonths(amount)
        BudgetPeriod.YEARLY -> date.plusYears(amount)
        BudgetPeriod.ONE_TIME -> date.plusMonths(amount)
    }

private fun anchorCaption(
    date: LocalDate,
    period: BudgetPeriod,
): String =
    when (period) {
        BudgetPeriod.WEEKLY -> {
            "Week of ${date.minusDays((date.dayOfWeek.value % 7).toLong())}"
        }

        BudgetPeriod.MONTHLY -> {
            date.month.name
                .lowercase()
                .replaceFirstChar(Char::uppercase) +
                " ${date.year}"
        }

        BudgetPeriod.YEARLY -> {
            date.year.toString()
        }

        BudgetPeriod.ONE_TIME -> {
            date.toString()
        }
    }
