// Copyright (c) 2026 Mizan contributors
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import app.mizan.domain.assets.Asset
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import app.mizan.domain.assets.AssetValuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAllAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val transactions by remember(repository) { repository.observeAllAssetTransactions() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observePriceHistory() }.collectAsStateWithLifecycle(emptyList())
    val fxHistory by remember(repository) { repository.observeFxHistory() }.collectAsStateWithLifecycle(emptyList())
    val statsEntries by remember(repository) { repository.observeStatsEntries() }.collectAsStateWithLifecycle(emptyList())
    var range by rememberSaveable { mutableStateOf("6M") }
    var addOwnedLot by rememberSaveable { mutableStateOf(false) }
    var editAsset by rememberSaveable { mutableStateOf(false) }
    var editType by rememberSaveable(assetId) { mutableStateOf(AssetType.GOLD.name) }
    var editName by rememberSaveable(assetId) { mutableStateOf("") }
    var editIdentifier by rememberSaveable(assetId) { mutableStateOf("") }
    var editQuoteCurrency by rememberSaveable(assetId) { mutableStateOf("EGP") }
    var editGoldForm by rememberSaveable(assetId) { mutableStateOf("GRAM") }
    var editPriceMode by rememberSaveable(assetId) { mutableStateOf("MANUAL") }
    var editResaleDeduction by rememberSaveable(assetId) { mutableStateOf("") }
    var editError by remember(assetId) { mutableStateOf<String?>(null) }
    var editingOwnedLotId by rememberSaveable(assetId) { mutableStateOf<String?>(null) }
    var lotQuantity by rememberSaveable(assetId) { mutableStateOf("") }
    var lotAmountPaid by rememberSaveable(assetId) { mutableStateOf("") }
    var lotUnitCost by rememberSaveable(assetId) { mutableStateOf("") }
    var lotCurrentPrice by rememberSaveable(assetId) { mutableStateOf("") }
    var lotDate by rememberSaveable(assetId) { mutableStateOf(LocalDate.now(ZoneId.of("Africa/Cairo")).toString()) }
    var lotError by remember(assetId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snapshot by produceState<V2WealthSnapshot?>(
        null,
        accounts,
        balances,
        currencies,
        assets,
        transactions,
        prices,
        fxHistory,
        statsEntries,
    ) {
        value =
            withContext(Dispatchers.Default) {
                V2WealthModels.build(accounts, balances, currencies, assets, transactions, prices, fxHistory, statsEntries, Instant.now())
            }
    }
    val position = snapshot?.positions?.firstOrNull { it.asset.id == assetId }
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
    val metrics by produceState<V2AssetDetailMetrics?>(null, position, assetTrades, currentValueMinor, today) {
        value =
            withContext(
                Dispatchers.Default,
            ) { V2AssetDetailModels.metrics(position, assetTrades.map(::toDomainTrade), currentValueMinor, today) }
    }
    val detailMetrics =
        metrics ?: run {
            MzSkeleton(Modifier.fillMaxWidth().height(140.dp))
            return
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
    val chartPriceKey = assetPriceKey(asset)
    val history =
        remember(prices, assetTrades, chartPriceKey, range, cutoff, decimals) {
            V2AssetDetailModels.chartHistory(
                marketPoints =
                    prices
                        .filter { it.key == chartPriceKey }
                        .map { point ->
                            V2AssetHistoryPoint(
                                Instant.ofEpochMilli(point.asOf).atZone(ZoneId.of("Africa/Cairo")).toLocalDate(),
                                BigDecimal(point.price).movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                            )
                        },
                tradePoints =
                    assetTrades.map { trade ->
                        V2AssetHistoryPoint(
                            LocalDate.parse(trade.date),
                            BigDecimal(trade.unitPrice).movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                        )
                    },
                cutoff = cutoff,
            )
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
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(asset.name, style = MzTypography.title)
                        Spacer(Modifier.width(8.dp))
                        MzButton(
                            "Edit",
                            {
                                editType = asset.type
                                editName = asset.name
                                editIdentifier = asset.symbolOrKarats
                                editQuoteCurrency = asset.quoteCurrency
                                editGoldForm = asset.goldForm ?: "GRAM"
                                editPriceMode = asset.priceMode
                                editResaleDeduction = asset.resaleDeductionPct
                                editError = null
                                editAsset = true
                            },
                            style = MzButtonStyle.GHOST,
                        )
                    }
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
                        if (history.points.isNotEmpty()) {
                            MzAreaChart(
                                history.points.map { it.valueMinor },
                                Modifier.fillMaxWidth(),
                                hideAmounts = hideAmounts,
                                xAxisLabels = chartDateLabels(history.points.map { it.date }),
                            )
                        } else {
                            Text(
                                "No price history yet. Saved prices and trades remain visible offline.",
                                style = MzTypography.bodySmall,
                                color = tokens.textDim,
                            )
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (history.points.size >
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
                                "${history.sourceLabel} · ${latestSnapshot?.source ?: position.sourceLabel} · $age",
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
                MzButton("Add owned holding", { addOwnedLot = true }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY)
            }
            if (assetTrades.isEmpty()) {
                item {
                    MzCard(
                        Modifier.fillMaxWidth(),
                    ) { Text("No trades recorded yet.", Modifier.padding(16.dp), color = tokens.textDim) }
                }
            } else {
                items(assetTrades, key = { it.id }) { trade ->
                    AssetLotRow(
                        trade,
                        asset.unit,
                        asset.quoteCurrency,
                        hideAmounts,
                        editable = trade.kind == "BUY" && trade.txnId == null,
                    ) {
                        editingOwnedLotId = trade.id
                        lotQuantity = trade.quantity
                        lotAmountPaid =
                            BigDecimal
                                .valueOf(trade.totalMinor)
                                .movePointLeft(decimals)
                                .stripTrailingZeros()
                                .toPlainString()
                        lotUnitCost = trade.unitPrice
                        lotCurrentPrice =
                            position.pricePerUnit
                                ?.stripTrailingZeros()
                                ?.toPlainString()
                                .orEmpty()
                        lotDate = trade.date
                        lotError = null
                        addOwnedLot = false
                    }
                }
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
    if (addOwnedLot || editingOwnedLotId != null) {
        val currencyDecimals = currencies.firstOrNull { it.code == asset.quoteCurrency }?.decimals ?: 2
        val editingOwnedLot = editingOwnedLotId != null
        ModalBottomSheet(
            onDismissRequest = {
                addOwnedLot = false
                editingOwnedLotId = null
            },
            containerColor = LocalMzTokens.current.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MzSpacing.sideMargin).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (editingOwnedLot) "Edit owned lot" else "Add an already owned lot", style = MzTypography.title)
                Text(
                    if (editingOwnedLot) {
                        "Correct the quantity, purchase cost, and date. This does not change your cash balance."
                    } else {
                        "This adds the holding and cost basis without changing your cash balance."
                    },
                    style = MzTypography.bodySmall,
                )
                MzInput(lotQuantity, { lotQuantity = it }, "Quantity (${asset.unit})")
                MzInput(lotAmountPaid, { lotAmountPaid = it }, "Total amount paid · ${asset.quoteCurrency}")
                MzInput(lotUnitCost, { lotUnitCost = it }, "Purchase price per ${asset.unit} · ${asset.quoteCurrency}")
                MzInput(lotDate, { lotDate = it }, "Purchase date · YYYY-MM-DD")
                MzInput(
                    lotCurrentPrice.ifBlank {
                        position.pricePerUnit
                            ?.stripTrailingZeros()
                            ?.toPlainString()
                            .orEmpty()
                    },
                    { lotCurrentPrice = it },
                    "Current market price per ${asset.unit} · ${asset.quoteCurrency}",
                )
                lotError?.let { Text(it, color = tokens.expense, style = MzTypography.bodySmall) }
                MzButton(
                    if (editingOwnedLot) "Save lot changes" else "Save owned lot",
                    {
                        val quantity = runCatching { BigDecimal(lotQuantity) }.getOrNull()
                        val amount = runCatching { BigDecimal(lotAmountPaid) }.getOrNull()
                        val unitCost = runCatching { BigDecimal(lotUnitCost) }.getOrNull()
                        val currentPrice =
                            runCatching {
                                BigDecimal(lotCurrentPrice.ifBlank { position.pricePerUnit?.toPlainString().orEmpty() })
                            }.getOrNull()
                        val date = runCatching { LocalDate.parse(lotDate) }.getOrNull()
                        if (quantity == null || quantity <= BigDecimal.ZERO || amount == null || amount < BigDecimal.ZERO ||
                            unitCost == null || unitCost <= BigDecimal.ZERO || currentPrice == null || currentPrice <= BigDecimal.ZERO ||
                            date == null
                        ) {
                            lotError = "Enter a positive quantity and prices, a valid amount paid, and a valid date."
                        } else {
                            lotError = null
                            val totalPaidMinor =
                                runCatching {
                                    amount.movePointRight(currencyDecimals).setScale(0, RoundingMode.HALF_UP).longValueExact()
                                }.getOrNull()
                            if (totalPaidMinor == null) {
                                lotError = "The amount paid is too large."
                            } else {
                                scope.launch {
                                    runCatching {
                                        val editingId = editingOwnedLotId
                                        if (editingId != null) {
                                            repository.updateOwnedAssetHolding(
                                                editingId,
                                                date,
                                                quantity,
                                                unitCost,
                                                currentPrice,
                                                totalPaidMinor,
                                            )
                                        } else {
                                            repository.recordOwnedAssetHolding(
                                                assetId,
                                                date,
                                                quantity,
                                                unitCost,
                                                currentPrice,
                                                totalPaidMinor,
                                            )
                                        }
                                        addOwnedLot = false
                                        editingOwnedLotId = null
                                        lotQuantity = ""
                                        lotAmountPaid = ""
                                        lotUnitCost = ""
                                        lotCurrentPrice = ""
                                    }.onFailure { lotError = it.message ?: "Could not save the owned lot." }
                                }
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (editAsset) {
        val selectedEditType = runCatching { AssetType.valueOf(editType) }.getOrDefault(AssetType.OTHER)
        val quoteOptions = currencies.filter { it.enabled || it.code == editQuoteCurrency }
        ModalBottomSheet(
            onDismissRequest = { editAsset = false },
            containerColor = LocalMzTokens.current.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MzSpacing.sideMargin)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Edit holding", style = MzTypography.title)
                Text(
                    "Type, currency, and unit changes also affect how saved lots are interpreted.",
                    style = MzTypography.bodySmall,
                    color = tokens.warning,
                )
                Text("Type", style = MzTypography.bodySmall)
                AssetType.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            MzChip(
                                option.name.lowercase().replaceFirstChar(Char::uppercase),
                                selectedEditType == option,
                                {
                                    editType = option.name
                                    if (option == AssetType.GOLD) {
                                        if (editIdentifier !in listOf("24K", "21K", "18K")) editIdentifier = "21K"
                                        if (editGoldForm !in listOf("GRAM", "BAR", "COIN")) editGoldForm = "GRAM"
                                        editPriceMode = if (editIdentifier == "21K") "AUTO" else "DERIVED"
                                    } else {
                                        if (selectedEditType == AssetType.GOLD) editIdentifier = ""
                                        editPriceMode = "MANUAL"
                                    }
                                },
                            )
                        }
                    }
                }
                MzInput(editName, { editName = it }, "Name")
                if (selectedEditType == AssetType.GOLD) {
                    Text("Gold form", style = MzTypography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GRAM", "BAR", "COIN").forEach { form ->
                            MzChip(form.lowercase().replaceFirstChar(Char::uppercase), editGoldForm == form, {
                                editGoldForm = form
                                if (form == "COIN") {
                                    editIdentifier = "21K"
                                    editPriceMode = "AUTO"
                                }
                            })
                        }
                    }
                    Text("Karat", style = MzTypography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("24K", "21K", "18K").forEach { karat ->
                            MzChip(karat, editIdentifier == karat, {
                                editIdentifier = karat
                                if (editGoldForm == "COIN" && karat != "21K") editGoldForm = "GRAM"
                                editPriceMode = if (karat == "21K") "AUTO" else "DERIVED"
                            })
                        }
                    }
                } else {
                    MzInput(
                        editIdentifier,
                        { editIdentifier = it.uppercase() },
                        if (selectedEditType == AssetType.STOCK) "EGX ticker" else "Symbol or identifier",
                    )
                }
                Text("Price mode", style = MzTypography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedEditType == AssetType.GOLD) {
                        MzChip("Auto", editPriceMode == "AUTO", { editPriceMode = "AUTO" })
                        MzChip("Manual", editPriceMode == "MANUAL", { editPriceMode = "MANUAL" })
                        if (editIdentifier != "21K" && editGoldForm != "COIN") {
                            MzChip("Derived", editPriceMode == "DERIVED", { editPriceMode = "DERIVED" })
                        }
                    } else {
                        MzChip("Manual", true, { editPriceMode = "MANUAL" })
                    }
                }
                var currencyMenuExpanded by remember(editAsset) { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    MzButton(
                        "Quote currency · $editQuoteCurrency",
                        { currencyMenuExpanded = true },
                        Modifier.fillMaxWidth(),
                        MzButtonStyle.SECONDARY,
                    )
                    DropdownMenu(currencyMenuExpanded, { currencyMenuExpanded = false }) {
                        quoteOptions.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${currency.code} · ${currency.name}") },
                                onClick = {
                                    editQuoteCurrency = currency.code
                                    currencyMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                MzInput(editResaleDeduction, { editResaleDeduction = it }, "Resale deduction · %")
                editError?.let { Text(it, color = tokens.expense, style = MzTypography.bodySmall) }
                MzButton(
                    "Save changes",
                    {
                        val deduction = runCatching { BigDecimal(editResaleDeduction) }.getOrNull()
                        val enabledCurrency = currencies.any { it.enabled && it.code == editQuoteCurrency }
                        if (editName.isBlank() || editIdentifier.isBlank() || !enabledCurrency || deduction == null ||
                            deduction < BigDecimal.ZERO || deduction > BigDecimal(100)
                        ) {
                            editError = "Enter a name and identifier, choose an enabled currency, and set deduction from 0 to 100%."
                        } else {
                            scope.launch {
                                runCatching {
                                    repository.saveAsset(
                                        Asset(
                                            id = asset.id,
                                            name = editName.trim(),
                                            type = selectedEditType,
                                            symbolOrKarats = editIdentifier.trim(),
                                            quoteCurrency = editQuoteCurrency,
                                            unit =
                                                when (selectedEditType) {
                                                    AssetType.GOLD -> if (editGoldForm == "COIN") "coin" else "g"
                                                    AssetType.STOCK -> "share"
                                                    else -> "unit"
                                                },
                                            resaleDeductionPct = deduction,
                                            archived = asset.archived,
                                            goldForm = editGoldForm.takeIf { selectedEditType == AssetType.GOLD },
                                            priceMode = editPriceMode,
                                        ),
                                    )
                                    editAsset = false
                                }.onFailure { editError = it.message ?: "Could not update this holding." }
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
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
    editable: Boolean,
    onEdit: () -> Unit,
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
            if (editable) MzButton("Edit", onEdit, style = MzButtonStyle.GHOST)
            AmountText(trade.totalMinor, currency, hideAmounts = hideAmounts, textStyle = MzTypography.amountSmall)
        }
    }
}

private fun assetPriceKey(asset: app.mizan.data.db.AssetEntity): String =
    when {
        asset.type != "GOLD" -> "MANUAL:${asset.id}"
        asset.goldForm == "COIN" -> "GOLD_POUND"
        else -> "GOLD_${asset.symbolOrKarats.uppercase()}"
    }

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
