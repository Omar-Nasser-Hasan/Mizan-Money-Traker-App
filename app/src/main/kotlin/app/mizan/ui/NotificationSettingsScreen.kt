// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
fun notificationSettingsScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val reminders by repository.observePlannedRemindersEnabled().collectAsStateWithLifecycle(true)
    val budgets by repository.observeBudgetAlertsEnabled().collectAsStateWithLifecycle(true)
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Notifications")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Allow notifications")
            }
        }
        notificationToggle("Planned payment reminders", reminders) { scope.launch { repository.setPlannedRemindersEnabled(it) } }
        notificationToggle("Budget alerts at 80% and 100%", budgets) { scope.launch { repository.setBudgetAlertsEnabled(it) } }
        Text("A threshold is delivered once per budget and period. HyperOS may require the battery and Autostart guide.")
    }
}

@Composable
private fun notificationToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked, onCheckedChange)
    }
}
