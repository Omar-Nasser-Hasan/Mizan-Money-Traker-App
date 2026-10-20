// Copyright (c) 2026 Mizan contributors
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.PayeeEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.entry.DraftRecord
import app.mizan.domain.entry.ParserAccount
import app.mizan.domain.entry.ParserCategory
import app.mizan.domain.entry.ParserContext
import app.mizan.domain.entry.VoiceParser
import app.mizan.domain.ledger.IncomeSplitCalculator
import app.mizan.domain.ledger.SplitLine
import app.mizan.domain.ledger.SplitMode
import app.mizan.voice.OnDeviceVoiceRecognizer
import app.mizan.voice.VoiceDraftCommitter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun V2VoiceReviewScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onDone: () -> Unit,
    onOpenVoiceSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val incomeSources by remember(repository) { repository.observeIncomeSources() }.collectAsStateWithLifecycle(emptyList())
    val splitLines by remember(repository) { repository.observeAllSplitLines() }.collectAsStateWithLifecycle(emptyList())
    val splitTemplates by remember(repository) { repository.observeSplitTemplates() }.collectAsStateWithLifecycle(emptyList())
    val expenses by remember(repository, "EXPENSE") { repository.observeCategories("EXPENSE") }.collectAsStateWithLifecycle(emptyList())
    val incomes by remember(repository, "INCOME") { repository.observeCategories("INCOME") }.collectAsStateWithLifecycle(emptyList())
    val learned by remember(repository) { repository.observeLearnedPayeeCategories() }.collectAsStateWithLifecycle(emptyMap())
    val recognizer = remember { OnDeviceVoiceRecognizer(context) }
    val scope = rememberCoroutineScope()
    var transcript by remember { mutableStateOf("") }
    var finalTranscript by remember { mutableStateOf("") }
    var partialTranscript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Speak a record, then review every draft before saving.") }
    var listening by remember { mutableStateOf(false) }
    var reviewOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("ar-EG") }
    var showVoiceSettings by remember { mutableStateOf(false) }
    val defaultTemplateId =
        incomeSources.firstOrNull { it.id == "onboarding-default-income" }?.defaultTemplateId
            ?: incomeSources.firstOrNull()?.defaultTemplateId
    val defaultSplitLines =
        remember(defaultTemplateId, splitLines) {
            splitLines
                .filter { it.templateId == defaultTemplateId }
                .map { SplitLine(it.id, it.templateId, it.accountId, SplitMode.valueOf(it.mode), it.value) }
        }
    val defaultRemainder = splitTemplates.firstOrNull { it.id == defaultTemplateId }?.remainderAccountId
    val parserContext =
        remember(accounts, expenses, incomes, learned, defaultSplitLines, defaultRemainder) {
            ParserContext(
                accounts.map { account -> ParserAccount(account.id, setOf(account.name)) },
                (expenses + incomes).map { ParserCategory(it.id, setOf(it.name)) },
                learnedPayees = learned,
                defaultAccountId = null,
                today = LocalDate.now(ZoneId.of("Africa/Cairo")),
                defaultIncomeSplitLines = defaultSplitLines,
                defaultIncomeRemainderAccountId = defaultRemainder,
            )
        }
    val drafts = remember(transcript, parserContext) { VoiceParser.parse(transcript, parserContext) }
    val editedDrafts = remember(drafts) { mutableStateListOf<DraftRecord>().apply { addAll(drafts) } }

    fun transcriptText(vararg parts: String): String = parts.map(String::trim).filter(String::isNotBlank).joinToString(" ")

    fun startSession(nextLanguage: String) {
        language = nextLanguage
        listening = true
        finalTranscript = ""
        partialTranscript = ""
        transcript = ""
        reviewOpen = false
        showVoiceSettings = false
        status = if (nextLanguage == "ar-EG") "Listening in Arabic…" else "Listening in English…"
        startVoice(
            recognizer,
            nextLanguage,
            { segment ->
                finalTranscript = transcriptText(finalTranscript, segment)
                partialTranscript = ""
                transcript = finalTranscript
            },
            { error ->
                status = error
                showVoiceSettings = error == resources.getString(app.mizan.R.string.p14_voice_language_unavailable)
                listening = false
            },
            { partial ->
                partialTranscript = partial
                transcript = transcriptText(finalTranscript, partialTranscript)
            },
            {
                transcript = transcriptText(finalTranscript, partialTranscript)
                listening = false
                reviewOpen = transcript.isNotBlank()
                status = if (reviewOpen) "Transcript ready. Review every record before saving." else "No speech was recognized. Try again."
            },
        )
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startSession(language) else status = "Microphone permission was not granted."
        }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }
    Column(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin, vertical = MzSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Voice entry", style = MzTypography.title)
        VoiceWaveform(listening)
        Text(status, color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
        if (showVoiceSettings) {
            MzButton("Open Quick entry settings", onOpenVoiceSettings, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY)
        }
        if (listening) {
            MzButton(
                "Stop recognition",
                {
                    status = "Finishing transcript…"
                    recognizer.stopListening()
                },
                Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(MzSpacing.gutter)) {
                MzButton("Speak Arabic", {
                    status = "Requesting microphone…"
                    permission.launch(android.Manifest.permission.RECORD_AUDIO)
                })
                MzButton("Speak English", {
                    if (androidx.core.content.ContextCompat
                            .checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        startSession("en-US")
                    } else {
                        language = "en-US"
                        status = "Requesting microphone…"
                        permission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }, style = MzButtonStyle.SECONDARY)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutlinedTextField(
                transcript,
                { transcript = it },
                label = { Text("Transcript · النص") },
                modifier = Modifier.fillMaxWidth(),
                textStyle =
                    androidx.compose.ui.text
                        .TextStyle(textDirection = TextDirection.ContentOrRtl),
            )
        }
        if (drafts.isNotEmpty()) MzButton("Review ${drafts.size} records", { reviewOpen = true }, Modifier.fillMaxWidth())
        MzButton("Discard", onDone, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
    }
    if (reviewOpen) {
        VoiceReviewSheet(
            drafts = editedDrafts,
            accounts = accounts.associate { it.id to it.name },
            categories =
                mapOf(
                    app.mizan.domain.entry.DraftKind.EXPENSE to expenses.associate { it.id to it.name },
                    app.mizan.domain.entry.DraftKind.INCOME to incomes.associate { it.id to it.name },
                ),
            hideAmounts = hideAmounts,
            saving = saving,
            onEdit = { index, replacement -> editedDrafts[index] = replacement },
            onDismiss = { reviewOpen = false },
            onSaveAll = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            val bookingRates =
                                editedDrafts.map { it.currency to it.date }.distinct().associateWith { (currency, date) ->
                                    requireNotNull(
                                        repository.effectiveRate(currency, date),
                                    ) { resources.getString(app.mizan.R.string.p15_voice_missing_rate, currency, date.toString()) }
                                }
                            val knownPayees = repository.observePayees().first()
                            val payeeIds =
                                editedDrafts
                                    .mapNotNull { it.payee?.trim()?.takeIf(String::isNotBlank) }
                                    .distinctBy { it.lowercase() }
                                    .associate { name ->
                                        val existing = knownPayees.firstOrNull { it.name.equals(name, true) }
                                        val id =
                                            existing?.id
                                                ?: java.util.UUID
                                                    .nameUUIDFromBytes("voice-payee|${name.lowercase()}".toByteArray())
                                                    .toString()
                                        if (existing == null) repository.savePayee(PayeeEntity(id, name))
                                        name.lowercase() to id
                                    }
                            val saved =
                                VoiceDraftCommitter(repository::postAll).saveAll(
                                    editedDrafts.toList(),
                                    accounts.map { it.id }.toSet(),
                                    emptyMap(),
                                    payeeIds,
                                    bookingRates,
                                )
                            editedDrafts.forEach { draft ->
                                draft.payee?.let { payee ->
                                    draft.categoryId?.let { category -> repository.learnPayeeCategory(payee, category) }
                                }
                            }
                            status = "Saved $saved reviewed records."
                            reviewOpen = false
                            onDone()
                        } catch (error: IllegalArgumentException) {
                            status = error.message ?: "Choose an account for each draft before saving."
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        )
    }
}

private fun startVoice(
    recognizer: OnDeviceVoiceRecognizer,
    language: String,
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit,
    onPartialTranscript: (String) -> Unit = {},
    onStopped: () -> Unit,
) {
    recognizer.start(language, onTranscript, onError, onPartialTranscript, continuous = true, onStopped = onStopped)
}

private fun voiceCurrencyDecimals(currency: String) =
    when (currency) {
        "KWD" -> 3
        "JPY" -> 0
        else -> 2
    }

@Composable
private fun VoiceWaveform(listening: Boolean) {
    val tokens = LocalMzTokens.current
    val transition = rememberInfiniteTransition(label = "voice-waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "voice-waveform-phase",
    )
    Canvas(Modifier.fillMaxWidth().height(76.dp)) {
        val count = 31
        val spacing = size.width / count
        repeat(count) { index ->
            val wave = if (listening) kotlin.math.abs(kotlin.math.sin(index * .52 + phase * 2)) else .08
            val height = size.height * (.12f + wave.toFloat() * .8f)
            drawLine(
                color = if (listening) tokens.champagne else tokens.textDim.copy(alpha = .42f),
                start =
                    androidx.compose.ui.geometry
                        .Offset(index * spacing + spacing / 2, (size.height - height) / 2),
                end =
                    androidx.compose.ui.geometry
                        .Offset(index * spacing + spacing / 2, (size.height + height) / 2),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun VoiceReviewSheet(
    drafts: List<DraftRecord>,
    accounts: Map<String, String>,
    categories: Map<app.mizan.domain.entry.DraftKind, Map<String, String>>,
    hideAmounts: Boolean,
    saving: Boolean,
    onEdit: (Int, DraftRecord) -> Unit,
    onDismiss: () -> Unit,
    onSaveAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalMzTokens.current.surface) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = MzSpacing.sideMargin),
            verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        ) {
            item { Text("Review ${drafts.size} records", style = MzTypography.title) }
            items(drafts.indices.toList(), key = { drafts[it].sourceClause + it }) { index ->
                var editing by remember(index, drafts[index]) {
                    mutableStateOf(
                        drafts[index].accountId == null || drafts[index].categoryId == null ||
                            drafts[index].paymentType == null,
                    )
                }
                var amount by remember(drafts[index].amountMinor) {
                    mutableStateOf(
                        BigDecimal
                            .valueOf(
                                drafts[index].amountMinor,
                            ).movePointLeft(voiceCurrencyDecimals(drafts[index].currency))
                            .toPlainString(),
                    )
                }
                var payee by remember(drafts[index].payee) { mutableStateOf(drafts[index].payee.orEmpty()) }
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MzSpacing.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${drafts[index].kind.name.lowercase().replaceFirstChar(Char::uppercase)} · ${drafts[index].currency}")
                        drafts[index].payee?.let { Text(it, style = MzTypography.body) }
                        if (editing) {
                            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") })
                            OutlinedTextField(payee, { payee = it }, label = { Text("Payee") })
                            MzButton("Done editing", {
                                val minor =
                                    runCatching {
                                        BigDecimal(amount).movePointRight(voiceCurrencyDecimals(drafts[index].currency)).longValueExact()
                                    }.getOrNull()
                                if (minor != null && minor > 0) {
                                    val current = drafts[index]
                                    val allocations =
                                        if (current.incomeSplitLines.isNotEmpty()) {
                                            IncomeSplitCalculator
                                                .allocate(
                                                    minor,
                                                    current.incomeSplitLines,
                                                    current.incomeSplitRemainderAccountId,
                                                ).allocations
                                        } else {
                                            current.incomeAllocations
                                        }
                                    onEdit(
                                        index,
                                        current.copy(
                                            amountMinor = minor,
                                            payee = payee.takeIf(String::isNotBlank),
                                            incomeAllocations = allocations,
                                        ),
                                    )
                                }
                                editing = false
                            })
                        } else {
                            AmountText(drafts[index].amountMinor, drafts[index].currency, hideAmounts = hideAmounts)
                        }
                        Text(
                            "${drafts[index].date} · " +
                                if (drafts[index].incomeAllocations.size > 1) {
                                    drafts[index].incomeAllocations.joinToString(" + ") { accounts[it.accountId].orEmpty() }
                                } else {
                                    accounts[drafts[index].accountId].orEmpty().ifBlank { "Choose account" }
                                } + " · " +
                                categories[drafts[index].kind]?.get(drafts[index].categoryId).orEmpty().ifBlank { "Choose category" },
                            color = LocalMzTokens.current.textDim,
                        )
                        if (drafts[index].incomeSplitLines.isNotEmpty()) {
                            Text(
                                "Saved default split · ${drafts[index].incomeAllocations.size} destinations",
                                color = LocalMzTokens.current.textDim,
                                style = MzTypography.bodySmall,
                            )
                        }
                        if (editing) {
                            Text("Account", style = MzTypography.labelCaps)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                accounts.forEach { (id, name) ->
                                    MzChip(name, drafts[index].accountId == id, {
                                        onEdit(
                                            index,
                                            drafts[index].copy(
                                                accountId = id,
                                                incomeAllocations = emptyList(),
                                                incomeSplitLines = emptyList(),
                                            ),
                                        )
                                    })
                                }
                            }
                            Text("Category", style = MzTypography.labelCaps)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories[drafts[index].kind].orEmpty().forEach { (id, name) ->
                                    MzChip(name, drafts[index].categoryId == id, {
                                        onEdit(index, drafts[index].copy(categoryId = id))
                                    })
                                }
                            }
                            if (drafts[index].kind in
                                setOf(app.mizan.domain.entry.DraftKind.EXPENSE, app.mizan.domain.entry.DraftKind.INCOME)
                            ) {
                                Text(
                                    if (drafts[index].kind == app.mizan.domain.entry.DraftKind.INCOME) "Received into" else "Paid with",
                                    style = MzTypography.labelCaps,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        app.mizan.domain.ledger.PaymentType.CASH to "Cash",
                                        app.mizan.domain.ledger.PaymentType.DEBIT to "Debit card",
                                        app.mizan.domain.ledger.PaymentType.CREDIT to "Credit card",
                                    ).filter {
                                        drafts[index].kind == app.mizan.domain.entry.DraftKind.EXPENSE ||
                                            it.first != app.mizan.domain.ledger.PaymentType.CREDIT
                                    }.map { (type, label) ->
                                        type to
                                            if (drafts[index].kind == app.mizan.domain.entry.DraftKind.INCOME &&
                                                type == app.mizan.domain.ledger.PaymentType.DEBIT
                                            ) {
                                                "Bank"
                                            } else {
                                                label
                                            }
                                    }.forEach { (type, label) ->
                                        MzChip(label, drafts[index].paymentType == type, {
                                            onEdit(index, drafts[index].copy(paymentType = type))
                                        })
                                    }
                                }
                            }
                        }
                        Text(
                            "Confidence ${"●".repeat(
                                (drafts[index].confidence / 20).coerceIn(1, 5),
                            )}${"○".repeat(5 - (drafts[index].confidence / 20).coerceIn(1, 5))}",
                            color = LocalMzTokens.current.accentText,
                        )
                        MzButton("Edit", { editing = true }, style = MzButtonStyle.GHOST)
                    }
                }
            }
            val ready =
                drafts.all { draft ->
                    (
                        if (draft.kind == app.mizan.domain.entry.DraftKind.INCOME && draft.incomeAllocations.isNotEmpty()) {
                            draft.incomeAllocations.all { it.accountId in accounts }
                        } else {
                            draft.accountId?.let(accounts::containsKey) == true
                        }
                    ) &&
                        draft.categoryId?.let(categories[draft.kind].orEmpty()::containsKey) == true &&
                        draft.paymentType != null
                }
            if (!ready) {
                item {
                    Text(
                        "Choose destination, category, and cash, bank, or card source for each voice draft before saving.",
                        color = LocalMzTokens.current.warning,
                    )
                }
            }
            item { MzButton(if (saving) "Saving…" else "Save all", onSaveAll, Modifier.fillMaxWidth(), enabled = !saving && ready) }
            item { MzButton("Discard", onDismiss, Modifier.fillMaxWidth(), MzButtonStyle.GHOST) }
        }
    }
}
