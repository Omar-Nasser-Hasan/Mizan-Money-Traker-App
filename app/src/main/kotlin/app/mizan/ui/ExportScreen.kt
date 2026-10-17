// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch

@Composable
fun exportScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("CSV includes every active record and entry. Amounts use minor units for exact import.") }
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Export records")
        Text("The UTF-8 CSV includes a byte-order mark so it opens correctly in Excel.")
        Button(
            onClick = {
                scope.launch {
                    status = "Building CSV…"
                    runCatching { repository.exportCsv() }
                        .onSuccess { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
                            context.startActivity(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/csv")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                            status = "Ready to share ${file.name}."
                        }.onFailure { status = "CSV export failed: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create and share CSV") }
        Button(
            onClick = {
                scope.launch {
                    status = "Building XLSX…"
                    runCatching { repository.exportXlsx() }
                        .onSuccess { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
                            context.startActivity(
                                Intent(Intent.ACTION_SEND)
                                    .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                            status = "Ready to share ${file.name}."
                        }.onFailure { status = "XLSX export failed: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create and share XLSX") }
        Button(
            onClick = {
                scope.launch {
                    status = "Building PDF…"
                    runCatching { repository.exportPdf() }
                        .onSuccess { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
                            context.startActivity(
                                Intent(Intent.ACTION_SEND)
                                    .setType("application/pdf")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                            status = "Ready to share ${file.name}."
                        }.onFailure { status = "PDF export failed: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create and share PDF") }
        Text(status)
    }
}
