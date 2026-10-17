// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch

@Composable
fun inboxScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onOpenGuide: () -> Unit,
) {
    val drafts by repository.observeInboxDrafts().collectAsStateWithLifecycle(emptyList())
    val smsEnabled by repository.observeSmsInboxEnabled().collectAsStateWithLifecycle(false)
    val notificationsEnabled by repository.observeNotificationInboxEnabled().collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val smsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scope.launch { repository.setSmsInboxEnabled(true) }
        }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Inbox") }
        item { Text("Bank messages become drafts only. Confirming a draft never happens automatically.") }
        item {
            Column {
                Text("Read incoming SMS")
                Switch(smsEnabled, { enabled ->
                    if (enabled) {
                        scope.launch {
                            repository.setSmsInboxEnabled(false)
                        }
                    } else {
                        smsPermission.launch(android.Manifest.permission.RECEIVE_SMS)
                    }
                })
            }
        }
        item {
            Column {
                Text("Read bank notifications")
                Switch(notificationsEnabled, { enabled ->
                    scope.launch { repository.setNotificationInboxEnabled(enabled) }
                    if (enabled) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            }
        }
        item { Button(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) { Text("Restricted settings guide") } }
        if (drafts.isEmpty()) item { Text("No pending drafts") }
        items(drafts, key = { it.id }) { draft ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${draft.kind.lowercase().replaceFirstChar(Char::uppercase)} · ${draft.amountMinor} ${draft.currency} minor")
                    Text(draft.merchant ?: "No merchant")
                    Text("${draft.source} · ${draft.cardLastFour?.let { "card ••••$it" } ?: "no card hint"}")
                    draft.reference?.let { Text("Reference $it") }
                    when {
                        draft.kind == "TRANSFER" && draft.merchant == "ATM" -> Text("Draft destination: Cash account")

                        draft.kind == "TRANSFER" &&
                            draft.merchant.equals(
                                "Thndr Securities Brokerage",
                                true,
                            )
                        -> Text("Draft destination: Investment account")

                        draft.kind == "REVERSAL" -> Text("Refund draft — confirm only after review")
                    }
                    Button(onClick = { scope.launch { repository.confirmInboxDraft(draft.id) } }) { Text("Confirm draft") }
                    Button(onClick = { scope.launch { repository.rejectInboxDraft(draft.id) } }) { Text("Reject") }
                }
            }
        }
    }
}
