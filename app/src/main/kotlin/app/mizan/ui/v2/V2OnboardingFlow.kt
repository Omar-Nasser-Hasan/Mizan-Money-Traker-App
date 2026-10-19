// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.ui.backupScreen
import app.mizan.ui.onboardingScreen

@Composable
fun V2OnboardingFlow(repository: MizanRepository) {
    var stage by rememberSaveable { mutableStateOf("WELCOME") }
    when (stage) {
        "WELCOME" -> {
            V2WelcomeScreen(onStart = { stage = "ACCOUNTS" }, onRestore = { stage = "RESTORE" })
        }

        "RESTORE" -> {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TextButton(onClick = { stage = "WELCOME" }) { Text(stringResource(R.string.setup_back)) }
                backupScreen(repository, Modifier.weight(1f))
            }
        }

        else -> {
            onboardingScreen(repository, Modifier.fillMaxSize().safeDrawingPadding(), onDone = {})
        }
    }
}
