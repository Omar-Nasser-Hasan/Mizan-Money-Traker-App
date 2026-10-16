// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

class BackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, WorkerEntryPoint::class.java)
                .repository()
                .createScheduledBackup()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
