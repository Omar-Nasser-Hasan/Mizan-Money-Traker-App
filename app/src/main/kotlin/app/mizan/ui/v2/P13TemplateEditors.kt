// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mizan.R
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.domain.accounts.OnboardingSetup
import java.math.BigDecimal
import java.util.UUID

private data class SplitDraft(
    val id: String,
    val accountId: String,
    val mode: String,
    val value: String,
)

@Composable
fun P13SplitEditor(
    template: SplitTemplateEntity,
    persisted: List<SplitLineEntity>,
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onSave: (SplitTemplateEntity, List<SplitLineEntity>) -> Unit,
) {
    var name by remember { mutableStateOf(template.name) }
    var remainder by remember { mutableStateOf(template.remainderAccountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    val options = accounts.map { it.id to it.name }

    fun decimals(accountId: String): Int =
        currencies.firstOrNull { it.code == accounts.firstOrNull { a -> a.id == accountId }?.defaultCurrency }?.decimals ?: 2
    var values by remember(persisted) {
        mutableStateOf(
            if (persisted.isEmpty()) {
                listOf(SplitDraft(UUID.randomUUID().toString(), accounts.firstOrNull()?.id.orEmpty(), "PERCENT", "100"))
            } else {
                persisted.map {
                    SplitDraft(
                        it.id,
                        it.accountId,
                        it.mode,
                        BigDecimal
                            .valueOf(
                                it.value,
                                if (it.mode ==
                                    "PERCENT"
                                ) {
                                    2
                                } else {
                                    decimals(it.accountId)
                                },
                            ).toPlainString(),
                    )
                }
            },
        )
    }
    P13EditorDialog(onDismiss, onSave = {
        runCatching {
            require(name.isNotBlank() && values.isNotEmpty() && remainder.isNotBlank())
            val lines =
                values.mapIndexed { index, row ->
                    val value =
                        if (row.mode ==
                            "PERCENT"
                        ) {
                            OnboardingSetup.percentBasisPoints(row.value)
                        } else {
                            OnboardingSetup.openingMinor(row.value, decimals(row.accountId))
                        }
                    require(value >= 0 && row.accountId.isNotBlank())
                    SplitLineEntity(row.id, template.id, row.accountId, row.mode, value, index)
                }
            require(lines.filter { it.mode == "PERCENT" }.sumOf { it.value } <= 10_000L)
            onSave(template.copy(name = name, remainderAccountId = remainder), lines)
        }.onFailure { error = true }
    }) {
        P13Name(name, { name = it })
        P13Choice(stringResource(R.string.p13_remainder_account), remainder, options) { remainder = it }
        Text(stringResource(R.string.p13_fixed_currency_note), style = MzTypography.bodySmall)
        values.forEachIndexed { index, row ->
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    P13Choice(stringResource(R.string.p13_account), row.accountId, options) { id ->
                        values =
                            values.mapIndexed { i, old -> if (i == index) old.copy(accountId = id) else old }
                    }
                    val modes = listOf("PERCENT" to stringResource(R.string.p13_percentage), "FIXED" to stringResource(R.string.p13_fixed))
                    P13Choice(stringResource(R.string.p13_allocation), row.mode, modes) { mode ->
                        values =
                            values.mapIndexed { i, old -> if (i == index) old.copy(mode = mode, value = "0") else old }
                    }
                    OutlinedTextField(row.value, { value ->
                        values =
                            values.mapIndexed { i, old ->
                                if (i ==
                                    index
                                ) {
                                    old.copy(value = value)
                                } else {
                                    old
                                }
                            }
                    }, label = {
                        Text(
                            stringResource(
                                if (row.mode ==
                                    "PERCENT"
                                ) {
                                    R.string.p13_percentage
                                } else {
                                    R.string.p13_amount
                                },
                            ),
                        )
                    }, singleLine = true)
                    TextButton(onClick = {
                        values =
                            values.filterIndexed {
                                i,
                                _,
                                ->
                                i != index
                            }
                    }) { Text(stringResource(R.string.p13_remove_line)) }
                }
            }
        }
        TextButton(onClick = {
            values =
                values + SplitDraft(UUID.randomUUID().toString(), accounts.firstOrNull()?.id.orEmpty(), "PERCENT", "0")
        }) { Text(stringResource(R.string.p13_add_line)) }
        if (error) Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense)
    }
}

