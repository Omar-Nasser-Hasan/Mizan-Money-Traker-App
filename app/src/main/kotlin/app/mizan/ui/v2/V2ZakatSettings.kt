// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.DebtBalance
import app.mizan.domain.zakat.ZakatCalculator
import app.mizan.domain.zakat.ZakatInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class P13ZakatModel(
    val cash: Long,
    val cards: Long,
    val receivables: Long,
    val debts: Long,
    val gold24: Long,
    val holdings: List<V2WealthPosition>,
    val incomplete: Boolean,
)

@Composable
fun V2ZakatScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onPay: () -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val balances by repository.observeAllAccountBalances().collectAsStateWithLifecycle(emptyList())
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val assets by repository.observeAssets().collectAsStateWithLifecycle(emptyList())
    val trades by repository.observeAllAssetTransactions().collectAsStateWithLifecycle(emptyList())
    val prices by repository.observePriceHistory().collectAsStateWithLifecycle(emptyList())
    val fx by repository.observeFxHistory().collectAsStateWithLifecycle(emptyList())
    val debts by repository.observeDebts().collectAsStateWithLifecycle(emptyList())
    val repayments by repository.observeDebtRepaymentTotals().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val inputs = listOf(accounts, balances, currencies, assets, trades, prices, fx, debts, repayments)
    val model by produceState<P13ZakatModel?>(null, inputs) {
        value =
            withContext(Dispatchers.Default) {
                val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
                val positions =
                    V2WealthModels
                        .build(
                            accounts,
                            balances,
                            currencies,
                            assets,
                            trades,
                            prices,
                            fx,
                            emptyList(),
                            Instant.now(),
                        ).positions
                val kinds = accounts.associate { it.id to it.type }
                val byAccount =
                    balances.groupBy { it.accountId }.mapValues { (_, rows) ->
                        rows.sumOf {
                            MoneyMath.baseMinor(Math.addExact(it.openingMinor, it.entryMinor), it.decimals, BigDecimal(it.egpPerUnit))
                        }
                    }
                val cash =
                    byAccount.filterKeys { kinds[it] != "CREDIT_CARD" }.values.sum() +
                        byAccount.filterKeys { kinds[it] == "CREDIT_CARD" }.values.sumOf { it.coerceAtLeast(0) }
                val cards = byAccount.filterKeys { kinds[it] == "CREDIT_CARD" }.values.sumOf { (-it).coerceAtLeast(0) }
                val paid = repayments.associate { it.debtId to it.repaidMinor }
                var incomplete = false

                fun debtValue(debt: app.mizan.data.db.DebtEntity): Long {
                    val rate =
                        if (debt.currency == "EGP") {
                            BigDecimal.ONE
                        } else {
                            fx
                                .filter { it.currency == debt.currency }
                                .maxByOrNull { it.date }
                                ?.egpPerUnit
                                ?.toBigDecimal()
                        }
                    if (rate == null) {
                        incomplete = true
                        return 0
                    }
                    val decimals = currencies.first { it.code == debt.currency }.decimals
                    return MoneyMath.baseMinor(
                        DebtBalance(
                            debt.principalMinor,
                            paid[debt.id] ?: 0,
                        ).outstandingMinor,
                        decimals,
                        rate,
                    )
                }
                val receivables = debts.filter { it.direction == "LENT" && it.status == "ACTIVE" }.sumOf(::debtValue)
                val liabilities =
                    debts
                        .filter {
                            it.direction == "BORROWED" && it.status == "ACTIVE" &&
                                (it.dueDate == null || LocalDate.parse(it.dueDate) <= today.plusYears(1))
                        }.sumOf(::debtValue)
                val gold =
                    prices
                        .filter { it.key == "GOLD_24K" }
                        .maxByOrNull { it.asOf }
                        ?.price
                        ?.toBigDecimal()
                if (gold == null) incomplete = true
                P13ZakatModel(
                    cash,
                    cards,
                    receivables,
                    liabilities,
                    gold?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact() ?: 0,
                    positions,
                    incomplete,
                )
            }
    }
    V2ZakatContent(model, hideAmounts, { month, day -> scope.launch { repository.setZakatAnniversary(month, day) } }, onPay)
}

