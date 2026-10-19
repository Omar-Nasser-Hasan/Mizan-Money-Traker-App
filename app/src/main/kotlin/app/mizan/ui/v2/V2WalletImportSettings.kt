// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.io.WalletCsvImporter
import app.mizan.domain.io.WalletImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun V2WalletImportScreen(repository: MizanRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    var csv by remember { mutableStateOf<String?>(null) }
    var headers by remember { mutableStateOf(emptyList<String>()) }
    var mapping by remember { mutableStateOf(emptyMap<String, String>()) }
    var preview by remember { mutableStateOf<WalletImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<Int?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    busy = true
                    error =
                        runCatching {
                            val text =
                                withContext(Dispatchers.IO) {
                                    requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use {
                                        it.readText()
                                    }
                                }
                            headers = withContext(Dispatchers.Default) { WalletCsvImporter.headers(text) }
                            csv = text
                            mapping = emptyMap()
                            preview = null
                            imported = null
                        }.isFailure
                    busy = false
                }
            }
        }
    V2SettingsList {
        item { Text(stringResource(R.string.p13_wallet_note), color = LocalMzTokens.current.text) }
        item {
            MzButton(
                stringResource(R.string.p13_choose_csv),
                {
                    picker.launch(
                        arrayOf(
                            "text/*",
                            "application/octet-stream",
                        ),
                    )
                },
                enabled = !busy,
            )
        }
        if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
        if (csv != null) {
            item {
                MzCard {
                    Column(Modifier.padding(16.dp)) {
                        val names = stringResource(R.string.p13_wallet_fields).split("|")
                        WalletCsvImporter.fields.forEachIndexed { index, field ->
                            P13Choice(
                                names[index],
                                mapping[field] ?: headers.firstOrNull { it.equals(field, true) },
                                headers.map { it to it },
                            ) { value ->
                                mapping = mapping + (field to value)
                                preview = null
                                imported = null
                            }
                        }
                    }
                }
            }
            item {
                MzButton(stringResource(R.string.p13_dry_run), {
                    scope.launch {
                        busy = true
                        error = runCatching { preview = repository.walletImport.preview(requireNotNull(csv), mapping) }.isFailure
                        busy = false
                    }
                }, enabled = !busy)
            }
        }
        preview?.let { checked ->
            item {
                Text(
                    stringResource(
                        R.string.p13_import_count,
                        checked.records.size,
                    ),
                    color = LocalMzTokens.current.text,
                )
            }
            items(
                checked.bucketTotalsMinor.entries.toList(),
                key = { "${it.key.account}:${it.key.currency}" },
            ) { (
                bucket,
                amount,
            ),
                ->
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(bucket.account)
                        AmountText(amount, bucket.currency, hideAmounts = hidden)
                    }
                }
            }
            items(checked.errors) { message -> Text(message, color = LocalMzTokens.current.expense) }
            item {
                MzButton(stringResource(R.string.p13_commit_import), {
                    scope.launch {
                        busy = true
                        error = runCatching { imported = repository.walletImport.commit(requireNotNull(csv), mapping) }.isFailure
                        busy = false
                    }
                }, enabled = !busy && checked.errors.isEmpty() && checked.records.isNotEmpty() && imported == null)
            }
        }
        imported?.let { count ->
            item {
                Text(
                    stringResource(
                        R.string.p13_imported,
                        count,
                    ),
                    color = LocalMzTokens.current.text,
                )
            }
        }
    }
}
