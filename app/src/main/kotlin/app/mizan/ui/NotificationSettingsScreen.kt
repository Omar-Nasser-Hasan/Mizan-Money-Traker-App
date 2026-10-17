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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import app.mizan.worker.DailyReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun notificationSettingsScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val reminders by repository.observePlannedRemindersEnabled().collectAsStateWithLifecycle(true)
    val budgets by repository.observeBudgetAlertsEnabled().collectAsStateWithLifecycle(true)
    val dailyLog by repository.observeDailyExpenseReminderEnabled().collectAsStateWithLifecycle(false)
    val debtReminders by repository.observeDebtRemindersEnabled().collectAsStateWithLifecycle(true)
    val cardReminders by repository.observeCreditCardRemindersEnabled().collectAsStateWithLifecycle(true)
    val savedDailyTime by repository.observeDailyExpenseReminderTime().collectAsStateWithLifecycle("20:00")
    val savedQuietStart by repository.observeQuietHoursStart().collectAsStateWithLifecycle("22:00")
    val savedQuietEnd by repository.observeQuietHoursEnd().collectAsStateWithLifecycle("07:00")
    val scope = rememberCoroutineScope()
    var dailyTime by remember(savedDailyTime) { mutableStateOf(savedDailyTime) }
    var quietStart by remember(savedQuietStart) { mutableStateOf(savedQuietStart) }
    var quietEnd by remember(savedQuietEnd) { mutableStateOf(savedQuietEnd) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = LocalContext.current
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
        notificationToggle("Daily expense reminder", dailyLog) {
            scope.launch {
                repository.setDailyExpenseReminderEnabled(it)
                if (it) {
                    DailyReminderScheduler.schedule(context, runCatching { LocalTime.parse(dailyTime) }.getOrDefault(LocalTime.of(20, 0)))
                } else {
                    DailyReminderScheduler.cancel(context)
                }
            }
        }
        if (dailyLog) {
            OutlinedTextField(
                dailyTime,
                { dailyTime = it },
                label = { Text("Daily reminder time (HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        val parsed = runCatching { LocalTime.parse(dailyTime) }.getOrNull() ?: return@launch
                        repository.setDailyExpenseReminderTime(parsed.toString())
                        DailyReminderScheduler.schedule(context, parsed)
                    }
                },
            ) { Text("Save daily reminder time") }
        }
        notificationToggle("Debt due reminders", debtReminders) { scope.launch { repository.setDebtRemindersEnabled(it) } }
        notificationToggle("Credit-card statement and due reminders", cardReminders) {
            scope.launch { repository.setCreditCardRemindersEnabled(it) }
        }
        OutlinedTextField(
            quietStart,
            { quietStart = it },
            label = { Text("Quiet hours start (HH:mm)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(quietEnd, { quietEnd = it }, label = { Text("Quiet hours end (HH:mm)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { scope.launch { repository.setQuietHours(quietStart, quietEnd) } }) { Text("Save quiet hours") }
        Text(
            "A threshold is delivered once per budget and period. Quiet hours suppress delivery. HyperOS may require the battery and Autostart guide.",
        )
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
