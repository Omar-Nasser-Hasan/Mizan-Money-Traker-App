// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingsSnapshot
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun V2CurrenciesScreen(repository: MizanRepository) {
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(null)
    val rates by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    val settings by repository.settings.snapshots.collectAsStateWithLifecycle(SettingsSnapshot())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val genericError = stringResource(R.string.p13_error)
    val usedError = stringResource(R.string.p13_currency_used)
    V2CurrenciesContent(currencies, rates, settings, onEnable = { code, enabled ->
        scope.launch {
            error = runCatching { if (repository.setCurrencyEnabled(code, enabled)) null else usedError }.getOrElse { genericError }
        }
    }, onSave = { code, pin, spread ->
        scope.launch {
            error =
                runCatching {
                    val rate = pin.takeIf(String::isNotBlank)?.let(::BigDecimal)
                    require(rate == null || rate > BigDecimal.ZERO)
                    val percentage = BigDecimal(spread)
                    require(percentage >= BigDecimal.ZERO && percentage <= BigDecimal("100"))
                    repository.settings.spread(code, spread)
                    repository.setManualPin(code, rate)
                }.exceptionOrNull()?.let { genericError }
        }
    }, onRefresh = { scope.launch { error = repository.refreshFx().exceptionOrNull()?.let { genericError } } }, error = error)
}

@Composable
fun V2CurrenciesContent(
    currencies: List<CurrencyEntity>?,
    rates: List<FxRateEntity>,
    preferences: SettingsSnapshot,
    onEnable: (String, Boolean) -> Unit,
    onSave: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    error: String? = null,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<CurrencyEntity?>(null) }
    var pin by remember { mutableStateOf("") }
    var spread by remember { mutableStateOf("0") }
    val rateByCurrency = remember(rates) { rates.associateBy { it.currency } }
    V2SettingsList {
        item {
            MzCard {
                MzListRow(
                    "lock",
                    stringResource(R.string.p13_base_locked),
                    stringResource(R.string.p13_base_locked),
                    stringResource(R.string.p13_egp_base),
                )
            }
        }
        item { MzButton(stringResource(R.string.p13_refresh), onRefresh, style = MzButtonStyle.SECONDARY) }
        if (error != null) item { Text(error, color = LocalMzTokens.current.expense) }
        if (currencies == null) {
            items(4) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else {
            items(currencies.filter { it.code != "EGP" }, key = CurrencyEntity::code) { currency ->
                val rate = rateByCurrency[currency.code]
                MzCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(currency.code, style = MzTypography.title)
                            Text(currency.name, style = MzTypography.bodySmall)
                            if (rate != null) {
                                Text(stringResource(R.string.p13_fx_rate, currency.code, rate.egpPerUnit), style = MzTypography.bodySmall)
                                Text(
                                    stringResource(
                                        if (currency.code in
                                            preferences.pins
                                        ) {
                                            R.string.p13_pinned
                                        } else {
                                            R.string.p13_midmarket
                                        },
                                    ),
                                    style = MzTypography.bodySmall,
                                )
                                val ageHours = ((System.currentTimeMillis() - rate.fetchedAt).coerceAtLeast(0) / 3_600_000)
                                Text(
                                    stringResource(if (ageHours >= 48) R.string.p13_rate_stale else R.string.p13_rate_age, ageHours),
                                    color = if (ageHours >= 48) LocalMzTokens.current.warning else LocalMzTokens.current.textDim,
                                    style = MzTypography.bodySmall,
                                )
                            } else {
                                Text(stringResource(R.string.p13_no_rate), color = LocalMzTokens.current.warning)
                            }
                        }
                        Switch(currency.enabled, { onEnable(currency.code, it) })
                    }
                    TextButton(onClick = {
                        editing = currency
                        pin = preferences.pins[currency.code].orEmpty()
                        spread = preferences.spreads[currency.code] ?: "0"
                    }) { Text(stringResource(R.string.p13_pin_spread)) }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.p13_fx_attribution),
                Modifier
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.exchangerate-api.com")))
                    }.padding(12.dp),
                color = LocalMzTokens.current.champagne,
            )
        }
    }
    editing?.let { currency ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(currency.code) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.p13_pin_note))
                    OutlinedTextField(pin, { pin = it }, label = { Text(stringResource(R.string.p13_pin_rate)) }, singleLine = true)
                    OutlinedTextField(spread, { spread = it }, label = { Text(stringResource(R.string.p13_spread)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(currency.code, pin, spread)
                    editing = null
                }) { Text(stringResource(R.string.p13_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.p13_cancel)) } },
        )
    }
}
