// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetValuation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
@Suppress("ktlint:standard:function-naming")
fun V2AssetDetailScreen(
    repository: MizanRepository,
    assetId: String,
    hideAmounts: Boolean,
    onTrade: (String, Boolean) -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val balances by repository.observeAllAccountBalances().collectAsStateWithLifecycle(emptyList())
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val assets by repository.observeAssets().collectAsStateWithLifecycle(emptyList())
    val transactions by repository.observeAllAssetTransactions().collectAsStateWithLifecycle(emptyList())
    val prices by repository.observePriceHistory().collectAsStateWithLifecycle(emptyList())
    val fxHistory by repository.observeFxHistory().collectAsStateWithLifecycle(emptyList())
    val statsEntries by repository.observeStatsEntries().collectAsStateWithLifecycle(emptyList())
    var range by rememberSaveable { mutableStateOf("6M") }
    val now = Instant.now()
    val snapshot =
        remember(accounts, balances, currencies, assets, transactions, prices, fxHistory, statsEntries, now) {
            V2WealthModels.build(accounts, balances, currencies, assets, transactions, prices, fxHistory, statsEntries, now)
        }
    val position = snapshot.positions.firstOrNull { it.asset.id == assetId }
    if (position == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MzSkeleton(Modifier.fillMaxWidth().height(140.dp))
            MzSkeleton(Modifier.fillMaxWidth().height(220.dp))
            MzSkeleton(Modifier.fillMaxWidth().height(120.dp))
        }
        return
    }
    val asset = position.asset
    val decimals = currencies.firstOrNull { it.code == position.priceCurrency }?.decimals ?: 2
    val assetTrades = transactions.filter { it.assetId == assetId && it.deletedAt == null }.sortedByDescending { it.date }
    val latestSnapshot = prices.filter { it.key == assetPriceKey(asset) }.maxByOrNull { it.asOf }
    val currentValueMinor =
        position.pricePerUnit?.let { price ->
            AssetValuation.valueMinor(position.quantity, price, decimals, BigDecimal(asset.resaleDeductionPct))
        } ?: 0L
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val detailMetrics =
        remember(position, assetTrades, currentValueMinor, today) {
            V2AssetDetailModels.metrics(position, assetTrades.map(::toDomainTrade), currentValueMinor, today)
        }
    val cutoff =
        remember(range) {
            val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
            when (range) {
                "1M" -> today.minusMonths(1)
                "6M" -> today.minusMonths(6)
                "1Y" -> today.minusYears(1)
                else -> LocalDate.MIN
            }
        }
    val history =
        remember(prices, assetTrades, range, cutoff, decimals) {
            val saved =
                prices
                    .filter {
                        it.key == assetPriceKey(asset) &&
                            Instant.ofEpochMilli(it.asOf).atZone(ZoneId.of("Africa/Cairo")).toLocalDate() >= cutoff
                    }.sortedBy { it.asOf }
            if (saved.isNotEmpty()) {
                saved.map { point ->
                    BigDecimal(point.price).movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact()
                }
            } else {
                assetTrades.asReversed().filter { LocalDate.parse(it.date) >= cutoff }.map { trade ->
                    BigDecimal(trade.unitPrice).movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact()
                }
            }
        }
    val tokens = LocalMzTokens.current
    val age = position.ageHours?.let(::priceAgeLabel) ?: "No saved market price"
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MzIcon(
                        if (asset.type ==
                            "GOLD"
                        ) {
                            "token"
                        } else if (asset.type ==
                            "STOCK"
                        ) {
                            "show_chart"
                        } else {
                            "account_balance"
                        },
                        asset.name,
                        tint = tokens.investment,
                    )
                    Text(asset.name, style = MzTypography.title)
                    Text(
                        "${position.quantity.stripTrailingZeros().toPlainString()} ${asset.unit} · ${asset.symbolOrKarats}",
                        style = MzTypography.bodySmall,
                        color = tokens.textDim,
                    )
                    AmountText(position.valueEgpMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.hero)
                    AssetProfitAmount(detailMetrics.unrealizedProfitEgpMinor, hideAmounts)
                }
            }
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1M", "6M", "1Y", "All").forEach { label ->
                                MzChip(label, range == label, { range = label }, Modifier.weight(1f))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("MARKET RATE", style = MzTypography.labelCaps, color = tokens.textDim)
                                Text(
                                    "${position.pricePerUnit?.stripTrailingZeros()?.toPlainString() ?: "—"} ${position.priceCurrency} / ${asset.unit}",
                                    style = MzTypography.amountSmall,
                                )
                            }
                            if (position.stale) Text("Stale · $age", style = MzTypography.bodySmall, color = tokens.warning)
                        }
                        if (history.isNotEmpty()) {
                            MzAreaChart(history, Modifier.fillMaxWidth())
                        } else {
                            Text(
                                "No price history yet. Saved prices and trades remain visible offline.",
                                style = MzTypography.bodySmall,
                                color = tokens.textDim,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (history.size >
                                    1
                                ) {
                                    range
                                } else {
                                    "First saved value"
                                },
                                style = MzTypography.bodySmall,
                                color = tokens.textDim,
                            )
                            Text(
                                "${latestSnapshot?.source ?: position.sourceLabel} · $age",
                                style = MzTypography.bodySmall,
                                color = if (position.stale) tokens.warning else tokens.textDim,
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetMetric(
                            "Average cost",
                            "${detailMetrics.averageCostEgpPerUnit.stripTrailingZeros().toPlainString()} EGP/${asset.unit}",
                            Modifier.weight(1f),
                        )
                        AssetMetric(
                            "Unrealized P/L",
                            formatMinor(detailMetrics.unrealizedProfitEgpMinor),
                            Modifier.weight(1f),
                            positive =
                                detailMetrics.unrealizedProfitEgpMinor >= 0,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetMetric(
                            "Realized P/L",
                            formatMinor(detailMetrics.realizedProfitEgpMinor),
                            Modifier.weight(1f),
                            positive =
                                detailMetrics.realizedProfitEgpMinor >= 0,
                        )
                        AssetMetric("Weight", "${detailMetrics.weightBasisPoints / 100}% of holdings", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetMetric(
                            "XIRR",
                            detailMetrics.xirr?.let { "${it.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)}% p.a." } ?: "—",
                            Modifier.weight(1f),
                        )
                        AssetMetric(
                            "Price today",
                            position.pricePerUnit?.let {
                                "${it.stripTrailingZeros().toPlainString()} ${position.priceCurrency}/${asset.unit}"
                            }
                                ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Market source", style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${latestSnapshot?.source ?: position.sourceLabel} · updated $age",
                            style = MzTypography.bodySmall,
                            color = tokens.textDim,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lots", style = MzTypography.title)
                    Text("${assetTrades.size} lots", style = MzTypography.bodySmall, color = tokens.textDim)
                }
            }
            if (assetTrades.isEmpty()) {
                item {
                    MzCard(
                        Modifier.fillMaxWidth(),
                    ) { Text("No trades recorded yet.", Modifier.padding(16.dp), color = tokens.textDim) }
                }
            } else {
                items(assetTrades, key = { it.id }) { trade -> AssetLotRow(trade, asset.unit, asset.quoteCurrency, hideAmounts) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MzButton(
                "Sell",
                { onTrade(assetId, true) },
                Modifier.weight(1f),
                MzButtonStyle.SECONDARY,
                enabled =
                    position.quantity > BigDecimal.ZERO,
            )
            MzButton("Buy", { onTrade(assetId, false) }, Modifier.weight(1f))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AssetMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    positive: Boolean? = null,
) {
    MzCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
            Text(
                value,
                style = MzTypography.amountSmall,
                color =
                    when (positive) {
                        true -> LocalMzTokens.current.income
                        false -> LocalMzTokens.current.expense
                        null -> LocalMzTokens.current.text
                    },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AssetLotRow(
    trade: AssetTransactionEntity,
    unit: String,
    currency: String,
    hideAmounts: Boolean,
) {
    MzCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MzIcon(if (trade.kind == "BUY") "add" else "remove", trade.kind, tint = LocalMzTokens.current.investment)
            Column(Modifier.weight(1f)) {
                Text(
                    "${trade.kind.lowercase().replaceFirstChar { it.uppercase() }} ${trade.quantity} $unit @ ${trade.unitPrice}",
                    style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "${trade.date}${if (trade.note.isBlank()) "" else " · ${trade.note}"}",
                    style = MzTypography.bodySmall,
                    color = LocalMzTokens.current.textDim,
                )
            }
            AmountText(trade.totalMinor, currency, hideAmounts = hideAmounts, textStyle = MzTypography.amountSmall)
        }
    }
}

private fun assetPriceKey(asset: app.mizan.data.db.AssetEntity): String =
    if (asset.type == "GOLD") "GOLD_${asset.symbolOrKarats.uppercase()}" else "MANUAL:${asset.id}"

private fun toDomainTrade(trade: AssetTransactionEntity) =
    AssetTransaction(
        trade.id,
        trade.assetId,
        AssetTransactionKind.valueOf(trade.kind),
        LocalDate.parse(trade.date),
        BigDecimal(trade.quantity),
        BigDecimal(trade.unitPrice),
        trade.feeMinor,
        trade.totalMinor,
        trade.txnId,
        trade.note,
    )

private fun priceAgeLabel(hours: Long): String = if (hours < 24) "$hours h ago" else "$hours h old"

private fun formatMinor(minor: Long): String =
    "${if (minor < 0) "−" else "+"}${BigDecimal.valueOf(kotlin.math.abs(minor)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)} EGP"

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AssetProfitAmount(
    value: Long,
    hideAmounts: Boolean,
) {
    AmountText(
        kotlin.math.abs(value),
        "EGP",
        style = if (value >= 0) AmountStyle.INCOME else AmountStyle.EXPENSE,
        hideAmounts = hideAmounts,
        textStyle = MzTypography.bodySmall,
    )
}
