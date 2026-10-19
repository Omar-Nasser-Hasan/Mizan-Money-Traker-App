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
import androidx.work.WorkerParameters
import app.mizan.data.settings.SettingFlag
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatRecord
import app.mizan.domain.stats.StatsEngine
import app.mizan.notify.NotificationChannels
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeeklySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()
        val state = repository.settings.snapshots.first()
        val zone = ZoneId.of("Africa/Cairo")
        val today = LocalDate.now(zone)
        // WorkManager may execute late or across a Cairo clock change; do not discard a scheduled summary by weekday/hour.
        if (!state[SettingFlag.WEEKLY] || repository.notificationsMutedNow()) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() ||
            (
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            )
        ) {
            return Result.success()
        }
        val rows = repository.observeStatsRows().first()
        val flow =
            withContext(Dispatchers.Default) {
                StatsEngine.cashFlow(
                    rows.map {
                        StatRecord(it.id, LocalDate.parse(it.bookedDate), StatKind.fromLedgerKind(it.kind), it.baseMinor, it.accountId)
                    },
                    Period(today.minusDays(7), today.minusDays(1)),
                )
            }
        if (!repository.claimNotification("weekly-summary-$today")) return Result.success()
        val body =
            if (state[SettingFlag.HIDE]) {
                applicationContext.getString(app.mizan.R.string.p13_summary_hidden)
            } else {
                applicationContext.getString(
                    app.mizan.R.string.p13_summary_body,
                    BigDecimal.valueOf(flow.incomeMinor, 2).toPlainString(),
                    BigDecimal.valueOf(flow.expenseMinor, 2).toPlainString(),
                )
            }
        NotificationManagerCompat.from(applicationContext).notify(
            7313,
            NotificationCompat
                .Builder(applicationContext, NotificationChannels.WEEKLY)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(
                    applicationContext.getString(app.mizan.R.string.p13_weekly),
                ).setContentText(body)
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }
}
