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
fun V2ExportScreen(repository: MizanRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    V2ExportContent(busy, error) { format ->
        scope.launch {
            busy = true
            error =
                runCatching {
                    val file =
                        when (format) {
                            "CSV" -> repository.exportCsv()
                            "XLSX" -> repository.exportXlsx()
                            else -> repository.exportPdf()
                        }
                    shareP13File(
                        context,
                        file,
                        when (format) {
                            "CSV" -> "text/csv"
                            "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            else -> "application/pdf"
                        },
                    )
                }.isFailure
            busy = false
        }
    }
}

@Composable
fun V2ExportContent(
    busy: Boolean,
    error: Boolean,
    onExport: (String) -> Unit,
) {
    V2SettingsList {
        item { Text(stringResource(R.string.p13_export_note), color = LocalMzTokens.current.text) }
        if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
        listOf("CSV", "XLSX", "PDF").forEach { format ->
            item(key = format) {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(format, style = MzTypography.title)
                        MzButton(stringResource(R.string.p13_export_format, format), { onExport(format) }, enabled = !busy)
                    }
                }
            }
        }
        if (busy) item { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
    }
}
