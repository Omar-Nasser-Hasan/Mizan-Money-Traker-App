// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AccountEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ledger.EntryCalculator
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeAllocation
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.IncomeSplitCalculator
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.SplitIncomeRequest
import app.mizan.domain.ledger.SplitLine
import app.mizan.domain.ledger.SplitMode
import app.mizan.domain.ledger.TransferRequest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun V2EntryScreen(
    repository: MizanRepository,
    initialKind: String,
    lastAccountId: String?,
    hideAmounts: Boolean,
    onDone: () -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val sources by repository.observeIncomeSources().collectAsStateWithLifecycle(emptyList())
    val templates by repository.observeSplitTemplates().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    val categories by repository
        .observeCategories(if (kind == "INCOME") "INCOME" else "EXPENSE")
        .collectAsStateWithLifecycle(emptyList())
    var accountId by remember(accounts, lastAccountId) { mutableStateOf(lastAccountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var targetId by remember(accounts) { mutableStateOf(accounts.firstOrNull { it.id != accountId }?.id.orEmpty()) }
    var categoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: MizanRepository.UNCATEGORIZED_ID) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var templateId by remember { mutableStateOf<String?>(null) }
    val lines by repository.observeSplitLines(templateId ?: "").collectAsStateWithLifecycle(emptyList())
    var expression by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val account = accounts.firstOrNull { it.id == accountId }
    val target = accounts.firstOrNull { it.id == targetId }
    val buckets by repository.observeBuckets(accountId).collectAsStateWithLifecycle(emptyList())
    val targetBuckets by repository.observeBuckets(targetId).collectAsStateWithLifecycle(emptyList())
    var currency by remember(accountId, account?.defaultCurrency) { mutableStateOf(account?.defaultCurrency ?: "EGP") }
    var targetCurrency by remember(targetId, target?.defaultCurrency) { mutableStateOf(target?.defaultCurrency ?: "EGP") }
    val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
    val amount = runCatching { EntryCalculator.minorUnits(expression, decimals) }.getOrDefault(0L)
    val allocations = splitAllocations(amount, lines, templates.firstOrNull { it.id == templateId }?.remainderAccountId)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
    ) {
        item {
            MzSegmented(
                options = listOf("Expense", "Income", "Transfer"),
                selectedIndex = listOf("EXPENSE", "INCOME", "TRANSFER").indexOf(kind).coerceAtLeast(0),
                onSelected = { kind = listOf("EXPENSE", "INCOME", "TRANSFER")[it] },
            )
        }
        item {
            AmountText(
                amount,
                currency,
                if (kind ==
                    "EXPENSE"
                ) {
                    AmountStyle.EXPENSE
                } else {
                    AmountStyle.NEUTRAL
                },
                hideAmounts,
                textStyle = MzTypography.hero,
            )
        }
        item { MzKeypad(onKey = { key -> expression = if (key == "⌫") expression.dropLast(1) else expression + key }) }
        item { Text("Account", style = MzTypography.labelCaps) }
        item { AccountChips(accounts, accountId) { accountId = it } }
        if (buckets.size > 1) {
            item {
                CurrencyChips(buckets.map { it.currency }, currency) { currency = it }
            }
        }
        if (kind == "EXPENSE") {
            item { Text("Category", style = MzTypography.labelCaps) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(6).forEach { category ->
                        MzChip(category.name, category.id == categoryId, { categoryId = category.id })
                    }
                }
            }
            if (account != null &&
                amount > 0
            ) {
                item { Text("Negative balances are allowed.", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall) }
            }
        }
        if (kind == "INCOME") {
            item { Text("Source", style = MzTypography.labelCaps) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sources.forEach { source ->
                        MzChip(
                            source.name,
                            source.id == sourceId,
                            {
                                sourceId = source.id
                                templateId = source.defaultTemplateId
                            },
                        )
                    }
                }
            }
            item { Text("Split", style = MzTypography.labelCaps) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    templates.forEach { template -> MzChip(template.name, template.id == templateId, { templateId = template.id }) }
                    MzChip("All to ${account?.name ?: "account"}", templateId == null, { templateId = null })
                }
            }
            if (allocations.isNotEmpty()) {
                item {
                    allocations.forEach { allocation ->
                        Text(
                            "${accounts.firstOrNull { it.id == allocation.accountId }?.name}: ${allocation.amountMinor} minor",
                            style = MzTypography.bodySmall,
                        )
                    }
                }
            }
        }
        if (kind == "TRANSFER") {
            item { Text("To account", style = MzTypography.labelCaps) }
            item { AccountChips(accounts.filter { it.id != accountId }, targetId) { targetId = it } }
            if (targetBuckets.size > 1) {
                item {
                    CurrencyChips(targetBuckets.map { it.currency }, targetCurrency) { targetCurrency = it }
                }
            }
            item { Text("Not income or expense", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall) }
        }
        item {
            MzButton("Save", {
                val current = account ?: return@MzButton
                if (amount <= 0L) {
                    message = "Enter an amount"
                    return@MzButton
                }
                scope.launch {
                    val date = LocalDate.now(ZoneId.of("Africa/Cairo"))
                    val rate = repository.effectiveRate(currency, date)
                    if (rate == null) {
                        message = "No booking rate is available for $currency"
                        return@launch
                    }
                    val posting =
                        when (kind) {
                            "EXPENSE" -> {
                                LedgerService().postExpense(
                                    ExpenseRequest(current.id, currency, amount, decimals, rate, categoryId, Instant.now(), date),
                                )
                            }

                            "INCOME" -> {
                                if (allocations.isEmpty()) {
                                    LedgerService().postIncome(
                                        IncomeRequest(current.id, currency, amount, decimals, rate, Instant.now(), date, categoryId),
                                    )
                                } else {
                                    LedgerService().postSplitIncome(
                                        SplitIncomeRequest(allocations, currency, decimals, rate, Instant.now(), date, categoryId),
                                    )
                                }
                            }

                            else -> {
                                val destination =
                                    target ?: run {
                                        message = "Choose another account"
                                        return@launch
                                    }
                                val targetDecimals = currencies.firstOrNull { it.code == targetCurrency }?.decimals ?: 2
                                val targetRate = repository.effectiveRate(targetCurrency, date)
                                if (targetRate == null) {
                                    message = "No booking rate is available for $targetCurrency"
                                    return@launch
                                }
                                LedgerService().postTransfer(
                                    TransferRequest(
                                        current.id,
                                        currency,
                                        amount,
                                        decimals,
                                        rate,
                                        destination.id,
                                        targetCurrency,
                                        amount,
                                        targetDecimals,
                                        targetRate,
                                        Instant.now(),
                                        date,
                                    ),
                                )
                            }
                        }
                    repository.post(posting)
                    repository.setLastUsedAccountId(current.id)
                    onDone()
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        if (message.isNotBlank()) item { Text(message, color = LocalMzTokens.current.warning) }
    }
}

@Composable
private fun AccountChips(
    accounts: List<AccountEntity>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        accounts.forEach { account ->
            MzChip(account.name, account.id == selectedId, { onSelected(account.id) })
        }
    }
}

@Composable
private fun CurrencyChips(
    currencies: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { currency ->
            MzChip(currency, currency == selected, { onSelected(currency) })
        }
    }
}

private fun splitAllocations(
    amount: Long,
    lines: List<app.mizan.data.db.SplitLineEntity>,
    remainderAccountId: String?,
): List<IncomeAllocation> =
    if (amount <= 0L || lines.isEmpty()) {
        emptyList()
    } else {
        runCatching {
            IncomeSplitCalculator
                .allocate(
                    amount,
                    lines.map {
                        SplitLine(it.id, it.templateId, it.accountId, SplitMode.valueOf(it.mode), it.value)
                    },
                    remainderAccountId,
                ).allocations
        }.getOrDefault(emptyList())
    }
