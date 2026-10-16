// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mizan.data.repository.MizanRepository
import app.mizan.notify.NotificationChannels
import dagger.hilt.android.EntryPointAccessors

class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        repository().activePlannedPayments().forEach { payment ->
            NotificationManagerCompat.from(applicationContext).notify(
                payment.id.hashCode(),
                NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(payment.name)
                    .setContentText("A planned payment needs your attention")
                    .setAutoCancel(true)
                    .build(),
            )
        }
        return Result.success()
    }

    private fun repository(): MizanRepository = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()
}
