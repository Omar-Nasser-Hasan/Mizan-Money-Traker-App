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
import app.mizan.worker.FxRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MizanApplication : Application() {
    @Inject lateinit var repository: MizanRepository

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.seedReferenceData()
            repository.refreshFxIfMissingOrStale()
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
    }
}
