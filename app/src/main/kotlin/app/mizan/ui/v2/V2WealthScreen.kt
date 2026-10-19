// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.db.AccountEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.AssetType
import app.mizan.domain.money.MoneyMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@Composable
@Suppress("ktlint:standard:function-naming")
fun V2WealthScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onOpenAccount: (String) -> Unit,
    onOpenHolding: (String) -> Unit,
    onUpdatePrices: () -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember(repository) { repository.observeAllAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(repository) { repository.observeAssets() }.collectAsStateWithLifecycle(emptyList())
    val trades by remember(repository) { repository.observeAllAssetTransactions() }.collectAsStateWithLifecycle(emptyList())
    val prices by remember(repository) { repository.observePriceHistory() }.collectAsStateWithLifecycle(emptyList())
    val fxHistory by remember(repository) { repository.observeFxHistory() }.collectAsStateWithLifecycle(emptyList())
    val statsEntries by remember(repository) { repository.observeStatsEntries() }.collectAsStateWithLifecycle(emptyList())
    val debts by remember(repository) { repository.observeAllDebts() }.collectAsStateWithLifecycle(emptyList())
    val debtRepayments by remember(repository) { repository.observeDebtRepaymentRows() }.collectAsStateWithLifecycle(emptyList())
    val snapshotState by produceState<V2WealthSnapshot?>(
        null,
        accounts,
        balances,
        currencies,
        assets,
        trades,
        prices,
        fxHistory,
        statsEntries,
        debts,
        debtRepayments,
    ) {
        value =
            withContext(Dispatchers.Default) {
                V2WealthModels.build(
                    accounts,
                    balances,
                    currencies,
                    assets,
                    trades,
                    prices,
                    fxHistory,
                    statsEntries,
                    Instant.now(),
                    debts,
                    debtRepayments,
                )
            }
    }
    val snapshot =
        snapshotState ?: run {
            MzSkeleton(Modifier.fillMaxWidth().height(220.dp))
            return
        }
    val chips = listOf("All", "Gold", "Stocks", "Funds", "Other")
    val selected =
        androidx.compose.runtime.saveable
            .rememberSaveable { androidx.compose.runtime.mutableStateOf("All") }
    val visible = snapshot.positions.filter { selected.value == "All" || it.asset.type == selected.value.uppercase().removeSuffix("S") }
    val tokens = LocalMzTokens.current
    val slices =
        buildList {
            if (snapshot.summary.cashEgpMinor > 0) add(MzDonutSlice("Cash", snapshot.summary.cashEgpMinor, Color(0xFF267EB8)))
            listOf(
                AssetType.GOLD to "Gold",
                AssetType.STOCK to "Stocks",
                AssetType.FUND to "Funds",
                AssetType.OTHER to "Other",
            ).forEach { (type, label) ->
                val amount = snapshot.summary.typeValuesEgpMinor[type] ?: 0L
                if (amount > 0) add(MzDonutSlice(label, amount, if (type == AssetType.GOLD) tokens.champagne else tokens.investment))
            }
        }
    val accountMap = accounts.associateBy { it.id }
    val valuesByAccount =
        balances.groupBy { it.accountId }.mapValues { (_, rows) ->
            rows.filter { accountMap[it.accountId]?.includeInTotals == true }.fold(0L) { sum, row ->
                Math.addExact(sum, MoneyMath.baseMinor(row.openingMinor + row.entryMinor, row.decimals, BigDecimal(row.egpPerUnit)))
            }
        }
    val physicalSourceTotals =
        listOf("CASH", "BANK", "CREDIT_CARD").associateWith { type ->
            balances
                .filter { accountMap[it.accountId]?.includeInTotals == true && accountMap[it.accountId]?.type == type }
                .fold(0L) { total, row ->
                    val value = MoneyMath.baseMinor(row.openingMinor + row.entryMinor, row.decimals, BigDecimal(row.egpPerUnit))
                    Math.addExact(total, if (type == "CREDIT_CARD") (-value).coerceAtLeast(0L) else value)
                }
        }
    val basisPoints = snapshot.summary.monthChange?.percentBasisPoints
    LazyColumn(
        modifier = Modifier.fillMaxSize().then(if (snapshot.positions.isNotEmpty()) Modifier.testTag("wealth-ready") else Modifier),
        contentPadding = PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("NET WORTH", style = MzTypography.labelCaps, color = LocalMzTokens.current.textDim)
                            if (basisPoints != null) {
                                Text(
                                    "${if (basisPoints >= 0) "+" else "−"}${BigDecimal.valueOf(
                                        kotlin.math.abs(basisPoints),
                                    ).movePointLeft(2).stripTrailingZeros().toPlainString()}% MoM",
                                    style = MzTypography.bodySmall,
                                    color =
                                        if (basisPoints >=
                                            0
                                        ) {
                                            tokens.income
                                        } else {
                                            tokens.expense
                                        },
                                )
                            }
                        }
                        AmountText(snapshot.summary.netWorthEgpMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.hero)
                        if (snapshot.summary.receivablesEgpMinor > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.p15_receivables), style = MzTypography.bodySmall)
                                AmountText(
                                    snapshot.summary.receivablesEgpMinor,
                                    "EGP",
                                    hideAmounts = hideAmounts,
                                    textStyle = MzTypography.amountSmall,
                                )
                            }
                        }
                        if (snapshot.summary.payablesEgpMinor > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.p15_payables), style = MzTypography.bodySmall)
                                AmountText(
                                    snapshot.summary.payablesEgpMinor,
                                    "EGP",
                                    hideAmounts = hideAmounts,
                                    textStyle = MzTypography.amountSmall,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = tokens.outline.copy(alpha = 0.35f))
                    Row(Modifier.fillMaxWidth()) {
                        WealthSplit("Cash", snapshot.summary.cashEgpMinor, Color(0xFF267EB8), hideAmounts, Modifier.weight(1f))
                        HorizontalDivider(Modifier.width(1.dp).height(58.dp), color = tokens.outline.copy(alpha = 0.35f))
                        WealthSplit("Holdings", snapshot.summary.holdingsEgpMinor, tokens.champagne, hideAmounts, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cash, bank and card", style = MzTypography.title)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WealthSplit("Cash", physicalSourceTotals["CASH"] ?: 0L, Color(0xFF267EB8), hideAmounts, Modifier.weight(1f))
                        WealthSplit("Bank", physicalSourceTotals["BANK"] ?: 0L, tokens.investment, hideAmounts, Modifier.weight(1f))
                        WealthSplit(
                            "Card owed",
                            physicalSourceTotals["CREDIT_CARD"] ?: 0L,
                            tokens.expense,
                            hideAmounts,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Asset allocation", style = MzTypography.title)
                        Text("LIVE SPLIT", style = MzTypography.labelCaps, color = tokens.textDim)
                    }
                    if (slices.isEmpty()) {
                        Text(
                            "Add a holding or fund an account to see your allocation.",
                            style = MzTypography.bodySmall,
                            color = tokens.textDim,
                        )
                    } else {
                        MzDonut(slices, centerText = "${snapshot.summary.investedBasisPoints / 100}%\nINVESTED")
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { label -> MzChip(label, selected.value == label, { selected.value = label }) }
            }
        }
        if (visible.isEmpty()) {
            item { MzEmptyState("No holdings yet", "Buy or add a holding to track its value here.", "Add holding", onUpdatePrices) }
        } else {
            items(visible, key = { it.asset.id }) { position ->
                HoldingOverviewRow(position, hideAmounts) { onOpenHolding(position.asset.id) }
            }
        }
        item { MzButton("Update prices", onUpdatePrices, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY) }
        item {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Accounts by type", style = MzTypography.title)
                        Text("${accounts.count { it.includeInTotals }} accounts", style = MzTypography.bodySmall, color = tokens.textDim)
                    }
                    snapshot.accountTypeTotals.forEach { (type, total) ->
                        val names = accounts.filter { it.includeInTotals && it.type == type }.joinToString(" · ") { it.name }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    type.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(names, style = MzTypography.bodySmall, color = tokens.textDim, maxLines = 1)
                            }
                            Spacer(Modifier.width(12.dp))
                            AmountText(total, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = tokens.outline.copy(alpha = 0.25f))
                    accounts.filter { it.includeInTotals }.forEach { account ->
                        WealthAccountRow(account, valuesByAccount[account.id] ?: 0L, hideAmounts) { onOpenAccount(account.id) }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun WealthSplit(
    label: String,
    amount: Long,
    dot: Color,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout
                .Box(Modifier.size(7.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
            Text(label.uppercase(), style = MzTypography.labelCaps, color = LocalMzTokens.current.textDim)
        }
        AmountText(amount, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.amountSmall)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun HoldingOverviewRow(
    position: V2WealthPosition,
    hideAmounts: Boolean,
    onClick: () -> Unit,
) {
    MzCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MzIcon(
                when (position.asset.type) {
                    "GOLD" -> "token"
                    "STOCK" -> "trending_up"
                    "FUND" -> "account_balance"
                    else -> "savings"
                },
                position.asset.name,
                tint = if (position.asset.type == "GOLD") LocalMzTokens.current.champagne else LocalMzTokens.current.investment,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(position.asset.name, style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                Text(
                    "${position.quantity.stripTrailingZeros().toPlainString()} ${position.asset.unit}",
                    style = MzTypography.bodySmall,
                    color = LocalMzTokens.current.textDim,
                )
                if (position.stale) {
                    Text(
                        "${position.ageHours} h old · stale",
                        style = MzTypography.bodySmall,
                        color = LocalMzTokens.current.warning,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AmountText(position.valueEgpMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.amountSmall)
                ProfitAmount(position.unrealizedEgpMinor, hideAmounts)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun WealthAccountRow(
    account: AccountEntity,
    valueEgpMinor: Long,
    hideAmounts: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(
                        8.dp,
                    ).background(
                        MzAccountColors.all[account.sortOrder.mod(MzAccountColors.all.size)],
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            )
            Column {
                Text(account.name, style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                Text(account.type.lowercase().replace('_', ' '), style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim)
            }
        }
        AmountText(valueEgpMinor, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.amountSmall)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ProfitAmount(
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
