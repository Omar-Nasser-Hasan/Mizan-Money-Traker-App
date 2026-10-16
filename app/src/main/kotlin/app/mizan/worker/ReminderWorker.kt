// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import app.mizan.domain.plan.budgetWindow
import app.mizan.notify.NotificationChannels
import app.mizan.notify.ReminderActionReceiver
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        if (!canNotify()) return Result.success()
        val snoozedId = inputData.getString(KEY_PLANNED_ID)
        if (snoozedId != null) {
            val payment = repository().activePlannedPayment(snoozedId) ?: return Result.success()
            val dueDate = inputData.getString(KEY_DUE_DATE) ?: return Result.success()
            notifyPlanned(payment.id, payment.name, dueDate)
            return Result.success()
        }
        repository().activePlannedPayments().forEach { payment ->
            val dueDates =
                RecurrenceEngine.occurrences(
                    Recurrence(
                        Frequency.valueOf(payment.frequency),
                        payment.interval,
                        LocalDate.parse(payment.startDate),
                        payment.endDate?.let(LocalDate::parse),
                    ),
                    today,
                    today.plusDays(payment.remindDaysBefore.toLong()),
                )
            if (dueDates.isEmpty()) return@forEach
            val dueDate = dueDates.first().toString()
            if (repository().plannedOccurrence(payment.id, dueDate) != null) return@forEach
            notifyPlanned(payment.id, payment.name, dueDate)
        }
        repository().activeBudgets().forEach { budget ->
            val period =
                budgetWindow(
                    BudgetPeriod.valueOf(budget.period),
                    today,
                    budget.startDate?.let(LocalDate::parse),
                    budget.endDate?.let(LocalDate::parse),
                )
            if (today !in period.start..period.end) return@forEach
            val spent = repository().budgetSpent(period.start.toString(), today.toString(), budget.scope, budget.scopeIds)
            listOf(80, 100).forEach { threshold ->
                if (spent * 100L < budget.limitMinor * threshold) return@forEach
                if (!repository().recordBudgetAlert(budget.id, period.start.toString(), threshold)) return@forEach
                NotificationManagerCompat.from(applicationContext).notify(
                    (budget.id + period.start + threshold).hashCode(),
                    NotificationCompat
                        .Builder(applicationContext, NotificationChannels.BUDGETS)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("${budget.name} is at $threshold%")
                        .setContentText("Spent $spent EGP minor of ${budget.limitMinor} EGP minor")
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }
        return Result.success()
    }

    private fun canNotify(): Boolean =
        NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            )

    private fun repository(): MizanRepository =
        EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()

    private fun notifyPlanned(
        plannedId: String,
        name: String,
        dueDate: String,
    ) {
        val paid = action(plannedId, dueDate, ReminderActionReceiver.ACTION_PAID)
        val skip = action(plannedId, dueDate, ReminderActionReceiver.ACTION_SKIP)
        val snooze = action(plannedId, dueDate, ReminderActionReceiver.ACTION_SNOOZE)
        NotificationManagerCompat.from(applicationContext).notify(
            plannedId.hashCode(),
            NotificationCompat
                .Builder(applicationContext, NotificationChannels.REMINDERS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(name)
                .setContentText("Due $dueDate")
                .addAction(0, "Mark paid", paid)
                .addAction(0, "Skip", skip)
                .addAction(0, "Snooze 1 hour", snooze)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun action(
        plannedId: String,
        dueDate: String,
        action: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            (plannedId + dueDate + action).hashCode(),
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderActionReceiver.EXTRA_PLANNED_ID, plannedId)
                .putExtra(ReminderActionReceiver.EXTRA_DUE_DATE, dueDate),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val KEY_PLANNED_ID = "planned_id"
        const val KEY_DUE_DATE = "due_date"

        fun input(
            plannedId: String,
            dueDate: String,
        ) = workDataOf(KEY_PLANNED_ID to plannedId, KEY_DUE_DATE to dueDate)
    }
}
