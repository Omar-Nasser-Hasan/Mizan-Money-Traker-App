// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.GoalLinkEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.plan.BudgetMath
import app.mizan.domain.plan.BudgetMathInput
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.DebtBalance
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.GoalProgress
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import app.mizan.domain.plan.Rollover
import app.mizan.domain.plan.budgetWindow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
fun planScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    var section by remember { mutableStateOf(PlanSection.BUDGETS) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var budgetScope by remember { mutableStateOf("ALL") }
    var budgetPeriod by remember { mutableStateOf("MONTHLY") }
    var rollover by remember { mutableStateOf("NONE") }
    var plannedKind by remember { mutableStateOf("EXPENSE") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var debtDirection by remember { mutableStateOf("LENT") }
    var dueDate by remember { mutableStateOf("") }
    var repaymentAmount by remember { mutableStateOf("") }
    var allocationPct by remember { mutableStateOf("100") }
    var goalDate by remember { mutableStateOf("") }
    var manualContribution by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val budgets by repository.observeBudgets().collectAsStateWithLifecycle(emptyList())
    val planned by repository.observePlannedPayments().collectAsStateWithLifecycle(emptyList())
    val debts by repository.observeDebts().collectAsStateWithLifecycle(emptyList())
    val goals by repository.observeGoals().collectAsStateWithLifecycle(emptyList())
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val categories by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val selectedScopeId = if (budgetScope == "ACCOUNT") accounts.firstOrNull()?.id.orEmpty() else categories.firstOrNull()?.id.orEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Plan", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanSection.entries.forEach { option ->
                    FilterChip(section == option, { section = option }, label = { Text(option.label) })
                }
            }
        }
        item {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(if (section == PlanSection.DEBTS) "Person" else "Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                amount,
                { amount = it },
                label = { Text("Amount (EGP)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (section) {
            PlanSection.BUDGETS -> {
                item { optionChips("Scope", listOf("ALL", "ACCOUNT", "CATEGORY"), budgetScope) { budgetScope = it } }
                item { optionChips("Period", listOf("WEEKLY", "MONTHLY", "YEARLY", "ONE_TIME"), budgetPeriod) { budgetPeriod = it } }
                item { optionChips("Rollover", listOf("NONE", "UNDER", "BOTH"), rollover) { rollover = it } }
                if (budgetScope !=
                    "ALL"
                ) {
                    item {
                        Text(
                            "Scope target: ${if (budgetScope == "ACCOUNT") accounts.firstOrNull()?.name ?: "Create an account" else categories.firstOrNull()?.name ?: "Uncategorized"}",
                        )
                    }
                }
                if (budgetPeriod ==
                    "ONE_TIME"
                ) {
                    item {
                        OutlinedTextField(
                            dueDate,
                            { dueDate = it },
                            label = { Text("End date (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            PlanSection.PLANNED -> {
                item { optionChips("Kind", listOf("EXPENSE", "INCOME", "TRANSFER"), plannedKind) { plannedKind = it } }
                item { optionChips("Repeats", listOf("ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"), frequency) { frequency = it } }
                item { Text("The first account is used. Edit account details or add more accounts from Settings.") }
            }

            PlanSection.DEBTS -> {
                item { optionChips("Direction", listOf("LENT", "BORROWED"), debtDirection) { debtDirection = it } }
                item {
                    OutlinedTextField(
                        dueDate,
                        { dueDate = it },
                        label = { Text("Due date (optional, YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        repaymentAmount,
                        { repaymentAmount = it },
                        label = { Text("Partial repayment amount (EGP)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            PlanSection.GOALS -> {
                item { Text("The first account can be linked now; add more allocations from the goal details.") }
                item {
                    OutlinedTextField(allocationPct, {
                        allocationPct = it
                    }, label = { Text("First-account allocation %") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(
                        goalDate,
                        { goalDate = it },
                        label = { Text("Target date (optional, YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(manualContribution, {
                        manualContribution = it
                    }, label = { Text("Initial manual contribution (optional, EGP)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Button(
                onClick = {
                    val minor = amountToMinor(amount, 2)
                    val account = accounts.firstOrNull()
                    if (name.isBlank() || minor <= 0 || (section != PlanSection.BUDGETS && account == null)) return@Button
                    scope.launch {
                        when (section) {
                            PlanSection.BUDGETS -> {
                                val oneTimeEnd =
                                    dueDate.takeIf {
                                        budgetPeriod == "ONE_TIME" && runCatching { LocalDate.parse(it) }.isSuccess
                                    }
                                repository.saveBudget(
                                    BudgetEntity(
                                        UUID.randomUUID().toString(),
                                        name,
                                        budgetScope,
                                        selectedScopeId,
                                        budgetPeriod,
                                        LocalDate.now().toString(),
                                        oneTimeEnd,
                                        minor,
                                        rollover,
                                    ),
                                )
                            }

                            PlanSection.PLANNED -> {
                                repository.savePlannedPayment(
                                    PlannedPaymentEntity(
                                        UUID.randomUUID().toString(),
                                        plannedKind,
                                        name,
                                        accountId = account!!.id,
                                        currency = account.defaultCurrency,
                                        amountMinor = minor,
                                        paymentType = "CASH",
                                        frequency = frequency,
                                        interval = 1,
                                        startDate = LocalDate.now().toString(),
                                        remindDaysBefore = 1,
                                    ),
                                )
                            }

                            PlanSection.DEBTS -> {
                                repository.saveDebt(
                                    DebtEntity(
                                        UUID.randomUUID().toString(),
                                        name,
                                        debtDirection,
                                        "EGP",
                                        account!!.id,
                                        minor,
                                        LocalDate.now().toString(),
                                        dueDate.takeIf { runCatching { LocalDate.parse(it) }.isSuccess },
                                    ),
                                )
                            }

                            PlanSection.GOALS -> {
                                val goal =
                                    GoalEntity(
                                        UUID.randomUUID().toString(),
                                        name,
                                        minor,
                                        goalDate.takeIf {
                                            runCatching { LocalDate.parse(it) }.isSuccess
                                        },
                                        "flag",
                                        "#F2B84B",
                                        "ACTIVE",
                                        System.currentTimeMillis(),
                                    )
                                repository.saveGoal(goal)
                                allocationPct.toLongOrNull()?.takeIf { it in 0..100 }?.let { percent ->
                                    account?.let {
                                        repository.saveGoalLink(
                                            GoalLinkEntity(goal.id, accountId = it.id, allocationPct = percent),
                                        )
                                    }
                                }
                                amountToMinor(manualContribution, 2).takeIf { it > 0 }?.let { contribution ->
                                    repository.saveGoalContribution(
                                        GoalContributionEntity(
                                            UUID.randomUUID().toString(),
                                            goal.id,
                                            LocalDate.now().toString(),
                                            contribution,
                                        ),
                                    )
                                }
                            }
                        }
                        name = ""
                        amount = ""
                        dueDate = ""
                        goalDate = ""
                        manualContribution = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add ${section.singularLabel}") }
        }
        when (section) {
            PlanSection.BUDGETS -> {
                items(budgets, key = { it.id }) { budget ->
                    budgetSummary(repository, budget, planned)
                }
            }

            PlanSection.PLANNED -> {
                items(planned, key = { it.id }) { payment ->
                    Column(Modifier.padding(8.dp)) {
                        Text("${payment.name} · ${payment.kind.lowercase()} · ${payment.frequency.lowercase()}")
                        amountText(payment.amountMinor, payment.currency)
                    }
                }
            }

            PlanSection.DEBTS -> {
                items(debts, key = { it.id }) { debt ->
                    debtSummary(repository, debt, repaymentAmount, onRepay = {
                        scope.launch {
                            repository.repayDebt(debt.id, it)
                            repaymentAmount = ""
                        }
                    })
                }
            }

            PlanSection.GOALS -> {
                items(goals, key = { it.id }) { goal ->
                    goalSummary(repository, goal)
                }
            }
        }
    }
}

private data class BudgetSummary(
    val spent: Long,
    val effectiveLimit: Long,
    val forecast: Long,
)

@Composable
private fun budgetSummary(
    repository: MizanRepository,
    budget: BudgetEntity,
    plans: List<PlannedPaymentEntity>,
) {
    var summary by remember(budget.id) { mutableStateOf<BudgetSummary?>(null) }
    LaunchedEffect(budget, plans) {
        val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
        val period =
            runCatching {
                budgetWindow(
                    BudgetPeriod.valueOf(budget.period),
                    today,
                    budget.startDate?.let(LocalDate::parse),
                    budget.endDate?.let(LocalDate::parse),
                )
            }.getOrNull() ?: return@LaunchedEffect
        val through = minOf(today, period.end)
        val spent = repository.budgetSpent(period.start.toString(), through.toString(), budget.scope, budget.scopeIds)
        val priorSpent =
            if (budget.period == "ONE_TIME") {
                0L
            } else {
                val prior = budgetWindow(BudgetPeriod.valueOf(budget.period), period.start.minusDays(1))
                repository.budgetSpent(prior.start.toString(), prior.end.toString(), budget.scope, budget.scopeIds)
            }
        val plannedRemaining =
            if (today >= period.end) {
                0L
            } else {
                plans
                    .asSequence()
                    .filter { it.kind == "EXPENSE" && it.currency == "EGP" && matchesBudgetScope(it, budget) }
                    .sumOf { payment ->
                        RecurrenceEngine
                            .occurrences(
                                Recurrence(
                                    Frequency.valueOf(payment.frequency),
                                    payment.interval,
                                    LocalDate.parse(payment.startDate),
                                    payment.endDate?.let(LocalDate::parse),
                                ),
                                today.plusDays(1),
                                period.end,
                            ).size
                            .toLong() * payment.amountMinor
                    }
            }
        val elapsed =
            ChronoUnit.DAYS
                .between(period.start, through)
                .toInt()
                .coerceAtLeast(0) + 1
        val remaining =
            ChronoUnit.DAYS
                .between(through, period.end)
                .toInt()
                .coerceAtLeast(0)
        val calculation =
            BudgetMath.calculate(
                BudgetMathInput(
                    budget.limitMinor,
                    spent,
                    budget.limitMinor,
                    priorSpent,
                    Rollover.valueOf(budget.rollover),
                    plannedRemaining,
                    elapsed,
                    remaining,
                ),
            )
        summary = BudgetSummary(spent, calculation.effectiveLimitMinor, calculation.forecastMinor)
    }
    Column(Modifier.padding(8.dp)) {
        Text("${budget.name} · ${budget.scope.lowercase()} · ${budget.period.lowercase()}")
        Text("Rollover: ${budget.rollover.lowercase()}")
        amountText(budget.limitMinor, "EGP")
        summary?.let { value ->
            Text("Spent ${value.spent} minor · effective limit ${value.effectiveLimit} minor")
            Text("Forecast ${value.forecast} minor · ${budgetState(value)}")
        } ?: Text("Calculating budget…")
    }
}

private fun matchesBudgetScope(
    payment: PlannedPaymentEntity,
    budget: BudgetEntity,
): Boolean =
    when (budget.scope) {
        "ALL" -> true
        "ACCOUNT" -> payment.accountId in budget.scopeIds.split(',')
        "CATEGORY" -> payment.categoryId in budget.scopeIds.split(',')
        else -> false
    }

private fun budgetState(summary: BudgetSummary): String =
    when {
        summary.spent > summary.effectiveLimit -> "Over"
        summary.forecast > summary.effectiveLimit -> "Trending over"
        else -> "On track"
    }

@Composable
private fun debtSummary(
    repository: MizanRepository,
    debt: DebtEntity,
    repaymentText: String,
    onRepay: (Long) -> Unit,
) {
    var repaid by remember(debt.id) { mutableStateOf(0L) }
    LaunchedEffect(debt.id) { repaid = repository.debtRepayments(debt.id) }
    val balance = DebtBalance(debt.principalMinor, repaid)
    val due = debt.dueDate?.let(LocalDate::parse)
    val overdue = due != null && due < LocalDate.now(ZoneId.of("Africa/Cairo")) && !balance.isClosed
    Column(Modifier.padding(8.dp)) {
        Text("${debt.person} · ${debt.direction.lowercase()}")
        Text(due?.let { "Due $it" } ?: "No due date")
        amountText(debt.principalMinor, debt.currency)
        Text("Outstanding: ${balance.outstandingMinor} ${debt.currency} minor")
        if (balance.isClosed) Text("Settled")
        if (overdue) Text("Overdue")
        Button(onClick = {
            amountToMinor(repaymentText, currencyDecimals(debt.currency)).takeIf { it > 0 }?.let(onRepay)
        }) { Text("Record repayment") }
    }
}

@Composable
private fun goalSummary(
    repository: MizanRepository,
    goal: GoalEntity,
) {
    val links by repository.observeGoalLinks(goal.id).collectAsStateWithLifecycle(emptyList())
    val contributions by repository.observeGoalContributions(goal.id).collectAsStateWithLifecycle(emptyList())
    val balances by repository.observeAccountBalances().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val linked =
        links.sumOf { link ->
            val accountValue =
                balances.filter { it.accountId == link.accountId }.sumOf { row ->
                    app.mizan.domain.money.MoneyMath.baseMinor(
                        row.openingMinor + row.entryMinor,
                        row.decimals,
                        java.math.BigDecimal(row.egpPerUnit),
                    )
                }
            accountValue * link.allocationPct / 100
        }
    val progress =
        GoalProgress(
            goal.targetMinor,
            linked,
            contributions.sumOf {
                it.amountMinor
            },
            goal.targetDate?.let(LocalDate::parse),
            app.mizan.domain.plan.GoalStatus
                .valueOf(goal.status),
        )
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    Column(Modifier.padding(8.dp)) {
        Text(goal.name)
        Text("Progress ${progress.progressMinor} / ${goal.targetMinor} EGP minor")
        Text("Monthly needed: ${progress.monthlyNeeded(today)} EGP minor")
        val recent = contributions.takeLast(3)
        val averageGain = if (recent.isEmpty()) 0L else recent.sumOf { it.amountMinor } / recent.size
        Text(progress.eta(today, averageGain)?.let { "ETA $it" } ?: "ETA —")
        if (progress.needsReachedPrompt) {
            Button(onClick = {
                scope.launch { repository.setGoalStatus(goal.id, "REACHED") }
            }) { Text("Mark as reached") }
        }
    }
}

@Composable
private fun optionChips(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    option == selected,
                    { onSelected(option) },
                    label = { Text(option.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
    }
}

private enum class PlanSection(
    val label: String,
    val singularLabel: String,
) {
    BUDGETS("Budgets", "budget"),
    PLANNED("Planned", "planned payment"),
    DEBTS("Debts", "debt"),
    GOALS("Goals", "goal"),
}
