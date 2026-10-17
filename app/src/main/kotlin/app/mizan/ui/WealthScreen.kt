// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AssetEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.Asset
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import app.mizan.domain.assets.AverageCost
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun wealthScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onAsset: (String) -> Unit,
    onCreate: () -> Unit,
    onPrices: () -> Unit,
) {
    val assets by repository.observeAssets().collectAsStateWithLifecycle(emptyList())
    val prices by repository.observeLatestPrices().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf<AssetType?>(null) }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Wealth")
            Text("Holdings are valued from their last known price. Stale values remain visible when offline.")
            TextButton(onClick = { scope.launch { repository.refreshGoldPrices() } }) { Text("Update gold prices") }
            TextButton(onClick = onPrices) { Text("Update all prices") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selectedType == null, { selectedType = null }, { Text("All") })
                AssetType.entries.forEach { type ->
                    FilterChip(
                        selectedType == type,
                        { selectedType = type },
                        { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Button(onClick = onCreate) { Text("Add asset") }
        }
        items(assets.filter { selectedType == null || it.type == selectedType!!.name }, key = { it.id }) { asset ->
            Card(onClick = { onAsset(asset.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(asset.name)
                    Text("${asset.type.lowercase()} · ${asset.symbolOrKarats} · ${asset.quoteCurrency}")
                    val key = if (asset.type == "GOLD") "GOLD_${asset.symbolOrKarats.uppercase()}" else "MANUAL:${asset.id}"
                    val last = prices.firstOrNull { it.key == key }
                    val stale = last != null && !last.isManual && Instant.now().toEpochMilli() - last.asOf > 24L * 60 * 60 * 1000
                    Text(last?.let { "Last price ${it.price} ${it.currency}${if (stale) " · stale" else ""}" } ?: "No price")
                    Text("Resale deduction: ${asset.resaleDeductionPct}%")
                }
            }
        }
    }
}

@Composable
fun assetEditorScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AssetType.GOLD) }
    var currency by remember { mutableStateOf("EGP") }
    var deduction by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("New asset")
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssetType.entries.forEach { value -> FilterChip(type == value, { type = value }, { Text(value.name) }) }
        }
        OutlinedTextField(symbol, { symbol = it }, label = { Text("Ticker or gold karat, e.g. 21K") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("Quote currency") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(deduction, { deduction = it }, label = { Text("Resale deduction %") }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it) }
        Button(onClick = {
            val pct = runCatching { BigDecimal(deduction) }.getOrNull()
            if (name.isBlank() || symbol.isBlank() || pct == null ||
                pct < BigDecimal.ZERO
            ) {
                error = "Enter a name, identifier, and non-negative deduction."
            } else {
                scope.launch {
                    repository.saveAsset(
                        Asset(
                            UUID.randomUUID().toString(),
                            name.trim(),
                            type,
                            symbol.trim(),
                            currency,
                            if (type ==
                                AssetType.GOLD
                            ) {
                                "g"
                            } else {
                                "unit"
                            },
                            pct,
                        ),
                    )
                    onDone()
                }
            }
        }) { Text("Save asset") }
    }
}

