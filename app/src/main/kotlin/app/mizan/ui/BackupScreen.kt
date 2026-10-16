// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch

@Composable
fun backupScreen(repository: MizanRepository, modifier: Modifier) {
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Backups are encrypted. Store your passphrase safely; Mizan cannot recover it.") }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backup and restore")
        OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Backup passphrase") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (passphrase.isNotEmpty()) scope.launch { status = "Creating encrypted backup…"; status = "Saved ${repository.createBackup(passphrase.toCharArray()).name}" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Create encrypted backup") }
        Text(status)
    }
}
