// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun V2OnboardingFlow(repository: MizanRepository) {
    var stage by rememberSaveable { mutableStateOf("WELCOME") }
    var draft by rememberSaveable(stateSaver = V2OnboardingDraft.StateSaver) { mutableStateOf(V2OnboardingDraft()) }
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val categories by repository.observeSetupCategories().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val accountError = stringResource(R.string.setup_error_accounts)
    val saveError = stringResource(R.string.setup_save_error)
    val splitError = stringResource(R.string.setup_error_split)
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.seedReferenceData() }

    fun finish(
        value: V2OnboardingDraft,
        lock: Boolean = false,
        passphrase: CharArray? = null,
    ) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                repository.finishOnboarding(value.plan(currencies.associate { it.code to it.decimals }), lock, passphrase)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = saveError
            } finally {
                passphrase?.fill('\u0000')
                saving = false
            }
        }
    }
    BackHandler(stage != "WELCOME") {
        if (!saving) {
            stage =
                when (stage) {
                    "ACCOUNTS", "RESTORE" -> "WELCOME"
                    "SPLIT" -> "ACCOUNTS"
                    "CATEGORIES" -> "SPLIT"
                    else -> "CATEGORIES"
                }
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
                V2BackupScreen(repository)
            }
        }

        "SPLIT" -> {
            V2SplitSetupScreen(
                draft,
                { draft = it },
                onContinue = {
                    error = null
                    stage = "CATEGORIES"
                },
                onSkip = {
                    draft = draft.copy(percentages = V2OnboardingDraft.defaultPercentages(draft.accounts), singleAccountId = null)
                    error = null
                    stage = "CATEGORIES"
                },
                onBack = {
                    error = null
                    stage = "ACCOUNTS"
                },
                error = error?.let { splitError },
            )
        }

        "CATEGORIES" -> {
            V2CategoriesSetupScreen(
                categories,
                draft.selectedCategoryIds,
                { draft = draft.copy(selectedCategoryIds = it) },
                onContinue = {
                    error = null
                    stage = "SECURE"
                },
                onSkip = {
                    draft = draft.copy(selectedCategoryIds = null)
                    error = null
                    stage = "SECURE"
                },
                onBack = { stage = "SPLIT" },
            )
        }

        "SECURE" -> {
            V2SecureSetupScreen(
                app.mizan.security.DeviceSecurity
                    .available(context),
                saving,
                error,
                onFinish = { lock, passphrase -> finish(draft, lock, passphrase) },
                onSkip = { finish(draft) },
                onBack = { if (!saving) stage = "CATEGORIES" },
            )
        }

        else -> {
            V2AccountsSetupScreen(
                draft.accounts,
                currencies,
                { updated ->
                    draft = draft.copy(accounts = updated, percentages = updated.associate { it.id to (draft.percentages[it.id] ?: "0") })
                },
                onContinue = {
                    runCatching {
                        draft
                            .copy(percentages = V2OnboardingDraft.defaultPercentages(draft.accounts), singleAccountId = null)
                            .plan(currencies.associate { it.code to it.decimals })
                    }.onSuccess {
                        error = null
                        stage = "SPLIT"
                    }.onFailure { error = accountError }
                },
                onSkip = {
                    draft = V2OnboardingDraft()
                    error = null
                    stage = "SPLIT"
                },
                onBack = { stage = "WELCOME" },
                error = error,
            )
        }
    }
}
