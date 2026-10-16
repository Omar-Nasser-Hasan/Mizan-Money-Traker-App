// Copyright (c) 2026 Mizan contributors
package app.mizan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

class TrashPurgeWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .repository()
            .purgeTrash()
        return Result.success()
    }
}
