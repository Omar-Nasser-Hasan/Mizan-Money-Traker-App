// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mizan.data.settings.SettingFlag
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class BackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        runCatching {
            val repository = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository()
            if (repository.settings.snapshots.first()[SettingFlag.BACKUP]) repository.createScheduledBackup()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
