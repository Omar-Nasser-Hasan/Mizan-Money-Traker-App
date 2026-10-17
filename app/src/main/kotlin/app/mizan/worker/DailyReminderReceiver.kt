// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.mizan.data.repository.MizanRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_DAILY_REMINDER && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = EntryPointAccessors.fromApplication(context.applicationContext, WorkerEntryPoint::class.java).repository()
                if (repository.dailyExpenseReminderEnabled()) {
                    val time = repository.dailyExpenseReminderTime()
                    DailyReminderScheduler.schedule(context.applicationContext, time)
                    if (intent.action == ACTION_DAILY_REMINDER) {
                        WorkManager.getInstance(context).enqueue(
                            OneTimeWorkRequestBuilder<ReminderWorker>()
                                .setInputData(workDataOf(ReminderWorker.KEY_DAILY_ONLY to true))
                                .build(),
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_REMINDER = "app.mizan.action.DAILY_REMINDER"
    }
}
