// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ledger.EntryCalculator
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeAllocation
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.IncomeSplitCalculator
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.SplitIncomeRequest
import app.mizan.domain.ledger.SplitLine
import app.mizan.domain.ledger.SplitMode
import app.mizan.domain.ledger.TransferRequest
import app.mizan.domain.ledger.TxnStatus
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.budgetWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Composable
fun V2EntryScreen(
    repository: MizanRepository,
    initialKind: String,
    lastAccountId: String?,
    hideAmounts: Boolean,
    editTransactionId: String? = null,
    inboxDraftId: String? = null,
    presetTransferTargetId: String? = null,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val sources by repository.observeIncomeSources().collectAsStateWithLifecycle(emptyList())
    val templates by repository.observeSplitTemplates().collectAsStateWithLifecycle(emptyList())
    val budgets by repository.observeBudgets().collectAsStateWithLifecycle(emptyList())
    val payees by repository.observePayees().collectAsStateWithLifecycle(emptyList())
    val labels by repository.observeLabels().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    val recordTemplates by repository.observeRecordTemplates(kind).collectAsStateWithLifecycle(emptyList())
    val categories by repository
        .observeCategories(if (kind == "INCOME") "INCOME" else "EXPENSE")
        .collectAsStateWithLifecycle(emptyList())
    var accountId by remember(accounts, lastAccountId) { mutableStateOf(lastAccountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var targetId by remember(accounts, presetTransferTargetId) {
        mutableStateOf(presetTransferTargetId ?: accounts.firstOrNull { it.id != accountId }?.id.orEmpty())
    }
    var categoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: MizanRepository.UNCATEGORIZED_ID) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var templateId by remember { mutableStateOf<String?>(null) }
    val persistedLines by repository.observeSplitLines(templateId ?: "").collectAsStateWithLifecycle(emptyList())
    var customSplitLines by remember { mutableStateOf<List<SplitLine>?>(null) }
    var showSplitSheet by remember { mutableStateOf(false) }
    var showRecordTemplates by remember { mutableStateOf(false) }
    var customSplitMode by remember { mutableStateOf(SplitMode.PERCENT) }
    var expression by remember { mutableStateOf("") }
    var feeExpression by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var duplicateConfirmed by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var expandedCategoryParentId by remember { mutableStateOf<String?>(null) }
    var categorySearch by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var payeeQuery by remember { mutableStateOf("") }
    var selectedLabelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newLabelName by remember { mutableStateOf("") }
    var receiptUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var bookingRateOverride by remember(editTransactionId) { mutableStateOf<BigDecimal?>(null) }
    var targetBookingRateOverride by remember(editTransactionId) { mutableStateOf<BigDecimal?>(null) }
    var editLoaded by remember(editTransactionId) { mutableStateOf(false) }
    val receiptPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            receiptUris = receiptUris + uris.map { it.toString() }
        }
    var paymentType by remember { mutableStateOf(PaymentType.CASH) }
    var status by remember { mutableStateOf(TxnStatus.CLEARED) }
    var bookedDate by remember { mutableStateOf(LocalDate.now(ZoneId.of("Africa/Cairo"))) }
    var timeText by remember {
        mutableStateOf(
            LocalTime
                .now(ZoneId.of("Africa/Cairo"))
                .withSecond(0)
                .withNano(0)
                .toString(),
        )
    }
    val account = accounts.firstOrNull { it.id == accountId }
    val target = accounts.firstOrNull { it.id == targetId }
    val buckets by repository.observeBuckets(accountId).collectAsStateWithLifecycle(emptyList())
    val bucketDeltas by repository.observeBucketDeltas(accountId).collectAsStateWithLifecycle(emptyList())
    val targetBuckets by repository.observeBuckets(targetId).collectAsStateWithLifecycle(emptyList())
    var currency by remember(accountId, account?.defaultCurrency) { mutableStateOf(account?.defaultCurrency ?: "EGP") }
    var targetCurrency by remember(targetId, target?.defaultCurrency) { mutableStateOf(target?.defaultCurrency ?: "EGP") }
    val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
    val targetDecimals = currencies.firstOrNull { it.code == targetCurrency }?.decimals ?: 2
    val sourceMarketRateFlow = remember(currency) { repository.observeMarketRate(currency) }
    val targetMarketRateFlow = remember(targetCurrency) { repository.observeMarketRate(targetCurrency) }
    val sourceMarketRate by sourceMarketRateFlow.collectAsStateWithLifecycle(null)
    val targetMarketRate by targetMarketRateFlow.collectAsStateWithLifecycle(null)
    val amount = runCatching { EntryCalculator.minorUnits(expression, decimals) }.getOrDefault(0L)
    val feeAmount = runCatching { EntryCalculator.minorUnits(feeExpression, decimals) }.getOrDefault(0L)
    val suggestedReceived =
        if (amount > 0L && sourceMarketRate != null && targetMarketRate != null) {
            runCatching {
                MoneyMath.convertMinor(
                    amount,
                    decimals,
                    sourceMarketRate!!.egpPerUnit,
                    targetDecimals,
                    targetMarketRate!!.egpPerUnit,
                )
            }.getOrNull()
        } else {
            null
        }
    var receivedExpression by remember(currency, targetCurrency) { mutableStateOf("") }
    val editRows by repository.observeRecordRows(editTransactionId.orEmpty()).collectAsStateWithLifecycle(emptyList())
    val inboxDraft by repository.observeInboxDraft(inboxDraftId.orEmpty()).collectAsStateWithLifecycle(null)
    val receivedAmount =
        runCatching { EntryCalculator.minorUnits(receivedExpression, targetDecimals) }.getOrNull() ?: suggestedReceived ?: 0L
    val activeSplitLines =
        customSplitLines
            ?: persistedLines.map { SplitLine(it.id, it.templateId, it.accountId, SplitMode.valueOf(it.mode), it.value) }
    val allocations = splitAllocations(amount, activeSplitLines, templates.firstOrNull { it.id == templateId }?.remainderAccountId)
    val selectedBucketBalance =
        (buckets.firstOrNull { it.currency == currency }?.openingMinor ?: 0L) +
            (bucketDeltas.firstOrNull { it.currency == currency }?.amountMinor ?: 0L)
    val wouldOverdraw = kind == "EXPENSE" && amount > 0L && selectedBucketBalance - amount < 0L

    androidx.compose.runtime.LaunchedEffect(editTransactionId, editRows, currencies, labels) {
        if (editTransactionId == null || editLoaded || editRows.isEmpty()) return@LaunchedEffect
        val first = editRows.first()
        val sourceRow = editRows.firstOrNull { it.amountMinor < 0L } ?: first
        val destinationRow = editRows.firstOrNull { it.amountMinor > 0L }
        val sourceDecimals = currencies.firstOrNull { it.code == sourceRow.currency }?.decimals ?: 2
        kind = first.kind
        accountId = sourceRow.accountId
        currency = sourceRow.currency
        categoryId = first.categoryId ?: MizanRepository.UNCATEGORIZED_ID
        expression =
            BigDecimal
                .valueOf(sourceRow.amountMinor)
                .abs()
                .movePointLeft(sourceDecimals)
                .toPlainString()
        note = first.note
        payeeQuery = first.payeeName.orEmpty()
        status = TxnStatus.valueOf(first.status)
        paymentType = PaymentType.valueOf(first.paymentType)
        bookedDate = LocalDate.parse(first.bookedDate)
        timeText =
            Instant
                .ofEpochMilli(first.bookedAt)
                .atZone(ZoneId.of("Africa/Cairo"))
                .toLocalTime()
                .withSecond(0)
                .withNano(0)
                .toString()
        bookingRateOverride = BigDecimal(sourceRow.rateToBase)
        selectedLabelIds = labels.filter { it.name in first.labels.split('|') }.mapTo(linkedSetOf()) { it.id }
        receiptUris = first.attachments.split('|').filter(String::isNotBlank)
        if (first.kind == "INCOME" && editRows.size > 1) {
            customSplitMode = SplitMode.FIXED
            customSplitLines =
                editRows.map { row ->
                    SplitLine("edit-${row.entryId}", "edit", row.accountId, SplitMode.FIXED, row.amountMinor)
                }
        }
        if (first.kind == "TRANSFER" && destinationRow != null) {
            targetId = destinationRow.accountId
            targetCurrency = destinationRow.currency
            val destinationDecimals = currencies.firstOrNull { it.code == destinationRow.currency }?.decimals ?: 2
            receivedExpression = BigDecimal.valueOf(destinationRow.amountMinor).movePointLeft(destinationDecimals).toPlainString()
            targetBookingRateOverride = BigDecimal(destinationRow.rateToBase)
        }
        editLoaded = true
    }
    androidx.compose.runtime.LaunchedEffect(inboxDraftId, inboxDraft, currencies, accounts) {
        val draft = inboxDraft ?: return@LaunchedEffect
        if (editTransactionId != null || editLoaded) return@LaunchedEffect
        val draftDecimals = currencies.firstOrNull { it.code == draft.currency }?.decimals ?: 2
        kind = if (draft.kind == "INCOME" || draft.kind == "REVERSAL") "INCOME" else draft.kind
        currency = draft.currency
        expression = BigDecimal.valueOf(draft.amountMinor).movePointLeft(draftDecimals).toPlainString()
        payeeQuery = draft.merchant.orEmpty()
        bookedDate = Instant.ofEpochMilli(draft.occurredAt).atZone(ZoneId.of("Africa/Cairo")).toLocalDate()
        timeText =
            Instant
                .ofEpochMilli(draft.occurredAt)
                .atZone(ZoneId.of("Africa/Cairo"))
                .toLocalTime()
                .withSecond(0)
                .withNano(0)
                .toString()
        if (draft.kind == "TRANSFER") {
            targetId =
                accounts
                    .firstOrNull { it.name.equals(if (draft.merchant == "ATM") "Cash" else "Investment", true) }
                    ?.id
                    .orEmpty()
        }
        editLoaded = true
    }

    fun applyPreset(preset: RecordTemplateEntity) {
        preset.accountId?.takeIf { candidate -> accounts.any { it.id == candidate } }?.let { accountId = it }
        preset.categoryId?.let { categoryId = it }
        templateId = preset.splitTemplateId
        customSplitLines = null
        preset.amountMinor?.let { minor ->
            val presetDecimals = currencies.firstOrNull { it.code == (preset.currency ?: currency) }?.decimals ?: decimals
            expression = BigDecimal.valueOf(minor).movePointLeft(presetDecimals).toPlainString()
        }
    }

    fun clearForAnother() {
        expression = ""
        feeExpression = ""
        receivedExpression = ""
        note = ""
        payeeQuery = ""
        selectedLabelIds = emptySet()
        receiptUris = emptyList()
        paymentType = PaymentType.CASH
        status = TxnStatus.CLEARED
        duplicateConfirmed = false
        message = "Ready for another ${kind.lowercase()}."
    }

    suspend fun saveEntry(keepOpen: Boolean) {
        val current = account ?: return
        if (amount <= 0L) {
            message = "Enter an amount"
            return
        }
        if (kind == "TRANSFER" && receivedAmount <= 0L) {
            message = "Enter an amount received"
            return
        }
        val rate = bookingRateOverride ?: repository.effectiveRate(currency, bookedDate)
        if (rate == null) {
            message = "No booking rate is available for $currency on $bookedDate"
            return
        }
        val bookedAt =
            runCatching {
                LocalDateTime.of(bookedDate, LocalTime.parse(timeText)).atZone(ZoneId.of("Africa/Cairo")).toInstant()
            }.getOrElse {
                message = "Enter time as HH:mm"
                return
            }
        val payeeId = payees.firstOrNull { it.name.equals(payeeQuery.trim(), ignoreCase = true) }?.id
        if (editTransactionId == null && kind != "TRANSFER" && !duplicateConfirmed &&
            repository.isPossibleDuplicate(current.id, amount, payeeId, bookedAt)
        ) {
            duplicateConfirmed = true
            message = "Possible duplicate. Tap Save again to post it."
            return
        }
        duplicateConfirmed = false
        val posting =
            when (kind) {
                "EXPENSE" -> {
                    LedgerService().postExpense(
                        ExpenseRequest(
                            current.id,
                            currency,
                            amount,
                            decimals,
                            rate,
                            categoryId,
                            bookedAt,
                            bookedDate,
                            payeeId,
                            note,
                            status,
                            paymentType,
                        ),
                    )
                }

                "INCOME" -> {
                    if (allocations.isEmpty()) {
                        LedgerService().postIncome(
                            IncomeRequest(
                                current.id,
                                currency,
                                amount,
                                decimals,
                                rate,
                                bookedAt,
                                bookedDate,
                                categoryId,
                                payeeId,
                                note,
                                status,
                                paymentType,
                            ),
                        )
                    } else {
                        LedgerService().postSplitIncome(
                            SplitIncomeRequest(
                                allocations,
                                currency,
                                decimals,
                                rate,
                                bookedAt,
                                bookedDate,
                                categoryId,
                                payeeId,
                                note,
                                status,
                                paymentType,
                            ),
                        )
                    }
                }

                else -> {
                    val destination =
                        target ?: run {
                            message = "Choose another account"
                            return
                        }
                    if (current.id == destination.id && currency == targetCurrency) {
                        message = "Choose a different account or currency"
                        return
                    }
                    val targetRate =
                        targetBookingRateOverride ?: repository.effectiveRate(targetCurrency, bookedDate) ?: run {
                            message = "No booking rate is available for $targetCurrency"
                            return
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
                            receivedAmount,
                            targetDecimals,
                            targetRate,
                            bookedAt,
                            bookedDate,
                            note,
                            status,
                            paymentType,
                            fee =
                                if (feeAmount > 0L) {
                                    ExpenseRequest(
                                        current.id,
                                        currency,
                                        feeAmount,
                                        decimals,
                                        rate,
                                        categoryId,
                                        bookedAt,
                                        bookedDate,
                                        payeeId,
                                        "Transfer fee${if (note.isBlank()) "" else ": $note"}",
                                        status,
                                        paymentType,
                                    )
                                } else {
                                    null
                                },
                        ),
                    )
                }
            }
        if (editTransactionId == null) {
            repository.post(posting, selectedLabelIds, receiptUris)
            if (inboxDraftId != null) repository.confirmInboxDraft(inboxDraftId)
        } else {
            repository.replaceRecord(editTransactionId, posting, selectedLabelIds, receiptUris)
        }
        repository.setLastUsedAccountId(current.id)
        if (keepOpen) clearForAnother() else onDone()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
    ) {
        item {
            MzSegmented(
                options = listOf("Expense", "Income", "Transfer"),
                selectedIndex = listOf("EXPENSE", "INCOME", "TRANSFER").indexOf(kind).coerceAtLeast(0),
                onSelected = { kind = listOf("EXPENSE", "INCOME", "TRANSFER")[it] },
            )
        }
        if (recordTemplates.isNotEmpty()) {
            item {
                MzChip("Templates", false, { showRecordTemplates = true })
            }
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
                    MzChip("Choose", false, { showCategoryPicker = true })
                }
            }
            if (wouldOverdraw) {
                item {
                    Text(
                        "${account?.name ?: "This account"} will go below zero. Allowed",
                        color = LocalMzTokens.current.warning,
                        style = MzTypography.bodySmall,
                    )
                }
            }
            item {
                V2EntryBudgetLine(
                    repository = repository,
                    budgets = budgets,
                    accountId = accountId,
                    categoryId = categoryId,
                    bookedDate = bookedDate,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MzChip(bookedDate.toString(), false, { showDatePicker = true })
                MzChip("More", showMore, { showMore = true })
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
                                source.defaultCategoryId?.let { categoryId = it }
                            },
                        )
                    }
                }
            }
            item { Text("Split", style = MzTypography.labelCaps) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MzChip(
                        templates.firstOrNull { it.id == templateId }?.name
                            ?: if (customSplitLines != null) "Custom" else "All to ${account?.name ?: "account"}",
                        true,
                        { showSplitSheet = true },
                    )
                    MzChip("Templates", false, { showSplitSheet = true })
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
            item { AccountChips(accounts, targetId) { targetId = it } }
            item {
                MzChip("Swap from and to", false, onClick = {
                    val previousAccountId = accountId
                    val previousCurrency = currency
                    accountId = targetId
                    targetId = previousAccountId
                    currency = targetCurrency
                    targetCurrency = previousCurrency
                })
            }
            if (targetBuckets.size > 1) {
                item {
                    CurrencyChips(targetBuckets.map { it.currency }, targetCurrency) { targetCurrency = it }
                }
            }
            item {
                MzInput(
                    value = receivedExpression,
                    onValueChange = { receivedExpression = it },
                    label = "Amount received in $targetCurrency (market suggestion: ${suggestedReceived ?: "unavailable"})",
                )
            }
            item {
                MzInput(
                    value = feeExpression,
                    onValueChange = { feeExpression = it },
                    label = "Optional fee in $currency",
                )
            }
            item {
                val quote =
                    if (sourceMarketRate != null && targetMarketRate != null) {
                        MoneyMath
                            .destinationUnitsPerSourceUnit(
                                sourceMarketRate!!.egpPerUnit,
                                targetMarketRate!!.egpPerUnit,
                            ).stripTrailingZeros()
                            .toPlainString()
                    } else {
                        "unavailable"
                    }
                val ageHours =
                    sourceMarketRate?.fetchedAt?.let { fetchedAt ->
                        Duration.between(Instant.ofEpochMilli(fetchedAt), Instant.now()).toHours().coerceAtLeast(0)
                    }
                Text(
                    "Market rate: 1 $currency = $quote $targetCurrency${ageHours?.let { " · ${it}h old" } ?: ""}",
                    style = MzTypography.bodySmall,
                )
            }
            item { Text("Not income or expense", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall) }
        }
        item { MzButton("Save", { scope.launch { saveEntry(keepOpen = false) } }, modifier = Modifier.fillMaxWidth()) }
        item {
            MzButton("Save and add another", {
                scope.launch { saveEntry(keepOpen = true) }
            }, style = MzButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
        if (message.isNotBlank()) item { Text(message, color = LocalMzTokens.current.warning) }
    }
    if (showDatePicker) {
        MzDatePickerSheet(
            date = bookedDate,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = {
                bookedDate = it
                showDatePicker = false
            },
        )
    }
    if (showMore) {
        MzSheet(onDismissRequest = { showMore = false }) {
            Text("More details", style = MzTypography.title)
            MzInput(note, { note = it }, "Note", singleLine = false)
            MzInput(payeeQuery, { payeeQuery = it }, "Payee")
            payees.filter { it.name.contains(payeeQuery, ignoreCase = true) }.take(4).forEach { payee ->
                MzChip(payee.name, payee.name.equals(payeeQuery, ignoreCase = true), onClick = { payeeQuery = payee.name })
            }
            Text("Labels", style = MzTypography.labelCaps)
            labels.forEach { label ->
                MzChip(label.name, label.id in selectedLabelIds, onClick = {
                    selectedLabelIds = if (label.id in selectedLabelIds) selectedLabelIds - label.id else selectedLabelIds + label.id
                })
            }
            MzInput(newLabelName, { newLabelName = it }, "Create label")
            MzButton("Add label", onClick = {
                val name = newLabelName.trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        val id = UUID.randomUUID().toString()
                        repository.saveLabel(LabelEntity(id, name, "#F2B84B"))
                        selectedLabelIds += id
                        newLabelName = ""
                    }
                }
            }, style = MzButtonStyle.GHOST)
            Text("Receipts", style = MzTypography.labelCaps)
            MzButton(
                "Add receipt",
                onClick = {
                    receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                style = MzButtonStyle.SECONDARY,
            )
            receiptUris.forEachIndexed { index, uri ->
                ReceiptThumbnail(
                    uri = uri,
                    ordinal = index + 1,
                    onRemove = { receiptUris = receiptUris - uri },
                )
            }
            Text("Payment type", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaymentType.entries.forEach { candidate ->
                    MzChip(candidate.name.lowercase().replace('_', ' '), paymentType == candidate, { paymentType = candidate })
                }
            }
            Text("Status", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TxnStatus.entries.forEach { candidate ->
                    MzChip(candidate.name.lowercase(), status == candidate, { status = candidate })
                }
            }
            MzInput(timeText, { timeText = it }, "Time (HH:mm)")
            MzButton("Done", { showMore = false }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (showSplitSheet) {
        MzSheet(onDismissRequest = { showSplitSheet = false }) {
            Text("Income split", style = MzTypography.title)
            Text("Saved templates", style = MzTypography.labelCaps)
            templates.forEach { template ->
                MzChip(template.name, template.id == templateId && customSplitLines == null, onClick = {
                    templateId = template.id
                    customSplitLines = null
                })
            }
            MzChip("All to ${account?.name ?: "account"}", templateId == null && customSplitLines == null, onClick = {
                templateId = null
                customSplitLines = null
            })
            MzChip("Custom", customSplitLines != null, onClick = {
                templateId = null
                customSplitMode = SplitMode.PERCENT
                customSplitLines =
                    accounts.mapIndexed { index, candidate ->
                        SplitLine("custom-${candidate.id}", "custom", candidate.id, SplitMode.PERCENT, if (index == 0) 10_000 else 0)
                    }
            })
            customSplitLines?.let { customLines ->
                Text("Custom allocation", style = MzTypography.labelCaps)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MzChip("Percent", customSplitMode == SplitMode.PERCENT, {
                        customSplitMode = SplitMode.PERCENT
                        customSplitLines = customLines.map { it.copy(mode = SplitMode.PERCENT) }
                    })
                    MzChip("Fixed amount", customSplitMode == SplitMode.FIXED, {
                        customSplitMode = SplitMode.FIXED
                        customSplitLines = customLines.map { it.copy(mode = SplitMode.FIXED) }
                    })
                }
                customLines.forEach { line ->
                    val accountName = accounts.firstOrNull { it.id == line.accountId }?.name ?: line.accountId
                    val value =
                        if (customSplitMode == SplitMode.PERCENT) {
                            BigDecimal
                                .valueOf(line.value)
                                .movePointLeft(2)
                                .stripTrailingZeros()
                                .toPlainString()
                        } else {
                            line.value.toString()
                        }
                    MzInput(
                        value = value,
                        onValueChange = { entered ->
                            val nextValue =
                                if (customSplitMode == SplitMode.PERCENT) {
                                    runCatching { BigDecimal(entered).movePointRight(2).longValueExact() }.getOrDefault(0)
                                } else {
                                    entered.toLongOrNull() ?: 0
                                }
                            customSplitLines =
                                customLines.map {
                                    if (it.accountId ==
                                        line.accountId
                                    ) {
                                        it.copy(mode = customSplitMode, value = nextValue)
                                    } else {
                                        it.copy(mode = customSplitMode)
                                    }
                                }
                        },
                        label = "$accountName ${if (customSplitMode == SplitMode.PERCENT) "%" else "minor units"}",
                    )
                }
            }
            val live = splitAllocations(amount, activeSplitLines, templates.firstOrNull { it.id == templateId }?.remainderAccountId)
            Text("Live remainder: ${amount - live.sumOf { it.amountMinor }} minor", style = MzTypography.bodySmall)
            live.forEach { allocation ->
                Text(
                    "${accounts
                        .firstOrNull {
                            it.id == allocation.accountId
                        }?.name ?: allocation.accountId}: ${allocation.amountMinor} minor",
                )
            }
            MzButton("Apply split", { showSplitSheet = false }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (showRecordTemplates) {
        MzSheet(onDismissRequest = { showRecordTemplates = false }) {
            Text("One-tap templates", style = MzTypography.title)
            recordTemplates.forEach { preset ->
                MzChip(preset.name, false, onClick = {
                    applyPreset(preset)
                    showRecordTemplates = false
                })
            }
        }
    }
    if (showCategoryPicker) {
        MzSheet(onDismissRequest = { showCategoryPicker = false }) {
            Text("Choose category", style = MzTypography.title)
            MzInput(categorySearch, { categorySearch = it }, "Search categories")
            Text("Frequent", style = MzTypography.labelCaps)
            categories.take(6).forEach { category ->
                MzChip(category.name, category.id == categoryId, onClick = {
                    categoryId = category.id
                    showCategoryPicker = false
                })
            }
            Text("All categories", style = MzTypography.labelCaps)
            categories.filter { it.parentId == null && it.name.contains(categorySearch, ignoreCase = true) }.forEach { parent ->
                MzChip(
                    label = if (expandedCategoryParentId == parent.id) "⌄ ${parent.name}" else "› ${parent.name}",
                    selected = parent.id == categoryId,
                    onClick = {
                        val children = categories.filter { it.parentId == parent.id }
                        if (children.isEmpty()) {
                            categoryId = parent.id
                            showCategoryPicker = false
                        } else {
                            expandedCategoryParentId = if (expandedCategoryParentId == parent.id) null else parent.id
                        }
                    },
                )
                if (expandedCategoryParentId == parent.id) {
                    categories.filter { it.parentId == parent.id }.forEach { child ->
                        MzChip("  ${child.name}", child.id == categoryId, onClick = {
                            categoryId = child.id
                            showCategoryPicker = false
                        })
                    }
                }
            }
            MzInput(newCategoryName, { newCategoryName = it }, "New category")
            MzButton(
                "Create category",
                onClick = {
                    val name = newCategoryName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            val id = UUID.randomUUID().toString()
                            repository.saveCategory(
                                CategoryEntity(
                                    id,
                                    null,
                                    name,
                                    "category",
                                    "#F2B84B",
                                    if (kind == "INCOME") "INCOME" else "EXPENSE",
                                ),
                            )
                            categoryId = id
                            newCategoryName = ""
                            showCategoryPicker = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    lines: List<SplitLine>,
    remainderAccountId: String?,
): List<IncomeAllocation> =
    if (amount <= 0L || lines.isEmpty()) {
        emptyList()
    } else {
        runCatching {
            IncomeSplitCalculator
                .allocate(
                    amount,
                    lines,
                    remainderAccountId,
                ).allocations
        }.getOrDefault(emptyList())
    }

@Composable
private fun ReceiptThumbnail(
    uri: String,
    ordinal: Int,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(uri) {
        image =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openInputStream(Uri.parse(uri))
                        ?.use(BitmapFactory::decodeStream)
                        ?.asImageBitmap()
                }.getOrNull()
            }
    }
    MzCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onRemove),
    ) {
        Row(
            modifier = Modifier.padding(MzSpacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Receipt $ordinal. Tap to remove.",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                if (image == null) "Receipt $ordinal loading…" else "Receipt $ordinal · tap to remove",
                style = MzTypography.bodySmall,
            )
        }
    }
}

@Composable
private fun V2EntryBudgetLine(
    repository: MizanRepository,
    budgets: List<BudgetEntity>,
    accountId: String,
    categoryId: String,
    bookedDate: LocalDate,
) {
    val applicable =
        budgets.filter { budget ->
            when (budget.scope) {
                "ALL" -> true
                "ACCOUNT" -> accountId in budget.scopeIds.split(',')
                "CATEGORY" -> categoryId in budget.scopeIds.split(',')
                else -> false
            }
        }
    var summary by remember(applicable, bookedDate) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(applicable, bookedDate) {
        summary =
            applicable.firstOrNull()?.let { budget ->
                val window =
                    runCatching {
                        budgetWindow(
                            BudgetPeriod.valueOf(budget.period),
                            bookedDate,
                            budget.startDate?.let(LocalDate::parse),
                            budget.endDate?.let(LocalDate::parse),
                        )
                    }.getOrNull() ?: return@let null
                val spent =
                    repository.budgetSpent(
                        window.start.toString(),
                        minOf(bookedDate, window.end).toString(),
                        budget.scope,
                        budget.scopeIds,
                    )
                val remaining = budget.limitMinor - spent
                "${budget.name}: ${if (remaining >= 0L) "$remaining EGP minor left" else "${-remaining} EGP minor over"}"
            }
    }
    summary?.let { Text(it, color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall) }
}
