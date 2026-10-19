// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.provider.OpenableColumns
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
import app.mizan.data.io.MizanImportXlsxReader
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.io.MizanImportPreview
import app.mizan.domain.io.WalletCsvImporter
import app.mizan.domain.io.WalletImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun V2WalletImportScreen(repository: MizanRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by remember(repository) { repository.observeHideAmounts() }.collectAsStateWithLifecycle(false)
    var csv by remember { mutableStateOf<String?>(null) }
    var headers by remember { mutableStateOf(emptyList<String>()) }
    var mapping by remember { mutableStateOf(emptyMap<String, String>()) }
    var preview by remember { mutableStateOf<WalletImportPreview?>(null) }
    var mizanPreview by remember { mutableStateOf<MizanImportPreview?>(null) }
    var mizanManifest by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<Int?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    csv = null
                    headers = emptyList()
                    mapping = emptyMap()
                    preview = null
                    mizanPreview = null
                    mizanManifest = false
                    error = null
                    imported = null
                    busy = true
                    val result =
                        runCatching {
                            val (bytes, mimeType, displayName) =
                                withContext(Dispatchers.IO) {
                                    val fileBytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                                    val type = context.contentResolver.getType(uri).orEmpty()
                                    val name =
                                        context.contentResolver
                                            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                            ?.use { cursor ->
                                                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                                            }.orEmpty()
                                    Triple(fileBytes, type, name)
                                }
                            val isWorkbook =
                                mimeType.contains("spreadsheetml") ||
                                    displayName.endsWith(".xlsx", true) ||
                                    (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
                            val text =
                                withContext(Dispatchers.Default) {
                                    if (isWorkbook) MizanImportXlsxReader.toCsv(bytes) else bytes.toString(Charsets.UTF_8)
                                }
                            val isMizan = isWorkbook || text.lineSequence().firstOrNull()?.contains("event_kind", ignoreCase = true) == true
                            if (isMizan) {
                                mizanPreview = withContext(Dispatchers.IO) { repository.mizanImport.preview(text) }
                                preview = null
                                headers = emptyList()
                                mizanManifest = true
                            } else {
                                headers = withContext(Dispatchers.Default) { WalletCsvImporter.headers(text) }
                                preview = null
                                mizanPreview = null
                                mizanManifest = false
                            }
                            csv = text
                            mapping = emptyMap()
                            imported = null
                        }
                    error = result.exceptionOrNull()?.message
                    busy = false
                }
            }
        }

    V2SettingsList {
        item { Text(stringResource(R.string.p13_import_note), color = LocalMzTokens.current.text) }
        item {
            MzButton(
                stringResource(R.string.p13_choose_import_file),
                {
                    picker.launch(
                        arrayOf(
                            "text/csv",
                            "text/*",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        ),
                    )
                },
                enabled = !busy,
            )
        }
        error?.let { message -> item { Text(message, color = LocalMzTokens.current.expense) } }
        if (csv != null) {
            if (!mizanManifest) {
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
            }
            item {
                MzButton(
                    stringResource(R.string.p13_dry_run),
                    {
                        scope.launch {
                            busy = true
                            error =
                                runCatching {
                                    if (mizanManifest) {
                                        mizanPreview = repository.mizanImport.preview(requireNotNull(csv))
                                    } else {
                                        preview = repository.walletImport.preview(requireNotNull(csv), mapping)
                                    }
                                }.exceptionOrNull()?.message
                            busy = false
                        }
                    },
                    enabled = !busy,
                )
            }
        }
        mizanPreview?.let { checked ->
            item {
                Text(
                    stringResource(R.string.p13_import_count, checked.events.size),
                    color = LocalMzTokens.current.text,
                )
            }
            item {
                Text(stringResource(R.string.p13_review_count, checked.reviews.size), color = LocalMzTokens.current.text)
            }
            items(checked.reviews, key = { "review-${it.sourceRef}-${it.note.hashCode()}" }) { review ->
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(review.sourceRef.ifBlank { stringResource(R.string.p13_mapping_review) })
                        Text(review.note, color = LocalMzTokens.current.textDim)
                    }
                }
            }
            items(checked.bucketTotalsMinor.entries.toList(), key = { "mizan-${it.key.account}:${it.key.currency}" }) { (bucket, amount) ->
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(bucket.account)
                        AmountText(amount, bucket.currency, hideAmounts = hidden)
                    }
                }
            }
            items(checked.errors, key = { "mizan-error-${it.hashCode()}" }) { message ->
                Text(message, color = LocalMzTokens.current.expense)
            }
            item {
                MzButton(
                    stringResource(R.string.p13_commit_import),
                    {
                        scope.launch {
                            busy = true
                            error =
                                runCatching { imported = repository.mizanImport.commit(requireNotNull(csv)) }
                                    .exceptionOrNull()
                                    ?.message
                            busy = false
                        }
                    },
                    enabled = !busy && checked.errors.isEmpty() && checked.events.isNotEmpty() && imported == null,
                )
            }
        }
        preview?.let { checked ->
            item {
                Text(stringResource(R.string.p13_import_count, checked.records.size), color = LocalMzTokens.current.text)
            }
            items(checked.bucketTotalsMinor.entries.toList(), key = { "wallet-${it.key.account}:${it.key.currency}" }) { (bucket, amount) ->
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(bucket.account)
                        AmountText(amount, bucket.currency, hideAmounts = hidden)
                    }
                }
            }
            items(checked.errors, key = { "wallet-error-${it.hashCode()}" }) { message ->
                Text(message, color = LocalMzTokens.current.expense)
            }
            item {
                MzButton(
                    stringResource(R.string.p13_commit_import),
                    {
                        scope.launch {
                            busy = true
                            error =
                                runCatching { imported = repository.walletImport.commit(requireNotNull(csv), mapping) }
                                    .exceptionOrNull()
                                    ?.message
                            busy = false
                        }
                    },
                    enabled = !busy && checked.errors.isEmpty() && checked.records.isNotEmpty() && imported == null,
                )
            }
        }
        imported?.let { count ->
            item {
                Text(
                    stringResource(R.string.p13_imported, count),
                    color = LocalMzTokens.current.text,
                )
            }
        }
    }
}
