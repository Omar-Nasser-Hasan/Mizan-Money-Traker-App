// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mizan.data.repository.MizanRepository
import dagger.hilt.android.EntryPointAccessors

class PriceRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        repository().refreshGoldPrices().fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.success() },
        )

    private fun repository(): MizanRepository =
        EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()
}
