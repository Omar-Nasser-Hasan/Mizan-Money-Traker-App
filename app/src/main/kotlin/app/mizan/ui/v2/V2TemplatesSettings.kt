// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

@Composable
fun V2TemplatesScreen(
    repository: MizanRepository,
    onCreateAccount: () -> Unit,
) {
    val splits by remember(repository) { repository.observeSplitTemplates() }.collectAsStateWithLifecycle(null)
    val lines by remember(repository) { repository.observeAllSplitLines() }.collectAsStateWithLifecycle(emptyList())
    val sources by remember(repository) { repository.observeIncomeSources() }.collectAsStateWithLifecycle(emptyList())
    val records by remember(repository) { repository.observeAllRecordTemplates() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember(repository) { repository.observeSetupCategories() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val payees by remember(repository) { repository.observePayees() }.collectAsStateWithLifecycle(emptyList())
    val hidden by remember(repository) { repository.observeHideAmounts() }.collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    if (splits != null && accounts.isEmpty()) {
        V2SettingsList {
            item {
                MzEmptyState(
                    stringResource(R.string.p13_need_account),
                    stringResource(R.string.p13_categories_hint),
                    stringResource(R.string.p13_create_account),
                    onCreateAccount,
                )
            }
        }
        return
    }
    V2TemplatesContent(
        splits,
        lines,
        sources,
        records,
        accounts,
        categories,
        currencies,
        payees,
        hidden,
        onSplit = { template, values -> scope.launch { error = runCatching { repository.saveSplitTemplate(template, values) }.isFailure } },
        onSource = { value -> scope.launch { error = runCatching { repository.saveIncomeSource(value) }.isFailure } },
        onRecord = { value -> scope.launch { error = runCatching { repository.saveRecordTemplate(value) }.isFailure } },
        onArchiveSplit = { value ->
            val savedLines = lines.filter { it.templateId == value.id }
            scope.launch {
                error = runCatching { repository.saveSplitTemplate(value.copy(archived = true), savedLines) }.isFailure
                if (!error) {
                    undo = {
                        scope.launch { repository.saveSplitTemplate(value, savedLines) }
                        undo = null
                    }
                }
            }
        },
        onArchiveSource = { value ->
            scope.launch {
                error = runCatching { repository.saveIncomeSource(value.copy(archived = true)) }.isFailure
                if (!error) {
                    undo = {
                        scope.launch { repository.saveIncomeSource(value) }
                        undo = null
                    }
                }
            }
        },
        onArchiveRecord = { value ->
            scope.launch {
                error = runCatching { repository.saveRecordTemplate(value.copy(archived = true)) }.isFailure
                if (!error) {
                    undo = {
                        scope.launch { repository.saveRecordTemplate(value) }
                        undo = null
                    }
                }
            }
        },
        onUndo = undo,
        error = error,
    )
}

@Composable
fun V2TemplatesContent(
    splits: List<SplitTemplateEntity>?,
    lines: List<SplitLineEntity>,
    sources: List<IncomeSourceEntity>,
    records: List<RecordTemplateEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    currencies: List<CurrencyEntity>,
    payees: List<PayeeEntity>,
    hideAmounts: Boolean,
    onSplit: (SplitTemplateEntity, List<SplitLineEntity>) -> Unit,
    onSource: (IncomeSourceEntity) -> Unit,
    onRecord: (RecordTemplateEntity) -> Unit,
    onArchiveSplit: (SplitTemplateEntity) -> Unit,
    onArchiveSource: (IncomeSourceEntity) -> Unit,
    onArchiveRecord: (RecordTemplateEntity) -> Unit,
    onUndo: (() -> Unit)? = null,
    error: Boolean = false,
) {
    var splitForm by remember { mutableStateOf<SplitTemplateEntity?>(null) }
    var sourceForm by remember { mutableStateOf<IncomeSourceEntity?>(null) }
    var recordForm by remember { mutableStateOf<RecordTemplateEntity?>(null) }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    V2SettingsList {
        if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
        if (onUndo != null) item { MzButton(stringResource(R.string.p13_undo), onUndo, style = MzButtonStyle.GHOST) }
        item { Text(stringResource(R.string.p13_income_splits), style = MzTypography.title) }
        if (splits == null) {
            items(3) { MzSkeleton(Modifier.fillMaxWidth(), 100.dp) }
        } else {
            if (splits.isEmpty()) item { Text(stringResource(R.string.p13_no_splits), style = MzTypography.bodySmall) }
            items(splits, key = SplitTemplateEntity::id) { split ->
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(split.name, style = MzTypography.title)
                        lines.filter { it.templateId == split.id }.forEach { line ->
                            Row {
                                Text(accountNames[line.accountId] ?: stringResource(R.string.p13_account), Modifier.weight(1f))
                                if (line.mode ==
                                    "PERCENT"
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.p13_percent_value,
                                            BigDecimal.valueOf(line.value, 2).stripTrailingZeros().toPlainString(),
                                        ),
                                    )
                                } else {
                                    AmountText(
                                        line.value,
                                        accounts.firstOrNull { it.id == line.accountId }?.defaultCurrency ?: "EGP",
                                        hideAmounts = hideAmounts,
                                    )
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { splitForm = split }) { Text(stringResource(R.string.p13_edit)) }
                            TextButton(onClick = { onArchiveSplit(split) }) { Text(stringResource(R.string.p13_archive)) }
                        }
                    }
                }
            }
        }
        item {
            MzButton(stringResource(R.string.p13_new_split), {
                splitForm =
                    SplitTemplateEntity(UUID.randomUUID().toString(), "", accounts.firstOrNull()?.id)
            }, style = MzButtonStyle.SECONDARY)
        }
        item { Text(stringResource(R.string.p13_income_sources), style = MzTypography.title) }
        items(sources, key = IncomeSourceEntity::id) { source ->
            MzCard {
                MzListRow(
                    "work",
                    source.name,
                    source.name,
                    splits?.firstOrNull { it.id == source.defaultTemplateId }?.name ?: stringResource(R.string.p13_no_split),
                )
                Row {
                    TextButton(onClick = { sourceForm = source }) { Text(stringResource(R.string.p13_edit)) }
                    TextButton(onClick = { onArchiveSource(source) }) { Text(stringResource(R.string.p13_archive)) }
                }
            }
        }
        item {
            MzButton(stringResource(R.string.p13_new_source), {
                sourceForm = IncomeSourceEntity(UUID.randomUUID().toString(), "")
            }, style = MzButtonStyle.SECONDARY)
        }
        item { Text(stringResource(R.string.p13_record_templates), style = MzTypography.title) }
        items(records, key = RecordTemplateEntity::id) { record ->
            MzCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.name, style = MzTypography.title)
                    Text(
                        accountNames[record.accountId] ?: stringResource(R.string.p13_choose_when_used),
                        style = MzTypography.bodySmall,
                    )
                    record.amountMinor?.let {
                        AmountText(
                            it,
                            record.currency ?: "EGP",
                            style =
                                when (record.kind) {
                                    "INCOME" -> AmountStyle.INCOME
                                    "TRANSFER" -> AmountStyle.TRANSFER
                                    else -> AmountStyle.EXPENSE
                                },
                            hideAmounts = hideAmounts,
                        )
                    }
                    Row {
                        TextButton(onClick = { recordForm = record }) { Text(stringResource(R.string.p13_edit)) }
                        TextButton(onClick = { onArchiveRecord(record) }) { Text(stringResource(R.string.p13_archive)) }
                    }
                }
            }
        }
        item {
            MzButton(stringResource(R.string.p13_new_record_template), {
                recordForm =
                    RecordTemplateEntity(UUID.randomUUID().toString(), "", "EXPENSE")
            }, style = MzButtonStyle.SECONDARY)
        }
    }
    splitForm?.let { value ->
        P13SplitEditor(value, lines.filter { it.templateId == value.id }, accounts, currencies, { splitForm = null }) { template, values ->
            onSplit(template, values)
            splitForm = null
        }
    }
    sourceForm?.let { value ->
        P13SourceEditor(value, splits.orEmpty(), categories, { sourceForm = null }) {
            onSource(it)
            sourceForm = null
        }
    }
    recordForm?.let { value ->
        P13RecordTemplateEditor(value, accounts, categories, currencies, payees, sources, splits.orEmpty(), { recordForm = null }) {
            onRecord(it)
            recordForm =
                null
        }
    }
}
