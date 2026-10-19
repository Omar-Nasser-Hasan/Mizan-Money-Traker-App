// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mizan.R
import app.mizan.domain.accounts.OnboardingSetup
import java.math.BigDecimal

@Composable
fun V2SplitSetupScreen(
    draft: V2OnboardingDraft,
    onChange: (V2OnboardingDraft) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    error: String? = null,
) {
    val total =
        remember(draft.percentages, draft.singleAccountId) {
            if (draft.singleAccountId != null) {
                10_000L
            } else {
                runCatching {
                    draft.percentages.values.sumOf(OnboardingSetup::percentBasisPoints)
                }.getOrNull()
            }
        }
    V2SetupFrame(
        2,
        stringResource(R.string.setup_split_title),
        stringResource(R.string.setup_split_description),
        onBack,
        onContinue,
        onSkip,
        enabled = total == 10_000L,
        error = error,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = {
                            onChange(draft.copy(percentages = V2OnboardingDraft.defaultPercentages(draft.accounts), singleAccountId = null))
                        }) { Text(stringResource(R.string.setup_split_preset)) }
                        Text(
                            stringResource(
                                R.string.setup_split_total,
                                total?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() } ?: "—",
                            ),
                            style = MzTypography.bodySmall,
                            color = if (total == 10_000L) LocalMzTokens.current.income else LocalMzTokens.current.expense,
                        )
                        draft.accounts.forEach { account ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MzIcon(account.icon, account.name, tint = LocalMzTokens.current.investment)
                                if (draft.singleAccountId != null) {
                                    RadioButton(
                                        selected = draft.singleAccountId == account.id,
                                        onClick = { onChange(draft.copy(singleAccountId = account.id)) },
                                    )
                                    Text(account.name, style = MzTypography.body)
                                } else {
                                    OutlinedTextField(
                                        draft.percentages[account.id].orEmpty(),
                                        { onChange(draft.copy(percentages = draft.percentages + (account.id to it))) },
                                        label = { Text(stringResource(R.string.setup_split_percent, account.name)) },
                                        suffix = { Text("%") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                MzButton(
                    stringResource(if (draft.singleAccountId == null) R.string.setup_split_single else R.string.setup_split_reset),
                    { onChange(draft.copy(singleAccountId = if (draft.singleAccountId == null) draft.accounts.first().id else null)) },
                    Modifier.fillMaxWidth(),
                    MzButtonStyle.GHOST,
                )
            }
        }
    }
}