@Composable
fun V2ZakatContent(
    model: P13ZakatModel?,
    hideAmounts: Boolean,
    onReminder: (Int, Int) -> Unit,
    onPay: () -> Unit,
) {
    var excluded by remember { mutableStateOf(emptySet<String>()) }
    var receivables by remember { mutableStateOf(false) }
    var cashEdit by remember { mutableStateOf<String?>(null) }
    var debtEdit by remember { mutableStateOf<String?>(null) }
    var cardEdit by remember { mutableStateOf<String?>(null) }
    var priceEdit by remember { mutableStateOf<String?>(null) }
    var grams by remember { mutableStateOf("85") }
    var month by remember { mutableStateOf("9") }
    var day by remember { mutableStateOf("1") }
    var reminderSaved by remember { mutableStateOf(false) }
    val result =
        remember(model, excluded, receivables, cashEdit, debtEdit, cardEdit, priceEdit, grams) {
            model?.let { value ->
                runCatching {
                    fun minor(
                        edit: String?,
                        fallback: Long,
                    ): Long =
                        edit
                            ?.toBigDecimal()
                            ?.movePointRight(2)
                            ?.setScale(0, RoundingMode.HALF_UP)
                            ?.longValueExact() ?: fallback
                    val included = value.holdings.filter { it.asset.id !in excluded }
                    require(minor(priceEdit, value.gold24) > 0) { "Enter a dealer gold price" }
                    ZakatCalculator.calculate(
                        ZakatInput(
                            minor(cashEdit, value.cash),
                            included.filter { it.asset.type == "GOLD" }.sumOf { it.valueEgpMinor },
                            included.filter { it.asset.type != "GOLD" }.sumOf { it.valueEgpMinor },
                            if (receivables) value.receivables else 0,
                            minor(debtEdit, value.debts),
                            minor(cardEdit, value.cards),
                            minor(priceEdit, value.gold24),
                            grams.toBigDecimal(),
                        ),
                    )
                }.getOrNull()
            }
        }
    V2SettingsList {
        if (model == null) {
            items(4) { MzSkeleton(Modifier, 80.dp) }
        } else {
            item { Text(stringResource(R.string.p13_zakat_disclaimer), color = LocalMzTokens.current.text) }
            if (model.incomplete) {
                item {
                    Text(
                        stringResource(R.string.p13_zakat_incomplete),
                        color = LocalMzTokens.current.expense,
                    )
                }
            }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        P13ZakatField(R.string.p13_zakat_cash, cashEdit, model.cash, hideAmounts) { cashEdit = it }
                        P13ZakatField(R.string.p13_zakat_debts, debtEdit, model.debts, hideAmounts) { debtEdit = it }
                        P13ZakatField(R.string.p13_zakat_cards, cardEdit, model.cards, hideAmounts) { cardEdit = it }
                        P13ZakatField(R.string.p13_zakat_gold_rate, priceEdit, model.gold24, hideAmounts) { priceEdit = it }
                        OutlinedTextField(grams, { grams = it }, label = { Text(stringResource(R.string.p13_nisab_grams)) })
                        MzToggle(receivables, { receivables = it }, stringResource(R.string.p13_include_receivables))
                        AmountText(model.receivables, "EGP", hideAmounts = hideAmounts)
                    }
                }
            }
            items(model.holdings, key = { it.asset.id }) { position ->
                MzCard {
                    Column(Modifier.padding(16.dp)) {
                        MzToggle(position.asset.id !in excluded, {
                            excluded = if (it) excluded - position.asset.id else excluded + position.asset.id
                        }, position.asset.name)
                        AmountText(position.valueEgpMinor, "EGP", hideAmounts = hideAmounts)
                        if (position.stale) Text(stringResource(R.string.p13_zakat_stale))
                    }
                }
            }
            result?.let { calculated ->
                item {
                    MzCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.p13_eligible))
                            AmountText(calculated.eligibleMinor, "EGP", hideAmounts = hideAmounts)
                            Text(stringResource(R.string.p13_nisab))
                            AmountText(calculated.nisabMinor, "EGP", hideAmounts = hideAmounts)
                            Text(stringResource(R.string.p13_zakat_due))
                            AmountText(calculated.dueMinor, "EGP", hideAmounts = hideAmounts)
                            MzButton(stringResource(R.string.p13_log_zakat), onPay)
                        }
                    }
                }
            }
            if (result == null) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
            item {
                MzCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.p13_hijri_reminder))
                        OutlinedTextField(
                            month,
                            {
                                month = it
                                reminderSaved = false
                            },
                            label = { Text(stringResource(R.string.p13_hijri_month)) },
                        )
                        OutlinedTextField(
                            day,
                            {
                                day = it
                                reminderSaved = false
                            },
                            label = { Text(stringResource(R.string.p13_hijri_day)) },
                        )
                        MzButton(stringResource(R.string.p13_save), {
                            onReminder(month.toInt(), day.toInt())
                            reminderSaved = true
                        }, enabled = month.toIntOrNull() in 1..12 && day.toIntOrNull() in 1..30)
                        if (reminderSaved) Text(stringResource(R.string.p13_reminder_saved))
                    }
                }
            }
        }
    }
}

@Composable
private fun P13ZakatField(
    label: Int,
    edit: String?,
    default: Long,
    hideAmounts: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        edit ?: BigDecimal.valueOf(default, 2).toPlainString(),
        onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation =
            if (hideAmounts) {
                androidx.compose.ui.text.input
                    .PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
    )
}
