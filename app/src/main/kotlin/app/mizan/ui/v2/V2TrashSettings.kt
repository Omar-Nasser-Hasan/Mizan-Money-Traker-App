// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.mizan.R
import app.mizan.data.db.TrashRow
import app.mizan.data.repository.MizanRepository
import app.mizan.worker.PurgeRecordWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun V2TrashScreen(repository: MizanRepository) {
    val all by repository.observeTrash().collectAsStateWithLifecycle(null)
    val records = all?.filter { it.deletedAt >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30) }
    val scope = rememberCoroutineScope()
    val manager = WorkManager.getInstance(LocalContext.current)
    var error by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    V2TrashContent(records, error, undo, onRestore = { id ->
        scope.launch {
            error =
                runCatching {
                    manager.cancelUniqueWork("purge-record-$id")
                    repository.restoreRecord(id)
                }.isFailure
            if (!error) {
                undo = {
                    scope.launch { repository.deleteRecord(id) }
                    undo = null
                }
            }
        }
    }, onDelete = { pending = it })
    pending?.let { id ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.p13_delete_forever)) },
            text = { Text(stringResource(R.string.p13_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    val name = "purge-record-$id"
                    manager.enqueueUniqueWork(
                        name,
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<PurgeRecordWorker>()
                            .setInputData(workDataOf("record" to id))
                            .setInitialDelay(10, TimeUnit.SECONDS)
                            .build(),
                    )
                    val cancel = {
                        manager.cancelUniqueWork(name)
                        undo = null
                    }
                    undo = cancel
                    scope.launch {
                        delay(10_000)
                        if (undo === cancel) undo = null
                    }
                    pending = null
                }) { Text(stringResource(R.string.p13_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.p13_cancel)) } },
        )
    }
}

@Composable
fun V2TrashContent(
    records: List<TrashRow>?,
    error: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    V2SettingsList {
        item { Text(stringResource(R.string.p13_trash_note), color = LocalMzTokens.current.text) }
        if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
        if (onUndo != null) item { MzButton(stringResource(R.string.p13_undo), onUndo) }
        if (records == null) {
            items(3) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else if (records.isEmpty()) {
            item { Text(stringResource(R.string.p13_trash_empty), color = LocalMzTokens.current.textDim) }
        } else {
            items(records, key = TrashRow::id) { row ->
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.bookedDate, style = MzTypography.title)
                        Text(row.kind)
                        if (row.note.isNotBlank()) Text(row.note)
                        MzButton(stringResource(R.string.p13_restore), { onRestore(row.id) }, style = MzButtonStyle.SECONDARY)
                        MzButton(stringResource(R.string.p13_delete_forever), { onDelete(row.id) }, style = MzButtonStyle.GHOST)
                    }
                }
            }
        }
    }
}
