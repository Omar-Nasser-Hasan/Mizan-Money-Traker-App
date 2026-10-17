// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.entry.ParserAccount
import app.mizan.domain.entry.ParserCategory
import app.mizan.domain.entry.ParserContext
import app.mizan.domain.entry.VoiceParser
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.voice.OnDeviceVoiceRecognizer
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Review-only voice entry. Posting remains deliberately outside this screen until Save is tapped in Entry. */
@Composable
fun voiceEntryScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val expenses by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val incomes by repository.observeCategories("INCOME").collectAsStateWithLifecycle(emptyList())
    val learnedPayees by repository.observeLearnedPayeeCategories().collectAsStateWithLifecycle(emptyMap())
    val context = LocalContext.current
    val recognizer = remember { OnDeviceVoiceRecognizer(context) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("On-device recognition only; SPIKE-V1 has no offline pass yet.") }
    var confirmSave by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val parserContext =
        ParserContext(
            accounts.map { ParserAccount(it.id, setOf(it.name)) },
            (expenses + incomes).map { category ->
                ParserCategory(
                    category.id,
                    setOf(category.name) + parserAliases(category.name),
                )
            },
            learnedPayees = learnedPayees,
            defaultAccountId = accounts.firstOrNull()?.id,
            today = LocalDate.now(ZoneId.of("Africa/Cairo")),
        )
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) status = "Microphone permission was not granted."
        }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }
    val drafts = remember(transcript, parserContext) { VoiceParser.parse(transcript, parserContext) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Voice review")
        Text(status)
        Button(onClick = { permission.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
        Button(onClick = {
            recognizer.start("ar-EG", { transcript = it; status = "Transcript ready for review" }, { status = it })
        }, modifier = Modifier.fillMaxWidth()) { Text("Speak Arabic (on-device)") }
        Button(onClick = {
            recognizer.start("en-US", { transcript = it; status = "Transcript ready for review" }, { status = it })
        }, modifier = Modifier.fillMaxWidth()) { Text("Speak English (on-device)") }
        OutlinedTextField(transcript, { transcript = it }, label = { Text("Transcript") }, modifier = Modifier.fillMaxWidth())
        Text("Review ${drafts.size} draft(s) — nothing is saved here")
        drafts.forEach { draft ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${draft.kind} · ${draft.amountMinor} ${draft.currency} minor")
                    Text("${draft.date} · account ${draft.accountId ?: "choose in Entry"} · category ${draft.categoryId ?: "choose in Entry"}")
                    Text("Confidence ${draft.confidence}%")
                }
            }
        }
        if (drafts.isNotEmpty()) {
            Button(onClick = { confirmSave = true }, modifier = Modifier.fillMaxWidth()) { Text("Review complete — confirm Save all") }
        }
        Button(onClick = onDone) { Text("Discard review") }
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Save ${drafts.size} reviewed drafts?") },
            text = { Text("This is the only action that posts records. Drafts with an unavailable account or currency are left unsaved.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val service = LedgerService()
                        var saved = 0
                        drafts.forEach { draft ->
                            val account = accounts.firstOrNull { it.id == draft.accountId && it.defaultCurrency == draft.currency } ?: return@forEach
                            val category = draft.categoryId ?: MizanRepository.UNCATEGORIZED_ID
                            val posting =
                                if (draft.kind.name == "INCOME") {
                                    service.postIncome(IncomeRequest(account.id, draft.currency, draft.amountMinor, currencyDecimals(draft.currency), BigDecimal.ONE, Instant.now(), draft.date, category))
                                } else {
                                    service.postExpense(ExpenseRequest(account.id, draft.currency, draft.amountMinor, currencyDecimals(draft.currency), BigDecimal.ONE, category, Instant.now(), draft.date))
                                }
                            repository.post(posting)
                            draft.payee?.let { repository.learnPayeeCategory(it, category) }
                            saved++
                        }
                        status = "Saved $saved reviewed draft(s)."
                        confirmSave = false
                    }
                }) { Text("Save all") }
            },
            dismissButton = { Button(onClick = { confirmSave = false }) { Text("Cancel") } },
        )
    }
}

private fun parserAliases(categoryName: String): Set<String> {
    val name = categoryName.lowercase()
    return when {
        listOf("grocer", "restaurant", "cafe", "drinks", "delivery", "snack").any(name::contains) -> setOf("food")
        listOf("taxi", "transport", "metro", "ride").any(name::contains) -> setOf("transport")
        name.contains("fuel") -> setOf("fuel")
        listOf("electricity", "water", "gas").any(name::contains) -> setOf("utilities")
        listOf("zakat", "charity", "sadaq").any(name::contains) -> setOf("charity")
        else -> emptySet()
    }
}
