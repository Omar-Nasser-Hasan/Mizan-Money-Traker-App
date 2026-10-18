// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PlanningReminderScheduler {
    fun refresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(1, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("planning-refresh", ExistingWorkPolicy.REPLACE, request)
    }

    fun snooze(
        context: Context,
        plannedId: String,
        dueDate: String,
    ) {
        val request =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(1, TimeUnit.HOURS)
                .setInputData(ReminderWorker.input(plannedId, dueDate))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("snooze-$plannedId-$dueDate", ExistingWorkPolicy.REPLACE, request)
    }
}
