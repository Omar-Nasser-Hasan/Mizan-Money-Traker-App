// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Composable
fun V2PricesScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onAddHolding: () -> Unit,
) {
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observeLatestPrices() }.collectAsStateWithLifecycle(emptyList())
    val goldQuote = prices.firstOrNull { it.key == "GOLD_21K" }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var goldPrice by remember(goldQuote?.asOf) { mutableStateOf(goldQuote?.price.orEmpty()) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Update prices", style = MzTypography.title)
                    Text("Saved prices stay available when providers are offline.", style = MzTypography.bodySmall)
                    if (goldQuote == null) {
                        Text("No saved 21K gold price", style = MzTypography.bodySmall, color = LocalMzTokens.current.warning)
                    } else {
                        Text(
                            "${if (hideAmounts) "••••" else goldQuote.price} EGP/g · ${goldQuote.source}",
                            style = MzTypography.bodySmall,
                        )
                        Text(priceAgeText(goldQuote.asOf), style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
                        if (isStale(goldQuote.asOf)) {
                            Text("Stale · last-known price retained", style = MzTypography.bodySmall, color = LocalMzTokens.current.warning)
                        }
                    }
                    Text(
                        "Indicative dealer aggregation; confirm the price with the seller before trading.",
                        style = MzTypography.bodySmall,
                        color = LocalMzTokens.current.textDim,
                    )
                    Text(
                        "Daleelak (getdaleelak.com)",
                        modifier = Modifier.clickable { uriHandler.openUri("https://getdaleelak.com") },
                        style = MzTypography.bodySmall,
                        color = LocalMzTokens.current.champagne,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            goldPrice,
                            { goldPrice = it },
                            label = { Text("21K buy price · EGP per gram") },
                            modifier = Modifier.weight(1f),
                            visualTransformation =
                                if (hideAmounts) {
                                    PasswordVisualTransformation()
                                } else {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                },
                        )
                        MzButton(
                            "Save",
                            {
                                runCatching { BigDecimal(goldPrice) }.getOrNull()?.let { value ->
                                    scope.launch {
                                        runCatching { repository.saveManualGold21Price(value) }
                                            .onFailure { refreshMessage = it.message ?: "Could not save price." }
                                            .onSuccess { refreshMessage = "21K saved; 24K, 18K, and gold pound derived." }
                                    }
                                } ?: run { refreshMessage = "Enter a valid 21K price." }
                            },
                            style = MzButtonStyle.SECONDARY,
                        )
                    }
                    MzButton(
                        if (refreshing) "Updating…" else "Update from Daleelak",
                        {
                            refreshing = true
                            scope.launch {
                                repository
                                    .refreshGoldPrices()
                                    .onSuccess { refreshMessage = "Prices updated." }
                                    .onFailure { refreshMessage = "Update failed; saved prices are retained." }
                                refreshing = false
                            }
                        },
                        Modifier.fillMaxWidth(),
                        MzButtonStyle.PRIMARY,
                        enabled = !refreshing,
                    )
                    refreshMessage?.let { Text(it, style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim) }
                }
            }
        }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gold price ratios", style = MzTypography.title)
                    listOf("GOLD_24K", "GOLD_18K", "GOLD_POUND").forEach { key ->
                        val quote = prices.firstOrNull { it.key == key }
                        PriceLine(key.removePrefix("GOLD_"), quote, hideAmounts)
                    }
                    Text("24K = 24/21 · 18K = 18/21 · pound = 8 × 21K", style = MzTypography.bodySmall)
                }
            }
        }
        item { Text("Other holdings", style = MzTypography.title) }
        if (assets.isEmpty()) {
            item { MzEmptyState("No holdings", "Add a holding to track its latest price.", "Add holding", onAddHolding) }
        } else {
            items(assets, key = AssetEntity::id) { asset ->
                val key = assetPriceKey(asset)
                val quote = prices.firstOrNull { it.key == key }
                if (asset.type == "GOLD") {
                    val mode = goldPriceMode(asset, quote)
                    MzCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(asset.name, style = MzTypography.body)
                            PriceLine(mode, quote, hideAmounts)
                        }
                    }
                } else {
                    ManualAssetPriceRow(asset, quote, hideAmounts) { price ->
                        scope.launch {
                            runCatching { repository.saveManualPrice(asset.id, price, asset.quoteCurrency) }
                                .onFailure { refreshMessage = it.message ?: "Could not save price." }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "EGX and fund automatic quotes are unavailable; Manual prices remain the source until a supported provider exists.",
                style = MzTypography.bodySmall,
                color = LocalMzTokens.current.textDim,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ManualAssetPriceRow(
    asset: AssetEntity,
    quote: PriceSnapshotEntity?,
    hideAmounts: Boolean,
    onSave: (BigDecimal) -> Unit,
) {
    var value by remember(asset.id, quote?.asOf) { mutableStateOf(quote?.price.orEmpty()) }
    MzCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(asset.name, style = MzTypography.body)
                    Text(
                        "${asset.priceMode.lowercase().replaceFirstChar(Char::uppercase)} · ${asset.quoteCurrency}/${asset.unit}",
                        style = MzTypography.bodySmall,
                    )
                }
                if (quote != null && isStale(quote.asOf)) {
                    Text("Stale", style = MzTypography.bodySmall, color = LocalMzTokens.current.warning)
                }
            }
            if (quote != null) PriceLine(quote.source, quote, hideAmounts)
            OutlinedTextField(
                value,
                { value = it },
                label = { Text("Manual price · ${asset.quoteCurrency} per ${asset.unit}") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation =
                    if (hideAmounts) {
                        PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
            )
            MzButton(
                "Save price",
                { runCatching { BigDecimal(value) }.getOrNull()?.let(onSave) },
                Modifier.fillMaxWidth(),
                MzButtonStyle.SECONDARY,
            )
        }
    }
}

@Composable
private fun PriceLine(
    label: String,
    quote: PriceSnapshotEntity?,
    hideAmounts: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MzTypography.bodySmall)
        Text(
            quote?.let { "${if (hideAmounts) "••••" else it.price} ${it.currency}" } ?: "—",
            style = MzTypography.bodySmall,
        )
    }
    if (quote != null) {
        Text(
            "${quote.source} · ${priceAgeText(quote.asOf)}${if (isStale(quote.asOf)) " · stale" else ""}",
            style = MzTypography.bodySmall,
            color = LocalMzTokens.current.textDim,
        )
    }
}

private fun priceAgeText(asOf: Long): String {
    val hours = Duration.between(Instant.ofEpochMilli(asOf), Instant.now()).toHours().coerceAtLeast(0)
    return if (hours < 24) "$hours h old" else "${hours / 24} d old"
}

private fun isStale(asOf: Long): Boolean = Duration.between(Instant.ofEpochMilli(asOf), Instant.now()).toHours() >= 2

private fun assetPriceKey(asset: AssetEntity): String =
    when {
        asset.type != "GOLD" -> "MANUAL:${asset.id}"
        asset.goldForm == "COIN" -> "GOLD_POUND"
        else -> "GOLD_${asset.symbolOrKarats.uppercase()}"
    }

private fun goldPriceMode(
    asset: AssetEntity,
    quote: PriceSnapshotEntity?,
): String =
    when {
        quote == null && asset.priceMode == "DERIVED" -> "Derived"

        quote == null && asset.priceMode == "MANUAL" -> "Manual"

        quote?.isManual == true && quote.source.contains("21K", ignoreCase = true) &&
            (asset.goldForm == "COIN" || asset.symbolOrKarats.uppercase() != "21K") -> "Derived"

        quote?.isManual == true -> "Manual"

        else -> "Auto"
    }
