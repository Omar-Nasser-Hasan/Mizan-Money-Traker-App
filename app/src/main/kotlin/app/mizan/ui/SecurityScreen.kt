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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch

@Composable
fun securityScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val hideAmounts by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    val secureScreens by repository.observeSecureScreens().collectAsStateWithLifecycle(false)
    val lockTimeout by repository.observeLockTimeoutSeconds().collectAsStateWithLifecycle(30)
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Security")
        Text("Hide amounts in Mizan")
        Switch(hideAmounts, onCheckedChange = { scope.launch { repository.setHideAmounts(it) } })
        Text("Block screenshots and hide Mizan in Recents")
        Switch(secureScreens, onCheckedChange = { scope.launch { repository.setSecureScreens(it) } })
        Text("App lock uses your device fingerprint, face, or PIN.")
        Text("Lock after leaving Mizan")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 30, 60, 300).forEach { seconds ->
                val label =
                    if (seconds == 0) {
                        "Immediately"
                    } else if (seconds == 300) {
                        "5 min"
                    } else {
                        "$seconds sec"
                    }
                FilterChip(lockTimeout == seconds, { scope.launch { repository.setLockTimeoutSeconds(seconds) } }, label = { Text(label) })
            }
        }
    }
}
