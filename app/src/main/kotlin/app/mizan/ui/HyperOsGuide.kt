// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun hyperOsGuide(modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("HyperOS reminders guide")
        Text("For reliable reminders after a reboot, open Mizan's app info and set Battery saver to No restrictions. In HyperOS Security, enable Autostart for Mizan.")
        Text("For a sideloaded app on Android 13+, open App info → ⋮ and allow restricted settings before enabling SMS or notification access.")
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }, modifier = Modifier.fillMaxWidth()) { Text("Open Mizan app settings") }
    }
}
