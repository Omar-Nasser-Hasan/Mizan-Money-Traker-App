// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingText
import app.mizan.data.settings.SettingsSnapshot
import kotlinx.coroutines.launch

val P13NotificationFlags =
    listOf(SettingFlag.DAILY, SettingFlag.BUDGET, SettingFlag.PLANNED, SettingFlag.DEBT, SettingFlag.CARD, SettingFlag.WEEKLY)

fun openPhoneSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

@Composable
fun V2NotificationsScreen(repository: MizanRepository) {
    val state by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    var permitted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permitted = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    var pending by remember { mutableStateOf<SettingFlag?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permitted = granted
            if (granted) pending?.let { scope.launch { repository.settings.set(it, true) } }
            pending = null
        }
    V2NotificationsContent(state, permitted, { flag, enabled ->
        if (enabled && flag in P13NotificationFlags && !permitted && Build.VERSION.SDK_INT >= 33) {
            pending = flag
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launch { error = runCatching { repository.settings.set(flag, enabled) }.isFailure }
        }
    }, { daily, from, until ->
        scope.launch { error = runCatching { repository.settings.times(daily, from, until) }.isFailure }
    }, { openPhoneSettings(context) }, error)
}

@Composable
fun V2NotificationsContent(
    state: SettingsSnapshot?,
    deliveryAllowed: Boolean,
    onToggle: (SettingFlag, Boolean) -> Unit,
    onSaveTimes: (String, String, String) -> Unit,
    onPhoneSettings: () -> Unit,
    error: Boolean = false,
) {
    val names = stringArrayResource(R.array.p13_notification_names)
    val descriptions = stringArrayResource(R.array.p13_notification_descriptions)
    val daily = state?.get(SettingText.DAILY_TIME) ?: "20:00"
    val start = state?.get(SettingText.QUIET_START) ?: "22:00"
    val end = state?.get(SettingText.QUIET_END) ?: "07:00"
    var dailyTime by remember(daily) { mutableStateOf(daily) }
    var from by remember(start) { mutableStateOf(start) }
    var until by remember(end) { mutableStateOf(end) }
    V2SettingsList {
        if (state == null) {
            items(5) {
                MzSkeleton(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth(),
                    80.dp,
                )
            }
        } else {
            item {
                MzCard {
                    Text(
                        stringResource(if (deliveryAllowed) R.string.p13_delivery_on else R.string.p13_delivery_off),
                        androidx.compose.ui.Modifier
                            .padding(16.dp),
                    )
                }
            }
            if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
            item {
                MzCard {
                    P13NotificationFlags.forEachIndexed { index, flag ->
                        Column(
                            androidx.compose.ui.Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            MzToggle(state[flag], { onToggle(flag, it) }, names[index])
                            Text(descriptions[index], style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
                        }
                    }
                }
            }
            item {
                MzCard {
                    Column(
                        androidx.compose.ui.Modifier
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MzToggle(state[SettingFlag.QUIET], { onToggle(SettingFlag.QUIET, it) }, stringResource(R.string.p13_quiet))
                        OutlinedTextField(
                            dailyTime,
                            { dailyTime = it },
                            label = { Text(stringResource(R.string.p13_daily_time)) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            from,
                            { from = it },
                            label = { Text(stringResource(R.string.p13_quiet_start)) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            until,
                            { until = it },
                            label = { Text(stringResource(R.string.p13_quiet_end)) },
                            singleLine = true,
                        )
                        MzButton(stringResource(R.string.p13_save), { onSaveTimes(dailyTime, from, until) })
                    }
                }
            }
            item {
                MzCard {
                    Column(
                        androidx.compose.ui.Modifier
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.p13_battery_title), style = MzTypography.title)
                        Text(stringResource(R.string.p13_battery_body), style = MzTypography.body)
                        MzButton(stringResource(R.string.p13_phone_settings), onPhoneSettings, style = MzButtonStyle.SECONDARY)
                    }
                }
            }
        }
    }
}
