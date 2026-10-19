// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

class PurgeRecordWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("record") ?: return Result.failure()
        EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).repository().purgeDeletedRecord(id)
        return Result.success()
    }
}
