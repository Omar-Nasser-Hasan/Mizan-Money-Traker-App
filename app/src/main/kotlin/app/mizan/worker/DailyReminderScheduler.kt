// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules the user-selected daily time, with a WorkManager fallback where exact alarms are unavailable. */
object DailyReminderScheduler {
    private const val WORK_NAME = "daily-expense-reminder-fallback"

    fun schedule(
        context: Context,
        time: LocalTime,
    ) {
        val target = nextTrigger(time)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.toEpochMilli(), pendingIntent(context))
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } else {
            val delay = Duration.between(java.time.Instant.now(), target).coerceAtLeast(Duration.ZERO)
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ReminderWorker.KEY_DAILY_ONLY to true))
                    .build(),
            )
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun nextTrigger(time: LocalTime): java.time.Instant {
        val zone = ZoneId.of("Africa/Cairo")
        val now = ZonedDateTime.now(zone)
        val today = ZonedDateTime.of(LocalDate.now(zone), time, zone)
        return (if (today.isAfter(now)) today else today.plusDays(1)).toInstant()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            2041,
            Intent(context, DailyReminderReceiver::class.java).setAction(DailyReminderReceiver.ACTION_DAILY_REMINDER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