@Composable
fun assetDetailScreen(
    repository: MizanRepository,
    assetId: String,
    modifier: Modifier,
) {
    val asset by repository.observeAsset(assetId).collectAsStateWithLifecycle(null)
    val trades by repository.observeAssetTransactions(assetId).collectAsStateWithLifecycle(emptyList())
    val latestPrices by repository.observeLatestPrices().collectAsStateWithLifecycle(emptyList())
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var quantity by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var sale by remember { mutableStateOf(false) }
    var manualPrice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val current = asset ?: return
    val priceKey = if (current.type == "GOLD") "GOLD_${current.symbolOrKarats.uppercase()}" else "MANUAL:${current.id}"
    val price = latestPrices.firstOrNull { it.key == priceKey }
    val position =
        remember(trades) {
            AverageCost.position(
                trades.map {
                    AssetTransaction(
                        it.id,
                        it.assetId,
                        AssetTransactionKind.valueOf(it.kind),
                        LocalDate.parse(it.date),
                        BigDecimal(it.quantity),
                        BigDecimal(it.unitPrice),
                        it.feeMinor,
                        it.totalMinor,
                        it.txnId,
                        it.note,
                    )
                },
            )
        }
    val investmentAccount = accounts.firstOrNull { it.type == "INVESTMENT" } ?: accounts.firstOrNull()
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(current.name)
            Text(
                "Held: ${position.quantity.stripTrailingZeros().toPlainString()} ${current.unit} · cost: ${position.costMinor} minor ${current.quoteCurrency}",
            )
            Text(
                price?.let { "Price ${it.price} ${it.currency} · ${if (it.isManual) "Manual" else it.source}" }
                    ?: "No price yet — add a manual value.",
            )
            OutlinedTextField(manualPrice, {
                manualPrice = it
            }, label = { Text("Manual price per ${current.unit}") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val value = runCatching { BigDecimal(manualPrice) }.getOrNull()
                if (value == null ||
                    value <= BigDecimal.ZERO
                ) {
                    error = "Enter a positive price."
                } else {
                    scope.launch {
                        if (current.type == "GOLD" && current.symbolOrKarats.equals("21K", ignoreCase = true)) {
                            repository.saveManualGold21Price(value)
                        } else {
                            repository.saveManualPrice(current.id, value, current.quoteCurrency)
                        }
                        manualPrice =
                            ""
                    }
                }
            }) { Text("Save manual price") }
            Text(if (sale) "Sell" else "Buy")
            Row {
                FilterChip(!sale, { sale = false }, { Text("Buy") })
                FilterChip(sale, { sale = true }, { Text("Sell") })
            }
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                unitPrice,
                { unitPrice = it },
                label = { Text("Price per ${current.unit}") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(fee, { fee = it }, label = { Text("Fee in minor units") }, modifier = Modifier.fillMaxWidth())
            Text("Cash account: ${investmentAccount?.name ?: "Create an account first"}")
            error?.let { Text(it) }
            Button(onClick = {
                val qty = runCatching { BigDecimal(quantity) }.getOrNull()
                val perUnit = runCatching { BigDecimal(unitPrice) }.getOrNull()
                val feeMinor = fee.toLongOrNull()
                val decimals = currencies.firstOrNull { it.code == current.quoteCurrency }?.decimals
                if (qty == null || qty <= BigDecimal.ZERO || perUnit == null || perUnit <= BigDecimal.ZERO || feeMinor == null ||
                    feeMinor < 0 ||
                    investmentAccount == null ||
                    decimals == null
                ) {
                    error = "Enter valid values and create an account."
                } else if (sale && qty > position.quantity) {
                    error = "You cannot sell more than the holding."
                } else {
                    scope.launch {
                        val gross =
                            qty
                                .multiply(perUnit)
                                .movePointRight(decimals)
                                .setScale(0, RoundingMode.HALF_UP)
                                .longValueExact()
                        val total = if (sale) gross - feeMinor else Math.addExact(gross, feeMinor)
                        if (total <= 0) {
                            error = "Sale proceeds must remain positive."
                        } else {
                            val rate = repository.effectiveRate(current.quoteCurrency, LocalDate.now())
                            if (rate == null) {
                                error = "Set a booking FX rate first."
                            } else {
                                repository.postAssetTrade(
                                    AssetTransaction(
                                        UUID.randomUUID().toString(),
                                        current.id,
                                        if (sale) AssetTransactionKind.SELL else AssetTransactionKind.BUY,
                                        LocalDate.now(),
                                        qty,
                                        perUnit,
                                        feeMinor,
                                        total,
                                    ),
                                    investmentAccount.id,
                                    decimals,
                                    rate,
                                )
                                quantity = ""
                                unitPrice = ""
                                fee = "0"
                            }
                        }
                    }
                }
            }) { Text(if (sale) "Post sale" else "Post purchase") }
        }
        items(trades, key = { it.id }) { trade -> Text("${trade.date} · ${trade.kind} · ${trade.quantity} at ${trade.unitPrice}") }
    }
}

@Composable
fun priceUpdateScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val assets by repository.observeAssets().collectAsStateWithLifecycle(emptyList())
    val prices by repository.observeLatestPrices().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Update prices")
            Text("Gold uses the best-effort Daleelak dealer feed. EGX and fund prices remain manual because SPIKE-S1 failed.")
            Button(
                onClick = {
                    scope.launch {
                        status = repository.refreshGoldPrices().fold({ "Gold prices updated." }, { "Gold update failed; last-known values were kept." })
                    }
                },
            ) { Text("Update all") }
            if (status.isNotBlank()) Text(status)
        }
        items(assets, key = { it.id }) { asset ->
            ManualPriceRow(asset, prices, repository)
        }
    }
}

@Composable
private fun ManualPriceRow(
    asset: AssetEntity,
    prices: List<app.mizan.data.db.PriceSnapshotEntity>,
    repository: MizanRepository,
) {
    var value by remember(asset.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val key = if (asset.type == "GOLD") "GOLD_${asset.symbolOrKarats.uppercase()}" else "MANUAL:${asset.id}"
    val last = prices.firstOrNull { it.key == key }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(asset.name)
            Text(last?.let { "${it.price} ${it.currency} · ${it.source}" } ?: "No saved price")
            OutlinedTextField(value, { value = it }, label = { Text("Manual price") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val price = runCatching { BigDecimal(value) }.getOrNull()
                if (price != null && price > BigDecimal.ZERO) scope.launch {
                    if (asset.type == "GOLD" && asset.symbolOrKarats.equals("21K", true)) repository.saveManualGold21Price(price)
                    else repository.saveManualPrice(asset.id, price, asset.quoteCurrency)
                    value = ""
                }
            }) { Text("Save") }
        }
    }
}
