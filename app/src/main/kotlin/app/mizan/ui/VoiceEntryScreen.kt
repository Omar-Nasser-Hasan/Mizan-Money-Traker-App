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
import app.mizan.voice.OnDeviceVoiceRecognizer
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
    val context = LocalContext.current
    val recognizer = remember { OnDeviceVoiceRecognizer(context) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("On-device recognition only; SPIKE-V1 has no offline pass yet.") }
    val parserContext =
        ParserContext(
            accounts.map { ParserAccount(it.id, setOf(it.name)) },
            (expenses + incomes).map { ParserCategory(it.id, setOf(it.name)) },
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
        Button(onClick = onDone) { Text("Discard review") }
    }
}
