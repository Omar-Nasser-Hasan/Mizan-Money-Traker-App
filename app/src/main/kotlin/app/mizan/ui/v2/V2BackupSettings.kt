// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.backup.BackupPreview
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

data class P13BackupFile(
    val file: File,
    val name: String,
    val date: Instant,
    val bytes: Long,
)

@Composable
fun V2BackupScreen(repository: MizanRepository) {
    val context = LocalContext.current
    val state by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<P13BackupFile>?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var driveInfo by remember { mutableStateOf(false) }

    suspend fun reload() {
        files =
            withContext(Dispatchers.IO) {
                repository.localBackups().map {
                    P13BackupFile(it, it.name, Instant.ofEpochMilli(it.lastModified()), it.length())
                }
            }
    }
    LaunchedEffect(Unit) { error = runCatching { reload() }.isFailure }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    error =
                        runCatching {
                            restoreFile =
                                withContext(Dispatchers.IO) {
                                    File.createTempFile("restore-", ".mzbak", context.cacheDir).also { copy ->
                                        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                                            copy.outputStream().use(input::copyTo)
                                        }
                                    }
                                }
                        }.isFailure
                }
            }
        }
    V2SettingsList {
        if (error) item(key = "error") { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
        if (busy || files == null ||
            state == null
        ) {
            item(key = "loading") { MzSkeleton(Modifier.fillMaxWidth().testTag("p13-backup-loading"), 80.dp) }
        }
        item(key = "phone") {
            MzCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.p13_this_phone), style = MzTypography.title)
                    Text(stringResource(R.string.p13_local_retention))
                    Text(files?.firstOrNull()?.date?.toString() ?: stringResource(R.string.p13_no_backups))
                    MzToggle(state?.get(SettingFlag.BACKUP) ?: true, {
                        scope.launch { repository.settings.set(SettingFlag.BACKUP, it) }
                    }, stringResource(R.string.p13_daily_backup))
                    OutlinedTextField(
                        passphrase,
                        { passphrase = it },
                        label = { Text(stringResource(R.string.p13_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        confirmation,
                        { confirmation = it },
                        label = { Text(stringResource(R.string.p13_confirm_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.p13_passphrase_note))
                    MzButton(stringResource(R.string.p13_backup_now), {
                        scope.launch {
                            busy = true
                            error =
                                runCatching {
                                    if (passphrase.isNotEmpty()) {
                                        require(passphrase.length >= 8 && passphrase == confirmation)
                                        val secret = passphrase.toCharArray()
                                        try {
                                            repository.setBackupPassphrase(secret)
                                            repository.createBackup(secret)
                                        } finally {
                                            secret.fill('\u0000')
                                        }
                                        passphrase = ""
                                        confirmation = ""
                                    } else {
                                        requireNotNull(repository.createScheduledBackup())
                                    }
                                    reload()
                                }.isFailure
                            busy = false
                        }
                    }, enabled = !busy)
                }
            }
        }
        item(key = "drive") {
            MzCard {
                V2SettingRow(
                    "cloud",
                    stringResource(R.string.p13_drive),
                    stringResource(R.string.p13_drive_unconfigured),
                ) { driveInfo = true }
            }
        }
        val lastShared = state?.get(SettingText.LAST_SHARED)?.toLongOrNull() ?: 0L
        if (lastShared == 0L || System.currentTimeMillis() - lastShared > TimeUnit.DAYS.toMillis(14)) {
            item(key = "warning") { Text(stringResource(R.string.p13_offdevice_warning), color = LocalMzTokens.current.expense) }
        }
        files?.firstOrNull()?.let { latest ->
            item {
                MzButton(stringResource(R.string.p13_share_latest), {
                    shareP13File(context, latest.file, "application/octet-stream")
                    scope.launch { repository.settings.set(SettingText.LAST_SHARED, System.currentTimeMillis().toString()) }
                }, style = MzButtonStyle.SECONDARY)
            }
        }
        item(key = "choose") { MzButton(stringResource(R.string.p13_choose_backup), { picker.launch(arrayOf("*/*")) }, enabled = !busy) }
        items(files.orEmpty(), key = { it.name }) { saved ->
            MzCard {
                V2SettingRow("restore", saved.name, stringResource(R.string.p13_backup_size, saved.bytes)) {
                    restoreFile = saved.file
                }
            }
        }
    }
    if (driveInfo) {
        AlertDialog(
            onDismissRequest = { driveInfo = false },
            title = { Text(stringResource(R.string.p13_drive)) },
            text = { Text(stringResource(R.string.p13_drive_setup)) },
            confirmButton = { TextButton(onClick = { driveInfo = false }) { Text(stringResource(R.string.p13_close)) } },
        )
    }
    restoreFile?.let { selected ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    restoreFile = null
                    preview = null
                }
            },
            title = { Text(stringResource(R.string.p13_restore)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(selected.name)
                    OutlinedTextField(
                        passphrase,
                        {
                            passphrase = it
                            preview = null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        label = { Text(stringResource(R.string.p13_passphrase)) },
                    )
                    if (preview == null) {
                        Text(stringResource(R.string.p13_restore_preview_note))
                    } else {
                        Text(
                            stringResource(
                                R.string.p13_verified_backup,
                                preview!!.rowCounts["transactions"] ?: 0,
                                preview!!.attachmentCount,
                            ),
                        )
                    }
                    if (error) Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && passphrase.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        val secret = passphrase.toCharArray()
                        error =
                            runCatching {
                                if (preview == null) {
                                    preview = repository.previewBackup(selected, secret)
                                } else {
                                    repository.restoreBackup(selected, secret)
                                    repository.setOnboardingCompleted()
                                    restartApp(context)
                                }
                            }.isFailure
                        secret.fill('\u0000')
                        busy = false
                    }
                }) { Text(stringResource(if (preview == null) R.string.p13_verify else R.string.p13_restore_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    restoreFile = null
                    preview = null
                    passphrase = ""
                }) {
                    Text(stringResource(R.string.p13_cancel))
                }
            },
        )
    }
}
