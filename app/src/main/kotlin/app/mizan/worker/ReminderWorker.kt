// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.Manifest
import android.annotation.SuppressLint
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
import java.time.LocalTime
import java.time.ZoneId

class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    @SuppressLint("MissingPermission") // canNotify() runs before any notification is posted.
    override suspend fun doWork(): Result {
        val cairo = ZoneId.of("Africa/Cairo")
        val today = LocalDate.now(cairo)
        if (!canNotify() || repository().notificationsMutedNow(LocalTime.now(cairo))) return Result.success()
        if (inputData.getBoolean(KEY_DAILY_ONLY, false)) {
            notifyDailyLogIfNeeded(today)
            return Result.success()
        }
        val debtId = inputData.getString(KEY_DEBT_ID)
        if (debtId != null) {
            if (!repository().debtRemindersEnabled()) return Result.success()
            val debt = repository().debt(debtId) ?: return Result.success()
            if (debt.status != "ACTIVE" || !repository().claimNotification("debt-manual-${debt.id}-$today")) return Result.success()
            notifySimple(
                NotificationChannels.DEBTS,
                "Debt: ${debt.person}",
                debt.dueDate?.let {
                    "Due $it"
                } ?: "Reminder to follow up",
                "debt-manual-${debt.id}-$today",
            )
            return Result.success()
        }
        val snoozedId = inputData.getString(KEY_PLANNED_ID)
        if (snoozedId != null) {
            if (!repository().plannedRemindersEnabled()) return Result.success()
            val payment = repository().activePlannedPayment(snoozedId) ?: return Result.success()
            val dueDate = inputData.getString(KEY_DUE_DATE) ?: return Result.success()
            notifyPlanned(payment.id, payment.name, dueDate)
            return Result.success()
        }
        if (repository().plannedRemindersEnabled()) {
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
        }
        if (repository().budgetAlertsEnabled()) {
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
                budget.alertPcts.split(',').mapNotNull(String::toIntOrNull).distinct().forEach { threshold ->
                    if (spent * 100L < budget.limitMinor * threshold) return@forEach
                    if (!repository().recordBudgetAlert(budget.id, period.start.toString(), threshold)) return@forEach
                    NotificationManagerCompat.from(applicationContext).notify(
                        (budget.id + period.start + threshold).hashCode(),
                        NotificationCompat
                            .Builder(applicationContext, NotificationChannels.BUDGETS)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle("${budget.name} is at $threshold%")
                            .setContentText(
                                if (repository().hideAmountsEnabled()) {
                                    "Amounts are hidden. Open Mizan to review this budget."
                                } else {
                                    "Spent $spent EGP minor of ${budget.limitMinor} EGP minor"
                                },
                            ).setAutoCancel(true)
                            .build(),
                    )
                }
            }
        }
        notifyDailyLogIfNeeded(today)
        if (repository().debtRemindersEnabled()) {
            repository().activeDebts().forEach { debt ->
                val due = debt.dueDate?.let(LocalDate::parse) ?: return@forEach
                if (today >= due && repository().claimNotification("debt-${debt.id}-$today")) {
                    val overdue = if (today > due) "Overdue since $due" else "Due today"
                    notifySimple(NotificationChannels.DEBTS, "Debt: ${debt.person}", overdue, "debt-${debt.id}-$today")
                }
            }
        }
        if (repository().creditCardRemindersEnabled()) {
            repository().activeAccounts().filter { it.type == "CREDIT_CARD" }.forEach { card ->
                val dueDay = card.dueDay?.coerceAtMost(today.lengthOfMonth())
                val statementDay = card.statementDay?.coerceAtMost(today.lengthOfMonth())
                if (dueDay == today.dayOfMonth && repository().claimNotification("card-due-${card.id}-$today")) {
                    notifySimple(
                        NotificationChannels.CREDIT_CARDS,
                        "${card.name} payment due",
                        "Credit-card payment is due today.",
                        "card-due-${card.id}-$today",
                    )
                }
                if (statementDay == today.dayOfMonth && repository().claimNotification("card-statement-${card.id}-$today")) {
                    notifySimple(
                        NotificationChannels.CREDIT_CARDS,
                        "${card.name} statement day",
                        "Review this month's statement.",
                        "card-statement-${card.id}-$today",
                    )
                }
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

    @SuppressLint("MissingPermission") // canNotify() guards every entry to doWork.
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

    @SuppressLint("MissingPermission") // canNotify() guards every entry to doWork.
    private fun notifySimple(
        channel: String,
        title: String,
        text: String,
        identity: String,
    ) {
        NotificationManagerCompat.from(applicationContext).notify(
            identity.hashCode(),
            NotificationCompat
                .Builder(applicationContext, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
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

    private suspend fun notifyDailyLogIfNeeded(today: LocalDate) {
        if (
            repository().dailyExpenseReminderEnabled() &&
            repository().activeTransactionCount(today) == 0 &&
            repository().claimNotification("daily-log-$today")
        ) {
            notifySimple(NotificationChannels.DAILY_LOG, "Log today's expenses", "No records have been added today.", "daily-log-$today")
        }
    }

    companion object {
        const val KEY_PLANNED_ID = "planned_id"
        const val KEY_DUE_DATE = "due_date"
        const val KEY_DEBT_ID = "debt_id"
        const val KEY_DAILY_ONLY = "daily_only"

        fun input(
            plannedId: String,
            dueDate: String,
        ) = workDataOf(KEY_PLANNED_ID to plannedId, KEY_DUE_DATE to dueDate)

        fun debtInput(debtId: String) = workDataOf(KEY_DEBT_ID to debtId)
    }
}
