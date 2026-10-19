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
) {
    val context = LocalContext.current
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val expenses by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val incomes by repository.observeCategories("INCOME").collectAsStateWithLifecycle(emptyList())
    val learned by repository.observeLearnedPayeeCategories().collectAsStateWithLifecycle(emptyMap())
    val recognizer = remember { OnDeviceVoiceRecognizer(context) }
    val scope = rememberCoroutineScope()
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Speak a record, then review every draft before saving.") }
    var listening by remember { mutableStateOf(false) }
    var reviewOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("ar-EG") }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                listening = true
                startVoice(recognizer, language, {
                    transcript = it
                    listening = false
                    reviewOpen = true
                }, {
                    status = it
                    listening = false
                })
            } else {
                status = "Microphone permission was not granted."
            }
        }
    val parserContext =
        remember(accounts, expenses, incomes, learned) {
            ParserContext(
                accounts.map { ParserAccount(it.id, setOf(it.name)) },
                (expenses + incomes).map { ParserCategory(it.id, setOf(it.name)) },
                learnedPayees = learned,
                defaultAccountId = accounts.firstOrNull()?.id,
                today = LocalDate.now(ZoneId.of("Africa/Cairo")),
            )
        }
    val drafts = remember(transcript, parserContext) { VoiceParser.parse(transcript, parserContext) }
    val editedDrafts = remember(drafts) { mutableStateListOf<DraftRecord>().apply { addAll(drafts) } }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }
    Column(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin, vertical = MzSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Voice entry", style = MzTypography.title)
        VoiceWaveform(listening)
        Text(status, color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MzSpacing.gutter)) {
            MzButton("Speak Arabic", {
                language = "ar-EG"
                permission.launch(android.Manifest.permission.RECORD_AUDIO)
            })
            MzButton("Speak English", {
                if (androidx.core.content.ContextCompat
                        .checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    listening = true
                    language = "en-US"
                    startVoice(recognizer, language, {
                        transcript = it
                        listening = false
                        reviewOpen = true
                    }, {
                        status = it
                        listening =
                            false
                    })
                } else {
                    language = "en-US"
                    permission.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }, style = MzButtonStyle.SECONDARY)
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
            categories = (expenses + incomes).associate { it.id to it.name },
            hideAmounts = hideAmounts,
            saving = saving,
            onEdit = { index, replacement -> editedDrafts[index] = replacement },
            onDismiss = { reviewOpen = false },
            onSaveAll = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            val rates =
                                editedDrafts.map { it.currency }.distinct().associateWith { currency ->
                                    repository.observeMarketRate(currency).first()?.egpPerUnit ?: BigDecimal.ONE
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
                                    rates,
                                    payeeIds,
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
) {
    recognizer.start(language, onTranscript, onError)
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
    categories: Map<String, String>,
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
                var editing by remember(index) { mutableStateOf(false) }
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
                                    onEdit(index, drafts[index].copy(amountMinor = minor, payee = payee.takeIf(String::isNotBlank)))
                                }
                                editing = false
                            })
                        } else {
                            AmountText(drafts[index].amountMinor, drafts[index].currency, hideAmounts = hideAmounts)
                        }
                        Text(
                            "${drafts[index].date} · ${accounts[drafts[index].accountId].orEmpty()} · ${categories[drafts[index].categoryId].orEmpty()}",
                            color = LocalMzTokens.current.textDim,
                        )
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
            item { MzButton(if (saving) "Saving…" else "Save all", onSaveAll, Modifier.fillMaxWidth(), enabled = !saving) }
            item { MzButton("Discard", onDismiss, Modifier.fillMaxWidth(), MzButtonStyle.GHOST) }
        }
    }
}
