// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.ui.backupScreen
import kotlinx.coroutines.launch

@Composable
fun V2OnboardingFlow(repository: MizanRepository) {
    var stage by rememberSaveable { mutableStateOf("WELCOME") }
    var draft by rememberSaveable(stateSaver = V2OnboardingDraft.StateSaver) { mutableStateOf(V2OnboardingDraft()) }
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val accountError = stringResource(R.string.setup_error_accounts)
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.seedReferenceData() }

    fun finish(value: V2OnboardingDraft) {
        if (saving) return
        saving = true
        scope.launch {
            runCatching { repository.finishOnboarding(value.plan(currencies.associate { it.code to it.decimals })) }
                .onFailure { error = accountError }
            saving = false
        }
    }
    when (stage) {
        "WELCOME" -> {
            V2WelcomeScreen(onStart = {
                scope.launch {
                    repository.beginOnboarding()
                    stage = "ACCOUNTS"
                }
            }, onRestore = { stage = "RESTORE" })
        }

        "RESTORE" -> {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TextButton(onClick = { stage = "WELCOME" }) { Text(stringResource(R.string.setup_back)) }
                backupScreen(repository, Modifier.weight(1f))
            }
        }

        else -> {
            V2AccountsSetupScreen(
                draft.accounts,
                currencies,
                { draft = draft.copy(accounts = it, percentages = V2OnboardingDraft.defaultPercentages(it)) },
                onContinue = { finish(draft) },
                onSkip = {
                    draft = V2OnboardingDraft()
                    finish(draft)
                },
                onBack = { stage = "WELCOME" },
                error = error,
            )
        }
    }
}
