// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.mizan.MainActivity
import app.mizan.data.backup.BackupPreview
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun backupScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Backups are encrypted. Store your passphrase safely; Mizan cannot recover it.") }
    var newestBackup by remember { mutableStateOf<File?>(null) }
    var importedBackup by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null || passphrase.isBlank()) return@rememberLauncherForActivityResult
            scope.launch {
                status = "Checking encrypted backup…"
                runCatching {
                    val copy =
                        withContext(Dispatchers.IO) {
                            File.createTempFile("restore-", ".mzbak", context.cacheDir).also { destination ->
                                requireNotNull(context.contentResolver.openInputStream(uri)).use { source ->
                                    destination.outputStream().use(source::copyTo)
                                }
                            }
                        }
                    copy to repository.previewBackup(copy, passphrase.toCharArray())
                }.onSuccess { (file, checked) ->
                    importedBackup = file
                    preview = checked
                    status =
                        "Backup verified. It has ${checked.rowCounts["transactions"] ?: 0} records and ${checked.attachmentCount} receipt files from ${checked.createdAt}."
                }.onFailure { status = "Backup was not opened: ${it.message}" }
            }
        }
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Backup and restore")
        OutlinedTextField(
            passphrase,
            { passphrase = it },
            label = { Text("Backup passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (passphrase.isNotEmpty()) {
                    scope.launch {
                        status = "Creating encrypted backup…"
                        runCatching {
                            repository.setBackupPassphrase(passphrase.toCharArray())
                            repository.createBackup(passphrase.toCharArray())
                        }.onSuccess {
                            newestBackup = it
                            status =
                                "Saved ${it.name}. Seven daily and four weekly local copies are retained; future daily copies use the device Keystore-wrapped passphrase."
                        }.onFailure { status = "Backup failed: ${it.message}" }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create encrypted backup") }
        if (newestBackup != null) {
            Button(
                onClick = { shareBackup(context, newestBackup!!) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share newest backup") }
        }
        Button(
            onClick = {
                if (passphrase.isBlank()) {
                    status = "Enter the backup passphrase before choosing a file."
                } else {
                    picker.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Choose backup to preview") }
        if (preview != null && importedBackup != null) {
            Text("Restore replaces the current local data only after this verified preview. Mizan will restart immediately.")
            Button(
                onClick = {
                    scope.launch {
                        status = "Restoring verified backup…"
                        runCatching { repository.restoreBackup(importedBackup!!, passphrase.toCharArray()) }
                            .onSuccess {
                                repository.setOnboardingCompleted()
                                restartApp(context)
                            }.onFailure { status = "Restore failed; current data was left unchanged: ${it.message}" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore verified backup") }
        }
        Text(status)
    }
}

private fun shareBackup(
    context: Context,
    file: File,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
    context.startActivity(
        Intent(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

private fun restartApp(context: Context) {
    val restart =
        PendingIntent.getActivity(
            context,
            93,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    context.getSystemService(AlarmManager::class.java).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, restart)
    Process.killProcess(Process.myPid())
}
