// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mizan.R
import app.mizan.data.db.CurrencyEntity
import java.util.UUID

@Composable
fun V2SetupFrame(
    step: Int,
    title: String,
    description: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    continueLabel: String = stringResource(R.string.setup_continue),
    enabled: Boolean = true,
    skipEnabled: Boolean = true,
    error: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        containerColor = LocalMzTokens.current.canvas,
        topBar = {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { MzIcon("arrow_back", stringResource(R.string.setup_back)) }
                    Image(
                        painterResource(R.drawable.ic_mizan_mark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(28.dp),
                    )
                    Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp), style = MzTypography.title)
                    repeat(4) { index ->
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .padding(3.dp)
                                .size(if (index + 1 == step) 12.dp else 8.dp)
                                .background(
                                    if (index <
                                        step
                                    ) {
                                        LocalMzTokens.current.champagne
                                    } else {
                                        LocalMzTokens.current.raisedSurface
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
                Text(title, style = MzTypography.headline)
                Text(description, style = MzTypography.body, color = LocalMzTokens.current.textDim)
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                error?.let { Text(it, color = LocalMzTokens.current.expense, style = MzTypography.bodySmall) }
                MzButton(continueLabel, onContinue, Modifier.fillMaxWidth(), enabled = enabled)
                TextButton(onClick = onSkip, enabled = skipEnabled) { Text(stringResource(R.string.setup_skip)) }
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2AccountsSetupScreen(
    accounts: List<SetupAccountDraft>,
    currencies: List<CurrencyEntity>,
    onChange: (List<SetupAccountDraft>) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    error: String? = null,
) {
    var currencyAccountId by remember { mutableStateOf<String?>(null) }
    V2SetupFrame(
        1,
        stringResource(R.string.setup_accounts_title),
        stringResource(R.string.setup_accounts_description),
        onBack,
        onContinue,
        onSkip,
        enabled = currencies.isNotEmpty(),
        error = error,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (currencies.isEmpty()) {
                items(3) { MzSkeleton(Modifier.fillMaxWidth(), 160.dp) }
            } else {
                items(accounts, key = SetupAccountDraft::id) { account ->
                    MzCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MzIcon(account.icon, account.name, tint = LocalMzTokens.current.champagne)
                                OutlinedTextField(
                                    account.name,
                                    { name -> onChange(accounts.map { if (it.id == account.id) it.copy(name = name) else it }) },
                                    label = { Text(stringResource(R.string.setup_name)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                            }
                            account.balances.forEach { (currency, balance) ->
                                OutlinedTextField(
                                    balance,
                                    { value ->
                                        onChange(
                                            accounts.map {
                                                if (it.id ==
                                                    account.id
                                                ) {
                                                    it.copy(balances = it.balances + (currency to value))
                                                } else {
                                                    it
                                                }
                                            },
                                        )
                                    },
                                    label = { Text(stringResource(R.string.setup_balance, currency)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                            TextButton(onClick = { currencyAccountId = account.id }) { Text(stringResource(R.string.setup_currency)) }
                        }
                    }
                }
                item {
                    val label = stringResource(R.string.setup_add_account)
                    MzButton(label, {
                        onChange(
                            accounts + SetupAccountDraft(UUID.randomUUID().toString(), label, "CASH", "#059093", "account_balance_wallet"),
                        )
                    }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY)
                }
            }
        }
    }
    val account = accounts.firstOrNull { it.id == currencyAccountId }
    if (account != null) {
        ModalBottomSheet(onDismissRequest = { currencyAccountId = null }) {
            LazyColumn(contentPadding = PaddingValues(20.dp)) {
                items(currencies.filter { it.enabled && it.code !in account.balances }, key = CurrencyEntity::code) { currency ->
                    TextButton(onClick = {
                        onChange(accounts.map { if (it.id == account.id) it.copy(balances = it.balances + (currency.code to "0")) else it })
                        currencyAccountId = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("${currency.code} · ${currency.name}") }
                }
            }
        }
    }
}
