// Copyright (c) 2026 Mizan contributors
package app.mizan

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mizan.data.repository.MizanRepository
import app.mizan.notify.NotificationChannels
import app.mizan.worker.DailyReminderScheduler
import app.mizan.worker.FxRefreshWorker
import app.mizan.worker.TrashPurgeWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MizanApplication : Application() {
    @Inject lateinit var repository: MizanRepository

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val writer =
                Thread {
                    runCatching {
                        app.mizan.data.backup.LocalCrashLog
                            .write(java.io.File(filesDir, "logs"), error)
                    }
                }
            writer.start()
            runCatching { writer.join(1500) }
            previous?.uncaughtException(thread, error)
        }
        NotificationChannels.create(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.zakatAnniversary()?.let { (month, day) ->
                app.mizan.worker.ZakatReminderSchedule
                    .schedule(this@MizanApplication, month, day)
            }
            repository.seedReferenceData()
            repository.refreshFxIfMissingOrStale()
            repository.refreshGoldPricesIfStale()
            if (repository.dailyExpenseReminderEnabled()) {
                DailyReminderScheduler.schedule(this@MizanApplication, repository.dailyExpenseReminderTime())
            }
        }
        val request =
            PeriodicWorkRequestBuilder<FxRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-fx-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trash-purge",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashPurgeWorker>(24, TimeUnit.HOURS).build(),
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.settings.snapshots.map(app.mizan.worker.SettingsWorkPlan::from).distinctUntilChanged().collect {
                app.mizan.worker.SettingsEffects
                    .apply(this@MizanApplication, it)
            }
        }
    }
}
