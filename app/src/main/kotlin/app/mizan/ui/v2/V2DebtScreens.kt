// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.mizan.ui.v2

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
import app.mizan.data.db.DebtEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.plan.DebtBalance
import app.mizan.worker.PlanningReminderScheduler
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun V2DebtsScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onNavigate: (V2Route) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debts by remember(repository) { repository.observeAllDebts() }.collectAsStateWithLifecycle(emptyList())
    val repayments by remember(repository) { repository.observeDebtRepaymentTotals() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val rates by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val totals = repayments.associate { it.debtId to it.repaidMinor }
    val cardData = debts.map { GoalDebtCalculations.debt(it, totals[it.id] ?: 0L, today) }
    val owed =
        cardData.filter { it.debt.status == "ACTIVE" && it.debt.direction == "LENT" }.sumOf {
            GoalDebtCalculations.convertEgp(it.balance.outstandingMinor, it.debt.currency, currencies, rates)
        }
    val owing =
        cardData.filter { it.debt.status == "ACTIVE" && it.debt.direction == "BORROWED" }.sumOf {
            GoalDebtCalculations.convertEgp(it.balance.outstandingMinor, it.debt.currency, currencies, rates)
        }
    var tab by rememberSaveable { mutableStateOf("ACTIVE") }
    var repaymentDebtId by remember { mutableStateOf<String?>(null) }
    val rows = cardData.filter { if (tab == "ACTIVE") it.debt.status == "ACTIVE" else it.debt.status != "ACTIVE" }
    val owedRows = rows.filter { it.debt.direction == "LENT" }
    val owingRows = rows.filter { it.debt.direction == "BORROWED" }
    Column(Modifier.fillMaxSize()) {
        MzSegmented(listOf("Budgets", "Planned", "Goals", "Debts"), 3, { index ->
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
        MzSegmented(
            listOf("Active", "Closed"),
            if (tab ==
                "ACTIVE"
            ) {
                0
            } else {
                1
            },
            { tab = if (it == 0) "ACTIVE" else "CLOSED" },
            Modifier.padding(horizontal = MzSpacing.sideMargin),
        )
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = MzSpacing.sideMargin),
            verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(vertical = 12.dp),
        ) {
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You are owed", style = MzTypography.labelCaps)
                            AmountText(owed, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.title)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You owe", style = MzTypography.labelCaps)
                            AmountText(owing, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.title)
                        }
                    }
                }
            }
            item { Text("Owed to you · ${owedRows.size} records", style = MzTypography.title) }
            if (owedRows.isEmpty()) {
                item {
                    MzCard(
                        Modifier.fillMaxWidth(),
                    ) { Text("Nothing is owed to you right now", Modifier.padding(18.dp), style = MzTypography.bodySmall) }
                }
            }
            items(owedRows, key = { it.debt.id }) { row ->
                V2DebtCard(
                    row,
                    currencies
                        .firstOrNull {
                            it.code == row.debt.currency
                        }?.decimals ?: 2,
                    hideAmounts,
                    onEdit = { onNavigate(V2Route.EditDebt(row.debt.id)) },
                    onRepay = {
                        repaymentDebtId =
                            row.debt.id
                    },
                    onRemind = { PlanningReminderScheduler.remindDebt(context, row.debt.id) },
                )
            }
            item { Text("You owe · ${owingRows.size} records", style = MzTypography.title) }
            if (owingRows.isEmpty()) {
                item {
                    MzCard(
                        Modifier.fillMaxWidth(),
                    ) { Text("Nothing borrowed right now", Modifier.padding(18.dp), style = MzTypography.bodySmall) }
                }
            }
            items(owingRows, key = { it.debt.id }) { row ->
                V2DebtCard(
                    row,
                    currencies
                        .firstOrNull {
                            it.code == row.debt.currency
                        }?.decimals ?: 2,
                    hideAmounts,
                    onEdit = { onNavigate(V2Route.EditDebt(row.debt.id)) },
                    onRepay = {
                        repaymentDebtId =
                            row.debt.id
                    },
                    onRemind = { PlanningReminderScheduler.remindDebt(context, row.debt.id) },
                )
            }
            item { MzButton("＋  New debt", { onNavigate(V2Route.NewDebt) }, Modifier.fillMaxWidth()) }
        }
    }
    repaymentDebtId?.let { id ->
        val debt = debts.firstOrNull { it.id == id }
        if (debt != null) {
            val accountOptions = accounts.filter { account -> balances.any { it.accountId == account.id && it.currency == debt.currency } }
            var amount by rememberSaveable(id) { mutableStateOf("") }
            var date by rememberSaveable(id) { mutableStateOf(today.toString()) }
            var accountId by rememberSaveable(id) { mutableStateOf(debt.accountId) }
            val decimals = currencies.firstOrNull { it.code == debt.currency }?.decimals ?: 2
            val minor = parseMinor(amount, decimals)
            MzSheet(onDismissRequest = { repaymentDebtId = null }) {
                Text("Add repayment · ${debt.person}", style = MzTypography.title)
                Text("Remaining", style = MzTypography.bodySmall)
                AmountText((debt.principalMinor - (totals[id] ?: 0L)).coerceAtLeast(0L), debt.currency, hideAmounts = hideAmounts)
                MzInput(amount, { amount = it }, "Amount (${debt.currency})")
                MzInput(date, { date = it }, "Date (YYYY-MM-DD)")
                Text("Account", style = MzTypography.labelCaps)
                accountOptions.forEach { account ->
                    val bucket = balances.first { it.accountId == account.id && it.currency == debt.currency }
                    MzChip(
                        "${account.name} · ${accountBalance(bucket.openingMinor, bucket.entryMinor, decimals)} ${debt.currency}",
                        accountId == account.id,
                        { accountId = account.id },
                    )
                }
                MzButton(
                    "Post repayment",
                    {
                        if (minor in 1..(debt.principalMinor - (totals[id] ?: 0L)).coerceAtLeast(0L) &&
                            runCatching { LocalDate.parse(date) }.isSuccess &&
                            accountId.isNotBlank()
                        ) {
                            scope.launch {
                                repository.repayDebt(id, minor, accountId, LocalDate.parse(date))
                                repaymentDebtId = null
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled =
                        minor in 1..(debt.principalMinor - (totals[id] ?: 0L)).coerceAtLeast(0L) && accountId.isNotBlank(),
                )
            }
        }
    }
}

@Composable
fun V2DebtForm(
    repository: MizanRepository,
    debtId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val debts by remember(repository) { repository.observeAllDebts() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var direction by rememberSaveable(debtId) { mutableStateOf("LENT") }
    var person by rememberSaveable(debtId) { mutableStateOf("") }
    var principal by rememberSaveable(debtId) { mutableStateOf("") }
    var currency by rememberSaveable(debtId) { mutableStateOf("EGP") }
    var accountId by rememberSaveable(debtId) { mutableStateOf("") }
    var openedAt by rememberSaveable(debtId) { mutableStateOf(LocalDate.now(ZoneId.of("Africa/Cairo")).toString()) }
    var dueDate by rememberSaveable(debtId) { mutableStateOf("") }
    var note by rememberSaveable(debtId) { mutableStateOf("") }
    var status by rememberSaveable(debtId) { mutableStateOf("ACTIVE") }
    LaunchedEffect(debtId) {
        val existing = debtId?.let { repository.debt(it) } ?: return@LaunchedEffect
        direction = existing.direction
        person = existing.person
        principal =
            existing.principalMinor
                .toBigDecimal()
                .movePointLeft(
                    currencies.firstOrNull { it.code == existing.currency }?.decimals ?: 2,
                ).toPlainString()
        currency = existing.currency
        accountId = existing.accountId
        openedAt = existing.openedAt
        dueDate = existing.dueDate.orEmpty()
        note = existing.note
        status = existing.status
    }
    LaunchedEffect(accounts) { if (accountId.isBlank()) accountId = accounts.firstOrNull()?.id.orEmpty() }
    val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
    val settlementAccounts = accounts.filter { account -> balances.any { it.accountId == account.id && it.currency == currency } }
    LaunchedEffect(currency, settlementAccounts) {
        if (settlementAccounts.none { it.id == accountId }) {
            accountId =
                settlementAccounts.firstOrNull()?.id.orEmpty()
        }
    }
    val amountMinor = parseMinor(principal, decimals)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 12.dp),
    ) {
        item {
            MzSegmented(
                listOf("I lent", "I borrowed"),
                if (direction ==
                    "LENT"
                ) {
                    0
                } else {
                    1
                },
                { direction = if (it == 0) "LENT" else "BORROWED" },
            )
        }
        item {
            MzInput(person, { person = it }, "Counterparty")
            val suggestions =
                debts
                    .map { it.person }
                    .filter(String::isNotBlank)
                    .distinct()
                    .filterNot { it.equals(person, true) }
                    .take(8)
            if (suggestions.isNotEmpty()) {
                Text("Suggestions", style = MzTypography.labelCaps)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { MzChip(it, false, { person = it }) } }
            }
        }
        item {
            MzInput(principal, { principal = it }, "Principal")
            Text("Currency", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                currencies.filter { it.enabled }.forEach { item ->
                    MzChip(
                        item.code,
                        currency == item.code,
                        { currency = item.code },
                    )
                }
            }
        }
        item {
            Text("Settlement account", style = MzTypography.labelCaps)
            if (settlementAccounts.isEmpty()) {
                Text(
                    "Add a $currency bucket to an account before creating this debt.",
                    style = MzTypography.bodySmall,
                )
            }
            settlementAccounts.forEach { account ->
                val balance = balances.first { it.accountId == account.id && it.currency == currency }
                val label = "${account.name} · ${accountBalance(balance.openingMinor, balance.entryMinor, decimals)} $currency"
                MzChip(label, accountId == account.id, { accountId = account.id })
            }
        }
        item {
            MzInput(openedAt, { openedAt = it }, "Date (YYYY-MM-DD)")
            MzInput(dueDate, { dueDate = it }, "Due date (optional)")
            MzInput(note, {
                note =
                    it
            }, "Note", singleLine = false)
        }
        item {
            Text(
                "Lending moves cash out of the account and creates something owed to you. It isn't an expense.",
                style = MzTypography.bodySmall,
            )
        }
        item {
            MzButton(
                if (debtId == null) "Save debt" else "Save changes",
                {
                    if (person.isBlank() || amountMinor <= 0 || accountId.isBlank() ||
                        runCatching {
                            LocalDate.parse(
                                openedAt,
                            )
                        }.isFailure ||
                        (dueDate.isNotBlank() && runCatching { LocalDate.parse(dueDate) }.isFailure)
                    ) {
                        return@MzButton
                    }
                    scope.launch {
                        repository.saveDebt(
                            DebtEntity(
                                debtId ?: UUID.randomUUID().toString(),
                                person.trim(),
                                direction,
                                currency,
                                accountId,
                                amountMinor,
                                openedAt,
                                dueDate.takeIf(String::isNotBlank),
                                note.trim(),
                                status,
                            ),
                        )
                        onSaved()
                    }
                },
                Modifier.fillMaxWidth(),
                enabled =
                    person.isNotBlank() && amountMinor > 0 && accountId.isNotBlank() && settlementAccounts.any { it.id == accountId },
            )
            MzButton("Cancel", onBack, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
        }
    }
}

@Composable
private fun V2DebtCard(
    row: DebtCardData,
    decimals: Int,
    hideAmounts: Boolean,
    onEdit: () -> Unit,
    onRepay: () -> Unit,
    onRemind: () -> Unit,
) {
    MzCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.debt.person, style = MzTypography.title)
                    Text(if (row.debt.direction == "LENT") "I lent" else "I borrowed", style = MzTypography.bodySmall)
                }
                if (row.overdue &&
                    row.debt.status == "ACTIVE"
                ) {
                    Text("Overdue", color = LocalMzTokens.current.warning, style = MzTypography.labelCaps)
                }
            }
            if (row.debt.note.isNotBlank()) Text(row.debt.note, style = MzTypography.bodySmall)
            row.debt.dueDate?.let { Text("Due $it", style = MzTypography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remaining", style = MzTypography.bodySmall)
                AmountText(row.balance.outstandingMinor, row.debt.currency, hideAmounts = hideAmounts)
            }
            MzBudgetBar(if (row.debt.principalMinor > 0L) row.balance.repaymentsMinor.toFloat() / row.debt.principalMinor else 1f)
            Text(
                "${row.balance.repaymentsMinor.toBigDecimal().movePointLeft(
                    decimals,
                ).toPlainString()} ${row.debt.currency} repaid · ${row.balance.outstandingMinor.toBigDecimal().movePointLeft(
                    decimals,
                ).toPlainString()} remaining",
                style = MzTypography.bodySmall,
            )
            if (row.debt.status == "ACTIVE") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MzButton("Add repayment", onRepay, Modifier.weight(1f))
                    MzButton("Remind", onRemind, Modifier.weight(1f), MzButtonStyle.GHOST)
                }
            }
        }
    }
}

private fun parseMinor(
    value: String,
    decimals: Int,
): Long =
    runCatching {
        BigDecimal(value).movePointRight(decimals).longValueExact()
    }.getOrDefault(0L)

private fun accountBalance(
    openingMinor: Long,
    entryMinor: Long,
    decimals: Int,
): String = BigDecimal.valueOf(openingMinor + entryMinor, decimals).toPlainString()
