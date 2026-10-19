// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingFlag
import kotlinx.coroutines.launch

@Composable
fun V2DataSourcesScreen(
    repository: MizanRepository,
    onNavigate: (V2Route) -> Unit,
) {
    val state by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val prices by remember(repository) { repository.observeLatestPrices() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    V2SettingsList {
        if (state == null) {
            items(3) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else {
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.p13_gold_provider), style = MzTypography.title)
                        MzToggle(
                            state!![SettingFlag.GOLD_AUTO],
                            { scope.launch { repository.settings.set(SettingFlag.GOLD_AUTO, it) } },
                            stringResource(R.string.p13_gold_auto),
                        )
                        Text(stringResource(R.string.p13_gold_provider_note))
                        MzButton(stringResource(R.string.p13_provider_link), {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getdaleelak.com")))
                        }, style = MzButtonStyle.GHOST)
                    }
                }
            }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.p13_stock_provider), style = MzTypography.title)
                        Text(stringResource(R.string.p13_stock_manual))
                        MzButton(stringResource(R.string.p13_update_prices), { onNavigate(V2Route.Prices) })
                    }
                }
            }
            item {
                MzCard {
                    V2SettingRow(
                        "currency_exchange",
                        stringResource(R.string.p13_currencies),
                        stringResource(R.string.p13_fx_provider),
                    ) { onNavigate(V2Route.Currencies) }
                }
            }
            prices.forEach { price ->
                item(key = "${price.key}:${price.asOf}") {
                    MzCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(price.assetId ?: price.key, style = MzTypography.title)
                            Text(price.source)
                            Text(
                                java.time.Instant
                                    .ofEpochMilli(price.asOf)
                                    .atZone(java.time.ZoneId.of("Africa/Cairo"))
                                    .toString(),
                            )
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.p13_price_fallback), color = LocalMzTokens.current.textDim) }
        }
    }
}
