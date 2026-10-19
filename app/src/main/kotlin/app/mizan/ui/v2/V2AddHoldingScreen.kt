// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.Asset
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Composable
fun V2AddHoldingScreen(
    repository: MizanRepository,
    onSaved: () -> Unit,
) {
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val enabledCurrencies = currencies.filter { it.enabled }
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(AssetType.GOLD.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var identifier by rememberSaveable { mutableStateOf("21K") }
    var quoteCurrency by rememberSaveable { mutableStateOf("EGP") }
    var goldForm by rememberSaveable { mutableStateOf("GRAM") }
    var karat by rememberSaveable { mutableStateOf("21K") }
    var priceMode by rememberSaveable { mutableStateOf("AUTO") }
    var alreadyOwned by rememberSaveable { mutableStateOf(false) }
    var purchaseAlreadyRecorded by rememberSaveable { mutableStateOf(true) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var purchaseUnitPrice by rememberSaveable { mutableStateOf("") }
    var currentUnitPrice by rememberSaveable { mutableStateOf("") }
    var purchaseDate by rememberSaveable { mutableStateOf(LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).toString()) }
    var accountId by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedType = AssetType.valueOf(type)
    val decimals = currencies.firstOrNull { it.code == quoteCurrency }?.decimals ?: 2
    val cashAccounts =
        accounts.filter { account ->
            !account.archived && balances.any { it.accountId == account.id && it.currency == quoteCurrency }
        }
    LaunchedEffect(cashAccounts) { if (cashAccounts.none { it.id == accountId }) accountId = cashAccounts.firstOrNull()?.id.orEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add holding", style = MzTypography.title)
                    Text("Track gold, shares, funds, or another investment.", style = MzTypography.bodySmall)
                }
            }
        }
        item { Text("Type", style = MzTypography.bodySmall) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetType.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            MzChip(option.name.lowercase().replaceFirstChar(Char::uppercase), type == option.name, {
                                type = option.name
                                identifier = if (option == AssetType.GOLD) karat else ""
                                priceMode = if (option == AssetType.GOLD && karat != "21K") "DERIVED" else "MANUAL"
                            })
                        }
                    }
                }
            }
        }
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        if (selectedType == AssetType.GOLD) {
            item { Text("Gold form", style = MzTypography.bodySmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GRAM", "BAR", "COIN").forEach { form ->
                        MzChip(form.lowercase().replaceFirstChar(Char::uppercase), goldForm == form, {
                            goldForm = form
                            if (form == "COIN") {
                                karat = "21K"
                                identifier = "21K"
                                priceMode = "AUTO"
                            }
                        })
                    }
                }
            }
            item { Text("Karat", style = MzTypography.bodySmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("24K", "21K", "18K").forEach { value ->
                        MzChip(value, karat == value, {
                            karat = value
                            identifier = value
                            if (value != "21K" && goldForm == "COIN") goldForm = "GRAM"
                            priceMode = if (value == "21K") "AUTO" else "DERIVED"
                        })
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    identifier,
                    { identifier = it.uppercase() },
                    label = { Text(if (selectedType == AssetType.STOCK) "EGX ticker" else "Symbol or identifier") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Text(
                "Price mode · ${priceMode.lowercase().replaceFirstChar(Char::uppercase)}" +
                    if (priceMode == "AUTO") " · Daleelak gold prices" else "",
                style = MzTypography.bodySmall,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedType == AssetType.GOLD) {
                    MzChip("Auto", priceMode == "AUTO", { priceMode = "AUTO" })
                    MzChip("Manual", priceMode == "MANUAL", { priceMode = "MANUAL" })
                    if (karat != "21K") MzChip("Derived", true, { priceMode = "DERIVED" })
                } else {
                    MzChip("Manual", true, { priceMode = "MANUAL" })
                    Text(
                        "Automatic EGX and fund prices are unavailable; saved manual prices remain on screen.",
                        style = MzTypography.bodySmall,
                    )
                }
            }
        }
        item {
            var expanded by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                MzButton("Quote currency · $quoteCurrency", { expanded = true }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY)
                DropdownMenu(expanded, { expanded = false }) {
                    enabledCurrencies.forEach { currency ->
                        DropdownMenuItem(
                            text = { Text("${currency.code} · ${currency.name}") },
                            onClick = {
                                quoteCurrency = currency.code
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        item {
            MzToggle(alreadyOwned, { alreadyOwned = it }, "I already own this holding")
        }
        if (alreadyOwned) {
            item { Text("Acquisition details", style = MzTypography.labelCaps) }
            item {
                val unit =
                    when {
                        selectedType == AssetType.GOLD && goldForm == "COIN" -> "coin"
                        selectedType == AssetType.GOLD -> "g"
                        selectedType == AssetType.STOCK -> "shares"
                        else -> "units"
                    }
                MzInput(quantity, { quantity = it }, "Quantity ($unit)")
            }
            item { MzInput(purchaseUnitPrice, { purchaseUnitPrice = it }, "Purchase price per unit · $quoteCurrency") }
            item { MzInput(purchaseDate, { purchaseDate = it }, "Purchase date · YYYY-MM-DD") }
            item { MzInput(currentUnitPrice, { currentUnitPrice = it }, "Current market price per unit · $quoteCurrency") }
            item {
                MzToggle(purchaseAlreadyRecorded, { purchaseAlreadyRecorded = it }, "The purchase cash is already in my ledger")
            }
            if (!purchaseAlreadyRecorded) {
                item { Text("Purchase account", style = MzTypography.labelCaps) }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        cashAccounts.forEach { account ->
                            MzChip(account.name, account.id == accountId, { accountId = account.id })
                        }
                    }
                }
            }
        }
        error?.let { message -> item { Text(message, color = LocalMzTokens.current.expense) } }
        item {
            MzButton(
                "Save holding",
                {
                    val title = name.trim()
                    val symbol = if (selectedType == AssetType.GOLD) karat else identifier.trim()
                    val quantityValue = runCatching { BigDecimal(quantity) }.getOrNull()
                    val purchasePriceValue = runCatching { BigDecimal(purchaseUnitPrice) }.getOrNull()
                    val currentPriceValue = runCatching { BigDecimal(currentUnitPrice) }.getOrNull()
                    val parsedDate = runCatching { LocalDate.parse(purchaseDate) }.getOrNull()
                    if (title.isEmpty() || symbol.isEmpty() || quoteCurrency !in enabledCurrencies.map { it.code }) {
                        error = "Enter a name and identifier, and choose an enabled currency."
                    } else if (
                        alreadyOwned &&
                        (
                            quantityValue == null ||
                                quantityValue <= BigDecimal.ZERO ||
                                purchasePriceValue == null ||
                                purchasePriceValue <= BigDecimal.ZERO ||
                                currentPriceValue == null ||
                                currentPriceValue <= BigDecimal.ZERO ||
                                parsedDate == null
                        )
                    ) {
                        error = "Enter a positive quantity and prices, and a valid purchase date."
                    } else if (
                        alreadyOwned && !purchaseAlreadyRecorded && cashAccounts.none { it.id == accountId }
                    ) {
                        error = "Choose an account that holds $quoteCurrency."
                    } else {
                        error = null
                        scope.launch {
                            runCatching {
                                val id = UUID.randomUUID().toString()
                                repository.saveAsset(
                                    Asset(
                                        id = id,
                                        name = title,
                                        type = selectedType,
                                        symbolOrKarats = symbol,
                                        quoteCurrency = quoteCurrency,
                                        unit =
                                            when (selectedType) {
                                                AssetType.GOLD -> if (goldForm == "COIN") "coin" else "g"
                                                AssetType.STOCK -> "share"
                                                else -> "unit"
                                            },
                                        resaleDeductionPct = BigDecimal.ZERO,
                                        goldForm = goldForm.takeIf { selectedType == AssetType.GOLD },
                                        priceMode = priceMode,
                                    ),
                                )
                                if (alreadyOwned) {
                                    val quantityValue = requireNotNull(quantityValue)
                                    val purchasePriceValue = requireNotNull(purchasePriceValue)
                                    val currentPriceValue = requireNotNull(currentPriceValue)
                                    val dateValue = requireNotNull(parsedDate)
                                    if (purchaseAlreadyRecorded) {
                                        repository.recordOwnedAssetHolding(
                                            id,
                                            dateValue,
                                            quantityValue,
                                            purchasePriceValue,
                                            currentPriceValue,
                                        )
                                    } else {
                                        val totalMinor =
                                            quantityValue
                                                .multiply(purchasePriceValue)
                                                .movePointRight(decimals)
                                                .setScale(0, RoundingMode.HALF_UP)
                                                .longValueExact()
                                        val rate =
                                            requireNotNull(repository.effectiveRate(quoteCurrency, dateValue)) {
                                                "No booking rate for $quoteCurrency on $dateValue."
                                            }
                                        repository.postAssetTrade(
                                            AssetTransaction(
                                                UUID.randomUUID().toString(),
                                                id,
                                                AssetTransactionKind.BUY,
                                                dateValue,
                                                quantityValue,
                                                purchasePriceValue,
                                                0L,
                                                totalMinor,
                                                note = "Existing holding purchase",
                                            ),
                                            accountId,
                                            decimals,
                                            rate,
                                            currentPriceValue,
                                        )
                                    }
                                }
                                onSaved()
                            }.onFailure { failure -> error = failure.message ?: "Could not save the holding." }
                        }
                    }
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}
