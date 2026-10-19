// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.app.NotificationManager
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingText
import app.mizan.data.settings.SettingsSnapshot
import app.mizan.notify.NotificationChannels
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

data class SettingsWorkPlan(
    val flags: Map<SettingFlag, Boolean>,
    val dailyTime: String,
) {
    val planning: Boolean get() =
        listOf(SettingFlag.PLANNED, SettingFlag.BUDGET, SettingFlag.DEBT, SettingFlag.CARD).any {
            flags[it] ==
                true
        }

    companion object {
        fun from(state: SettingsSnapshot) =
            SettingsWorkPlan(
                SettingFlag.entries
                    .filter {
                        it !in
                            setOf(
                                SettingFlag.LOCK,
                                SettingFlag.SECURE,
                                SettingFlag.QUIET,
                                SettingFlag.SMS,
                                SettingFlag.NOTIFICATION_INBOX,
                                SettingFlag.ONLINE_VOICE,
                            )
                    }.associateWith { state[it] },
                state[SettingText.DAILY_TIME],
            )
    }
}

object SettingsEffects {
    fun weeklyDelayMillis(now: ZonedDateTime): Long {
        val sunday =
            now
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        return Duration.between(now, if (sunday.isAfter(now)) sunday else sunday.plusWeeks(1)).toMillis()
    }

    fun apply(
        context: Context,
        plan: SettingsWorkPlan,
    ) {
        val work = WorkManager.getInstance(context)
        if (plan.flags[SettingFlag.DAILY] == true) {
            DailyReminderScheduler.schedule(context, LocalTime.parse(plan.dailyTime))
        } else {
            DailyReminderScheduler.cancel(context)
        }
        if (plan.planning) {
            work.enqueueUniquePeriodicWork(
                "daily-reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).addTag("planning").build(),
            )
        } else {
            work.cancelUniqueWork("daily-reminders")
            work.cancelUniqueWork("planning-refresh")
            work.cancelAllWorkByTag("planning")
        }
        if (plan.flags[SettingFlag.WEEKLY] == true) {
            work.enqueueUniquePeriodicWork(
                "weekly-summary",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(weeklyDelayMillis(ZonedDateTime.now(ZoneId.of("Africa/Cairo"))), TimeUnit.MILLISECONDS)
                    .build(),
            )
        } else {
            work.cancelUniqueWork("weekly-summary")
        }
        if (plan.flags[SettingFlag.BACKUP] == true) {
            work.enqueueUniquePeriodicWork(
                "nightly-encrypted-backup",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                    .build(),
            )
        } else {
            work.cancelUniqueWork("nightly-encrypted-backup")
        }
        if (plan.flags[SettingFlag.GOLD_AUTO] == true) {
            work.enqueueUniquePeriodicWork(
                "gold-price-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PriceRefreshWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            work.cancelUniqueWork("gold-price-refresh")
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val disabled =
            mapOf(
                SettingFlag.DAILY to NotificationChannels.DAILY_LOG,
                SettingFlag.BUDGET to NotificationChannels.BUDGETS,
                SettingFlag.PLANNED to NotificationChannels.REMINDERS,
                SettingFlag.DEBT to NotificationChannels.DEBTS,
                SettingFlag.CARD to NotificationChannels.CREDIT_CARDS,
                SettingFlag.WEEKLY to NotificationChannels.WEEKLY,
            ).filterKeys { plan.flags[it] != true }
                .values
        manager.activeNotifications.filter { it.notification.channelId in disabled }.forEach { manager.cancel(it.id) }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launchWidgetUpdate(context)
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchWidgetUpdate(context: Context) =
    launch {
        app.mizan.quick
            .QuickEntryWidget()
            .updateAll(context)
    }
