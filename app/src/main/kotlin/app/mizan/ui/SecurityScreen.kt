// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.security.DeviceSecurity
import kotlinx.coroutines.launch

@Composable
fun securityScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val hideAmounts by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    val secureScreens by repository.observeSecureScreens().collectAsStateWithLifecycle(false)
    val lockTimeout by repository.observeLockTimeoutSeconds().collectAsStateWithLifecycle(30)
    val lockEnabled by repository.observeAppLockEnabled().collectAsStateWithLifecycle(false)
    val canLock = DeviceSecurity.available(LocalContext.current)
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.lock_settings))
        Text(stringResource(R.string.lock_hide_amounts))
        Switch(hideAmounts, onCheckedChange = { scope.launch { repository.setHideAmounts(it) } })
        Text(stringResource(R.string.lock_screen_secure))
        Switch(secureScreens, onCheckedChange = { scope.launch { repository.setSecureScreens(it) } })
        Text(stringResource(R.string.setup_lock_toggle))
        Switch(lockEnabled, onCheckedChange = { scope.launch { repository.setAppLockEnabled(it) } }, enabled = canLock || lockEnabled)
        Text(stringResource(if (canLock) R.string.setup_lock_description else R.string.setup_lock_unavailable))
        Text(stringResource(R.string.lock_timeout_setting))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 30, 60, 300).forEach { seconds ->
                val label =
                    if (seconds == 0) {
                        stringResource(R.string.lock_timeout_immediate)
                    } else if (seconds == 300) {
                        stringResource(R.string.lock_timeout_five_minutes)
                    } else {
                        stringResource(R.string.lock_timeout_option, seconds)
                    }
                FilterChip(lockTimeout == seconds, { scope.launch { repository.setLockTimeoutSeconds(seconds) } }, label = { Text(label) })
            }
        }
    }
}
