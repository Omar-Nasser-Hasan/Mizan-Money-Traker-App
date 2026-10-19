// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.mizan.domain.assets.AverageCost
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
@Suppress("ktlint:standard:function-naming")
fun V2AssetTradeScreen(
    repository: MizanRepository,
    initialAssetId: String?,
    initialSelling: Boolean,
    hideAmounts: Boolean,
    onSaved: (String) -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val trades by remember(repository) { repository.observeAllAssetTransactions() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observePriceHistory() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var selectedAssetId by rememberSaveable(initialAssetId) { mutableStateOf(initialAssetId) }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var selling by rememberSaveable(initialSelling) { mutableStateOf(initialSelling) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unitPrice by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("0") }
    var bookedDate by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("Africa/Cairo")).toString()) }
    var assetMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val asset = assets.firstOrNull { it.id == selectedAssetId } ?: assets.firstOrNull()
    LaunchedEffect(assets, selectedAssetId) {
        if (selectedAssetId == null || assets.none { it.id == selectedAssetId }) {
            selectedAssetId = assets.firstOrNull()?.id
        }
    }
    val eligibleAccounts =
        accounts.filter {
            it.type in setOf("CASH", "BANK", "EWALLET", "SAVINGS", "INVESTMENT") &&
                (asset == null || it.defaultCurrency == asset.quoteCurrency)
        }
    LaunchedEffect(eligibleAccounts, selectedAccountId) {
        if (selectedAccountId == null || eligibleAccounts.none { it.id == selectedAccountId }) {
            selectedAccountId = eligibleAccounts.firstOrNull()?.id
        }
    }
    LaunchedEffect(asset?.id) {
        quantity = ""
        unitPrice =
            asset
                ?.let { current ->
                    prices
                        .filter { it.key == tradePriceKey(current) }
                        .maxByOrNull { it.asOf }
                        ?.price
                        .orEmpty()
                }.orEmpty()
        fee = "0"
    }
    val selectedAccount = eligibleAccounts.firstOrNull { it.id == selectedAccountId }
    if (asset == null) {
        MzEmptyState("No holdings", "Add a holding before recording a buy or sale.", "Back", {})
        return
    }
    val currency = asset.quoteCurrency
    val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
    val selectedTrades = trades.filter { it.assetId == asset.id && it.deletedAt == null }.map(::toDomainAssetTrade)
    val position = remember(selectedTrades) { AverageCost.position(selectedTrades) }
    val savedPrice = prices.filter { it.key == tradePriceKey(asset) }.maxByOrNull { it.asOf }
    val todayPrice = savedPrice?.price?.let { runCatching { BigDecimal(it) }.getOrNull() }
    val parsedQuantity = quantity.trim().toBigDecimalOrNull()
    val parsedUnitPrice = unitPrice.trim().toBigDecimalOrNull()
    val feeMinor = parseMinor(fee, decimals)
    val preview =
        remember(parsedQuantity, parsedUnitPrice, feeMinor, decimals, selling, todayPrice, asset.resaleDeductionPct) {
            if (parsedQuantity == null || parsedUnitPrice == null || feeMinor == null) {
                null
            } else {
                runCatching {
                    V2AssetTradeModels.preview(
                        parsedQuantity,
                        parsedUnitPrice,
                        feeMinor,
                        decimals,
                        selling,
                        todayPrice,
                        BigDecimal(asset.resaleDeductionPct),
                    )
                }.getOrNull()
            }
        }
    val quoteAgeHours = savedPrice?.let { priceAgeHours(Instant.now(), Instant.ofEpochMilli(it.asOf)) }
    val quoteDescription =
        if (quoteAgeHours == null) {
            "No saved market quote · use your trade price"
        } else {
            "${savedPrice?.source ?: "Last known price"} · ${priceAgeLabel(quoteAgeHours)}${if (quoteAgeHours >= 24L) " · stale" else ""}"
        }
    val tradeKindNote =
        if (selling) {
            "Selling records an asset sale; it is not income."
        } else {
            "Buying moves cash into a holding. It is not an expense."
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MzSegmented(listOf("Buy", "Sell"), if (selling) 1 else 0, { selling = it == 1 })
        }
        item {
            Column {
                MzCard(Modifier.fillMaxWidth().clickable { assetMenu = true }) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            MzIcon(
                                if (asset.type == "GOLD") "token" else "show_chart",
                                asset.name,
                                tint = LocalMzTokens.current.investment,
                            )
                            Column {
                                Text(asset.name, style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${asset.symbolOrKarats} · $currency/${asset.unit}",
                                    style = MzTypography.bodySmall,
                                    color = LocalMzTokens.current.textDim,
                                )
                            }
                        }
                        MzIcon("expand_more", "Choose asset", tint = LocalMzTokens.current.textDim)
                    }
                }
                DropdownMenu(expanded = assetMenu, onDismissRequest = { assetMenu = false }) {
                    assets.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text("${choice.name} · ${choice.symbolOrKarats}") },
                            onClick = {
                                selectedAssetId = choice.id
                                assetMenu = false
                            },
                        )
                    }
                }
            }
        }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (selling) "TOTAL RECEIVED" else "TOTAL PAID",
                            style = MzTypography.labelCaps,
                            color = LocalMzTokens.current.textDim,
                        )
                        Text(currency, style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
                    }
                    AmountText(preview?.cashMinor ?: 0L, currency, hideAmounts = hideAmounts, textStyle = MzTypography.hero)
                    Text(
                        quoteDescription,
                        style = MzTypography.bodySmall,
                        color =
                            if (quoteAgeHours?.let { it >= 24L } == true) {
                                LocalMzTokens.current.warning
                            } else {
                                LocalMzTokens.current.textDim
                            },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MzInput(quantity, { quantity = it }, "QUANTITY · ${asset.unit}", Modifier.weight(1f))
                MzInput(unitPrice, { unitPrice = it }, "UNIT PRICE · $currency", Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                MzInput(fee, { fee = it }, "FEE · $currency", Modifier.weight(1f))
                MzInput(bookedDate, { bookedDate = it }, "DATE · YYYY-MM-DD", Modifier.weight(1f))
            }
        }
        item {
            Column {
                MzCard(Modifier.fillMaxWidth().clickable { accountMenu = true }) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                if (selling) "To account" else "From account",
                                style = MzTypography.bodySmall,
                                color = LocalMzTokens.current.textDim,
                            )
                            Text(
                                selectedAccount?.name ?: "Choose an account",
                                style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                        MzIcon("expand_more", "Choose account", tint = LocalMzTokens.current.textDim)
                    }
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    eligibleAccounts.forEach { account ->
                        DropdownMenuItem(text = { Text("${account.name} · ${account.defaultCurrency}") }, onClick = {
                            selectedAccountId = account.id
                            accountMenu = false
                        })
                    }
                }
            }
        }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (selling) "Sale proceeds" else "Price impact",
                        style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold),
                    )
                    val dealerValue = preview?.currentDealerValueMinor
                    if (dealerValue != null && preview?.marketImpactMinor != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Today's dealer buy value", style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
                            AmountText(dealerValue, currency, hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Compared with ${if (selling) "proceeds" else "price paid"}",
                                style = MzTypography.bodySmall,
                                color = LocalMzTokens.current.textDim,
                            )
                            AmountText(
                                kotlin.math.abs(preview.marketImpactMinor),
                                currency,
                                style = if (preview.marketImpactMinor >= 0) AmountStyle.INCOME else AmountStyle.EXPENSE,
                                hideAmounts = hideAmounts,
                                textStyle = MzTypography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            "Save a market price to compare this trade with today's dealer buy value.",
                            style = MzTypography.bodySmall,
                            color = LocalMzTokens.current.textDim,
                        )
                    }
                    Text(tradeKindNote, style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
                }
            }
        }
        if (selling && parsedQuantity != null && parsedQuantity > position.quantity) {
            item {
                Text(
                    "You cannot sell more than ${position.quantity.stripTrailingZeros().toPlainString()} ${asset.unit}.",
                    color = LocalMzTokens.current.expense,
                    style = MzTypography.bodySmall,
                )
            }
        }
        error?.let { message -> item { Text(message, color = LocalMzTokens.current.expense, style = MzTypography.bodySmall) } }
        if (eligibleAccounts.isEmpty()) {
            item {
                MzEmptyState("No matching cash account", "Add an account in $currency to record this trade.", "Add account", {})
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            MzButton(
                if (selling) "Post sale" else "Save acquisition",
                onClick = {
                    val q = parsedQuantity
                    val price = parsedUnitPrice
                    val date = runCatching { LocalDate.parse(bookedDate) }.getOrNull()
                    if (
                        q == null || price == null || feeMinor == null || date == null || selectedAccount == null || preview == null
                    ) {
                        error = "Enter a valid quantity, unit price, fee, date, and account."
                    } else if (selling && q > position.quantity) {
                        error = "You cannot sell more than the holding."
                    } else {
                        saving = true
                        error = null
                        scope.launch {
                            runCatching {
                                val rate =
                                    repository.effectiveRate(currency, date)
                                        ?: throw IllegalStateException("Set a booking FX rate first.")
                                repository.postAssetTrade(
                                    AssetTransaction(
                                        id = UUID.randomUUID().toString(),
                                        assetId = asset.id,
                                        kind = if (selling) AssetTransactionKind.SELL else AssetTransactionKind.BUY,
                                        date = date,
                                        quantity = q,
                                        unitPrice = price,
                                        feeMinor = feeMinor,
                                        totalMinor = preview.cashMinor,
                                    ),
                                    selectedAccount.id,
                                    decimals,
                                    rate,
                                )
                            }.onSuccess {
                                onSaved(asset.id)
                            }.onFailure { failure ->
                                error = failure.message ?: "Could not save this trade."
                            }
                            saving = false
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled =
                    !saving && preview != null && selectedAccount != null &&
                        (!selling || parsedQuantity?.let { it <= position.quantity } == true),
            )
        }
    }
}

private fun tradePriceKey(asset: app.mizan.data.db.AssetEntity) =
    when {
        asset.type != "GOLD" -> "MANUAL:${asset.id}"
        asset.goldForm == "COIN" -> "GOLD_POUND"
        else -> "GOLD_${asset.symbolOrKarats.uppercase()}"
    }

private fun toDomainAssetTrade(trade: AssetTransactionEntity) =
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

private fun parseMinor(
    value: String,
    decimals: Int,
): Long? =
    value
        .trim()
        .toBigDecimalOrNull()
        ?.movePointRight(decimals)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.longValueExactOrNull()

private fun priceAgeHours(
    now: Instant,
    then: Instant,
): Long = (now.toEpochMilli() - then.toEpochMilli()).coerceAtLeast(0L) / 3_600_000L

private fun priceAgeLabel(hours: Long): String = if (hours < 24L) "$hours h ago" else "$hours h old"

private fun java.math.BigDecimal.longValueExactOrNull(): Long? = runCatching { longValueExact() }.getOrNull()
