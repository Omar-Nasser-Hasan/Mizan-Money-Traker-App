// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.io.WalletImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun walletImportScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<WalletImportPreview?>(null) }
    var status by remember { mutableStateOf("Select a Wallet CSV to inspect it. Nothing is imported during this preview.") }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                status = "Reading CSV…"
                runCatching {
                    val file =
                        withContext(Dispatchers.IO) {
                            val input = context.contentResolver.openInputStream(uri) ?: error("The selected file cannot be read")
                            File(context.cacheDir, "wallet-preview.csv").also { file ->
                                input.use { source -> file.outputStream().use(source::copyTo) }
                            }
                        }
                    repository.previewWalletCsv(file)
                }.onSuccess {
                    preview = it
                    status = "Preview complete. No records were saved."
                }.onFailure { status = "CSV preview failed: ${it.message}" }
            }
        }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Import Wallet CSV") }
        item {
            Text(
                "Columns are matched by header name. Investment-category records are labelled as asset purchases in the preview; unknown accounts remain visible before any future import.",
            )
        }
        item {
            Button(onClick = {
                picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Select Wallet CSV") }
        }
        item { Text(status) }
        preview?.let { value ->
            item { Text("${value.records.size} records · ${value.errors.size} rows needing attention") }
            value.accountTotalsMinor.forEach { (account, total) ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(account)
                            Text("Net imported amount: $total minor EGP")
                        }
                    }
                }
            }
            value.errors.forEach { error -> item { Text(error) } }
        }
    }
}
