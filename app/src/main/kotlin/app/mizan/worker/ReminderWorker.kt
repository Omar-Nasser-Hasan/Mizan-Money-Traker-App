// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mizan.data.repository.MizanRepository
import app.mizan.notify.NotificationChannels
import app.mizan.notify.ReminderActionReceiver
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        repository().activePlannedPayments().forEach { payment ->
            val dueDates = RecurrenceEngine.occurrences(
                Recurrence(Frequency.valueOf(payment.frequency), payment.interval, LocalDate.parse(payment.startDate), payment.endDate?.let(LocalDate::parse)),
                today,
                today.plusDays(payment.remindDaysBefore.toLong()),
            )
            if (dueDates.isEmpty()) return@forEach
            val dueDate = dueDates.first().toString()
            val paid = action(payment.id, dueDate, ReminderActionReceiver.ACTION_PAID)
            val skip = action(payment.id, dueDate, ReminderActionReceiver.ACTION_SKIP)
            NotificationManagerCompat.from(applicationContext).notify(
                payment.id.hashCode(),
                NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(payment.name)
                    .setContentText("Due $dueDate")
                    .addAction(0, "Mark paid", paid)
                    .addAction(0, "Skip", skip)
                    .setAutoCancel(true)
                    .build(),
            )
        }
        return Result.success()
    }

    private fun repository(): MizanRepository = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()

    private fun action(plannedId: String, dueDate: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            (plannedId + dueDate + action).hashCode(),
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderActionReceiver.EXTRA_PLANNED_ID, plannedId)
                .putExtra(ReminderActionReceiver.EXTRA_DUE_DATE, dueDate),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