@Composable
fun P13SourceEditor(
    source: IncomeSourceEntity,
    splits: List<SplitTemplateEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (IncomeSourceEntity) -> Unit,
) {
    var name by remember { mutableStateOf(source.name) }
    var split by remember { mutableStateOf(source.defaultTemplateId.orEmpty()) }
    var category by remember { mutableStateOf(source.defaultCategoryId.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    val none = "" to stringResource(R.string.p13_none)
    P13EditorDialog(onDismiss, onSave = {
        if (name.isBlank()) {
            error = true
        } else {
            onSave(
                source.copy(
                    name = name,
                    defaultTemplateId =
                        split.ifBlank {
                            null
                        },
                    defaultCategoryId = category.ifBlank { null },
                ),
            )
        }
    }) {
        P13Name(name, { name = it })
        P13Choice(stringResource(R.string.p13_default_split), split, listOf(none) + splits.map { it.id to it.name }) { split = it }
        P13Choice(
            stringResource(R.string.p13_category),
            category,
            listOf(none) + categories.filter { it.kind == "INCOME" && !it.archived }.map { it.id to it.name },
        ) { category = it }
        if (error) Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense)
    }
}

@Composable
fun P13RecordTemplateEditor(
    template: RecordTemplateEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    currencies: List<CurrencyEntity>,
    payees: List<PayeeEntity>,
    sources: List<IncomeSourceEntity>,
    splits: List<SplitTemplateEntity>,
    onDismiss: () -> Unit,
    onSave: (RecordTemplateEntity) -> Unit,
) {
    var name by remember { mutableStateOf(template.name) }
    var kind by remember { mutableStateOf(template.kind) }
    var account by remember { mutableStateOf(template.accountId.orEmpty()) }
    var currency by remember { mutableStateOf(template.currency ?: "EGP") }
    val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
    var amount by remember { mutableStateOf(template.amountMinor?.let { BigDecimal.valueOf(it, decimals).toPlainString() }.orEmpty()) }
    var category by remember { mutableStateOf(template.categoryId.orEmpty()) }
    var payee by remember { mutableStateOf(template.payeeId.orEmpty()) }
    var note by remember { mutableStateOf(template.note) }
    var source by remember { mutableStateOf(template.incomeSourceId.orEmpty()) }
    var split by remember { mutableStateOf(template.splitTemplateId.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    val none = "" to stringResource(R.string.p13_choose_when_used)
    val kinds = listOf("EXPENSE", "INCOME", "TRANSFER")
    val names = stringArrayResource(R.array.p13_record_kinds).toList()
    P13EditorDialog(onDismiss, onSave = {
        runCatching {
            require(name.isNotBlank())
            val minor = amount.takeIf(String::isNotBlank)?.let { OnboardingSetup.openingMinor(it, decimals) }
            require(minor == null || minor > 0)
            onSave(
                template.copy(
                    name = name,
                    kind = kind,
                    accountId = account.ifBlank { null },
                    currency = currency,
                    amountMinor = minor,
                    categoryId = if (kind == "TRANSFER") null else category.ifBlank { null },
                    payeeId = payee.ifBlank { null },
                    note = note,
                    incomeSourceId = source.ifBlank { null },
                    splitTemplateId = split.ifBlank { null },
                ),
            )
        }.onFailure { error = true }
    }) {
        P13Name(name, { name = it })
        MzSegmented(names, kinds.indexOf(kind), onSelected = {
            kind = kinds[it]
            category = ""
        })
        P13Choice(stringResource(R.string.p13_account), account, listOf(none) + accounts.map { it.id to it.name }) { account = it }
        P13Choice(stringResource(R.string.p13_currency), currency, currencies.filter { it.enabled }.map { it.code to it.code }) {
            currency =
                it
        }
        OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.p13_optional_amount)) }, singleLine = true)
        if (kind !=
            "TRANSFER"
        ) {
            P13Choice(
                stringResource(R.string.p13_category),
                category,
                listOf(none) + categories.filter { it.kind == kind && !it.archived }.map { it.id to it.name },
            ) { category = it }
        }
        P13Choice(stringResource(R.string.p13_payee), payee, listOf(none) + payees.map { it.id to it.name }) { payee = it }
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.p13_note)) })
        if (kind == "INCOME") {
            P13Choice(stringResource(R.string.p13_income_sources), source, listOf(none) + sources.map { it.id to it.name }) { source = it }
            P13Choice(stringResource(R.string.p13_default_split), split, listOf(none) + splits.map { it.id to it.name }) { split = it }
        }
        if (error) Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense)
    }
}

@Composable
private fun P13Name(
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(value, onChange, label = { Text(stringResource(R.string.p13_name)) }, singleLine = true)
}

@Composable
private fun P13EditorDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.p13_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.p13_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p13_cancel)) } },
    )
}
