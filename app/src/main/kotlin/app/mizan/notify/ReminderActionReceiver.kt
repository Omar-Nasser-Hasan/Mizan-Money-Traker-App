// Copyright (c) 2026 Mizan contributors
package app.mizan.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.mizan.data.repository.MizanRepository
import app.mizan.worker.ReminderWorker
import app.mizan.worker.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val plannedId = intent.getStringExtra(EXTRA_PLANNED_ID).orEmpty()
            val dueDate = intent.getStringExtra(EXTRA_DUE_DATE).orEmpty()
            val status =
                when (intent.action) {
                    ACTION_SKIP -> "SKIPPED"
                    ACTION_SNOOZE -> "SNOOZED"
                    else -> "PAID"
                }
            val repository =
                EntryPointAccessors
                    .fromApplication(context.applicationContext, WorkerEntryPoint::class.java)
                    .repository()
            if (intent.action == ACTION_PAID) {
                repository.markPlannedPaid(plannedId, dueDate)
            } else {
                repository.markPlannedOccurrence(plannedId, dueDate, status)
            }
            if (intent.action == ACTION_SNOOZE) {
                val request =
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(1, TimeUnit.HOURS)
                        .setInputData(ReminderWorker.input(plannedId, dueDate))
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork("snooze-$plannedId-$dueDate", ExistingWorkPolicy.REPLACE, request)
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_PAID = "app.mizan.action.PLANNED_PAID"
        const val ACTION_SKIP = "app.mizan.action.PLANNED_SKIP"
        const val ACTION_SNOOZE = "app.mizan.action.PLANNED_SNOOZE"
        const val EXTRA_PLANNED_ID = "planned_id"
        const val EXTRA_DUE_DATE = "due_date"
    }
}
