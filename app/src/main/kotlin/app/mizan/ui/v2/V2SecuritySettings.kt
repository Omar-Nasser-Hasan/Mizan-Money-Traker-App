// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.mizan.data.settings.SettingsSnapshot
import app.mizan.security.DeviceSecurity
import kotlinx.coroutines.launch

@Composable
fun V2SecuritySettingsScreen(repository: MizanRepository) {
    val state by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val timeout by repository.observeLockTimeoutSeconds().collectAsStateWithLifecycle(30)
    val available = DeviceSecurity.available(LocalContext.current)
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    V2SecuritySettingsContent(state, timeout, available, { flag, enabled ->
        scope.launch {
            error =
                runCatching {
                    if (flag == SettingFlag.LOCK) {
                        repository.setAppLockEnabled(enabled)
                    } else {
                        repository.settings.set(flag, enabled)
                    }
                }.isFailure
        }
    }, { seconds -> scope.launch { repository.setLockTimeoutSeconds(seconds) } }, error)
}

@Composable
fun V2SecuritySettingsContent(
    state: SettingsSnapshot?,
    timeout: Int,
    available: Boolean,
    onToggle: (SettingFlag, Boolean) -> Unit,
    onTimeout: (Int) -> Unit,
    error: Boolean = false,
) {
    V2SettingsList {
        if (state == null) {
            items(4) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else {
            if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MzToggle(
                            state[SettingFlag.LOCK],
                            { onToggle(SettingFlag.LOCK, it) },
                            stringResource(R.string.setup_lock_toggle),
                            enabled = available || state[SettingFlag.LOCK],
                        )
                        Text(stringResource(if (available) R.string.setup_lock_description else R.string.setup_lock_unavailable))
                        P13Choice(
                            stringResource(R.string.lock_timeout_setting),
                            timeout.toString(),
                            listOf(0, 30, 60, 300).map { seconds ->
                                seconds.toString() to
                                    if (seconds == 0) {
                                        stringResource(R.string.lock_timeout_immediate)
                                    } else if (seconds == 300) {
                                        stringResource(R.string.lock_timeout_five_minutes)
                                    } else {
                                        stringResource(R.string.lock_timeout_option, seconds)
                                    }
                            },
                        ) { onTimeout(it.toInt()) }
                    }
                }
            }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MzToggle(state[SettingFlag.HIDE], { onToggle(SettingFlag.HIDE, it) }, stringResource(R.string.lock_hide_amounts))
                        MzToggle(
                            state[SettingFlag.SECURE],
                            { onToggle(SettingFlag.SECURE, it) },
                            stringResource(R.string.lock_screen_secure),
                        )
                    }
                }
            }
        }
    }
}
