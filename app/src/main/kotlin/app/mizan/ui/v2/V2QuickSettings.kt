// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingFlag
import app.mizan.quick.QuickEntryWidgetReceiver
import kotlinx.coroutines.launch

@Composable
fun V2QuickSettingsScreen(
    repository: MizanRepository,
    onNavigate: (V2Route) -> Unit,
) {
    val state by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    var showSmsRationale by remember { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            scope.launch { repository.settings.set(SettingFlag.SMS, granted) }
        }
    V2SettingsList {
        if (state == null) {
            items(3) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else {
            if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MzToggle(
                            state!![SettingFlag.WIDGET],
                            { scope.launch { repository.settings.set(SettingFlag.WIDGET, it) } },
                            stringResource(R.string.p13_widget),
                        )
                        MzButton(stringResource(R.string.p13_add_widget), {
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                manager.requestPinAppWidget(ComponentName(context, QuickEntryWidgetReceiver::class.java), null, null)
                            } else {
                                error = true
                            }
                        })
                    }
                }
            }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MzToggle(
                            state!![SettingFlag.ONLINE_VOICE],
                            { enabled -> scope.launch { repository.settings.set(SettingFlag.ONLINE_VOICE, enabled) } },
                            stringResource(R.string.p14_voice_online_only),
                        )
                        Text(stringResource(R.string.p14_voice_online_info))
                    }
                }
            }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MzToggle(state!![SettingFlag.SMS], { enabled ->
                            if (enabled) {
                                showSmsRationale = true
                            } else {
                                scope.launch { repository.settings.set(SettingFlag.SMS, false) }
                            }
                        }, stringResource(R.string.p13_sms))
                        MzToggle(state!![SettingFlag.NOTIFICATION_INBOX], { enabled ->
                            scope.launch {
                                repository.settings.set(SettingFlag.NOTIFICATION_INBOX, enabled)
                                if (enabled) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }, stringResource(R.string.p13_bank_notifications))
                        Text(stringResource(R.string.p13_inbox_note))
                        MzButton(
                            stringResource(R.string.p13_access_guide),
                            { onNavigate(V2Route.RestrictedSettingsGuide) },
                            style = MzButtonStyle.SECONDARY,
                        )
                    }
                }
            }
        }
    }
    if (showSmsRationale) {
        AlertDialog(
            onDismissRequest = { showSmsRationale = false },
            title = { Text("Allow bank message drafts?") },
            text = {
                Text(
                    "Mizan reads incoming SMS only after you opt in. Matching messages become drafts in Inbox; nothing posts without your confirmation. Android may require enabling restricted access in phone settings.",
                )
            },
            confirmButton = {
                MzButton("Continue", {
                    showSmsRationale = false
                    permission.launch(Manifest.permission.RECEIVE_SMS)
                })
            },
            dismissButton = { MzButton("Cancel", { showSmsRationale = false }, style = MzButtonStyle.GHOST) },
        )
    }
}
