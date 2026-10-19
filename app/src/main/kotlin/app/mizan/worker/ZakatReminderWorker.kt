// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mizan.R
import app.mizan.notify.NotificationChannels
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.concurrent.TimeUnit

object ZakatReminderSchedule {
    fun next(
        month: Int,
        day: Int,
        now: ZonedDateTime,
    ): ZonedDateTime {
        require(month in 1..12 && day in 1..30)
        val current = HijrahDate.from(now.toLocalDate()).get(ChronoField.YEAR)
        for (year in current..current + 2) {
            val first = HijrahDate.of(year, month, 1)
            val date =
                LocalDate
                    .from(first.with(ChronoField.DAY_OF_MONTH, day.coerceAtMost(first.lengthOfMonth()).toLong()))
                    .atTime(9, 0)
                    .atZone(now.zone)
            if (date > now) return date
        }
        error("No Hijri anniversary available")
    }

    fun schedule(
        context: Context,
        month: Int,
        day: Int,
    ) {
        val now = ZonedDateTime.now(ZoneId.of("Africa/Cairo"))
        WorkManager.getInstance(context).enqueueUniqueWork(
            "zakat-anniversary",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ZakatReminderWorker>()
                .setInitialDelay(Duration.between(now, next(month, day, now)).toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}

class ZakatReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()
        val anniversary = repository.zakatAnniversary() ?: return Result.success()
        if (repository.notificationsMutedNow()) return Result.retry()
        if ((
                Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) &&
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        ) {
            NotificationManagerCompat.from(applicationContext).notify(
                7413,
                NotificationCompat
                    .Builder(applicationContext, NotificationChannels.REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(applicationContext.getString(R.string.p13_zakat))
                    .setContentText(applicationContext.getString(R.string.p13_zakat_reminder_body))
                    .setAutoCancel(true)
                    .build(),
            )
        }
        ZakatReminderSchedule.schedule(applicationContext, anniversary.first, anniversary.second)
        return Result.success()
    }
}
