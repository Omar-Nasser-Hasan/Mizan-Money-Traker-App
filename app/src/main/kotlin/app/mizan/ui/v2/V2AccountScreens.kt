// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length", "ktlint:standard:no-wildcard-imports")

package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.CreditCardBalance
import app.mizan.domain.plan.CreditMath
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

private val accountTypes = listOf("CASH", "BANK", "EWALLET", "SAVINGS", "INVESTMENT", "CREDIT_CARD", "OTHER")
private val accountPalette =
    listOf(
        "#3A924F",
        "#059093",
        "#0785DB",
        "#717BCB",
        "#9A6EC2",
        "#CE588B",
        "#C45AAC",
        "#DA535F",
        "#CA642F",
        "#AA7818",
        "#7A882F",
        "#188ABB",
    )

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun V2AccountDetailScreen(
    repository: MizanRepository,
    accountId: String,
    hideAmounts: Boolean,
    onNavigate: (V2Route) -> Unit,
    onBack: () -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val buckets by remember(repository, accountId) { repository.observeBuckets(accountId) }.collectAsStateWithLifecycle(emptyList())
    val fx by remember(repository) { repository.observeLatestFxRates() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val accountRecordsFlow = remember(accountId) { repository.observeRecentRecordsForAccount(accountId, 8) }
    val accountRecords by accountRecordsFlow.collectAsStateWithLifecycle(emptyList())
    val deltaFlow = remember(accountId) { repository.observeBucketDeltas(accountId) }
    val deltas by deltaFlow.collectAsStateWithLifecycle(emptyList())
    val dailyFlow =
        remember(accountId) {
            repository.observeDailyDeltas(accountId, LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).minusDays(29).toString())
        }
    val dailyDeltas by dailyFlow.collectAsStateWithLifecycle(emptyList())
    val account = accounts.firstOrNull { it.id == accountId }
    var fixOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val balances =
        buckets.map { bucket ->
            bucket to
                (bucket.openingMinor + (deltas.firstOrNull { it.currency == bucket.currency }?.amountMinor ?: 0L))
        }
    val totalEgp =
        balances.sumOf { (bucket, minor) ->
            val rate = fx.firstOrNull { it.currency == bucket.currency }?.egpPerUnit ?: if (bucket.currency == "EGP") "1" else null
            rate?.let { MoneyMath.baseMinor(minor, enabledCurrencyDecimals(currencies, bucket.currency), BigDecimal(it)) } ?: 0L
        }
    val card = account?.type == "CREDIT_CARD"
    val today = LocalDate.now(java.time.ZoneId.of("Africa/Cairo"))
    val rawTrendValues =
        remember(totalEgp, dailyDeltas, today) {
            val byDay = dailyDeltas.associate { it.date to it.baseMinor }
            val start = totalEgp - dailyDeltas.sumOf { it.baseMinor }
            (0L..29L).scan(start) { running, index -> running + (byDay[today.minusDays(29L - index).toString()] ?: 0L) }.drop(1)
        }
    val trendValues = remember(rawTrendValues, card) { if (card) rawTrendValues.map { -it } else rawTrendValues }
    val trendLow = trendValues.minOrNull() ?: totalEgp
    val trendRange = ((trendValues.maxOrNull() ?: totalEgp) - trendLow).coerceAtLeast(1L)
    val changePct =
        if (trendValues.firstOrNull()?.let { it != 0L } == true) {
            BigDecimal
                .valueOf(
                    trendValues.last() - trendValues.first(),
                ).multiply(
                    BigDecimal(100),
                ).divide(BigDecimal.valueOf(trendValues.first().absoluteValue), 0, java.math.RoundingMode.HALF_UP)
                .toPlainString()
        } else {
            "0"
        }
    val dueIn =
        account?.dueDay?.let { day ->
            val currentDue = today.withDayOfMonth(minOf(day, today.lengthOfMonth()))
            val nextDue =
                if (currentDue.isBefore(
                        today,
                    )
                ) {
                    today.plusMonths(1).withDayOfMonth(minOf(day, today.plusMonths(1).lengthOfMonth()))
                } else {
                    currentDue
                }
            ChronoUnit.DAYS.between(today, nextDue)
        }
    val currentUtil = account?.let { CreditMath.perCardUtilization(CreditCardBalance(it.creditLimitMinor ?: 0L, totalEgp)) }
    val dailyUtil =
        (0L..29L).map { offset ->
            val date = today.minusDays(29L - offset)
            val futureDelta =
                dailyDeltas
                    .filter {
                        runCatching {
                            LocalDate
                                .parse(
                                    it.date,
                                ).isAfter(date)
                        }.getOrDefault(false)
                    }.sumOf { it.baseMinor }
            account?.let { CreditMath.perCardUtilization(CreditCardBalance(it.creditLimitMinor ?: 0L, totalEgp - futureDelta)) }
        }
    val averageUtil = CreditMath.averageUtilization(dailyUtil) ?: currentUtil
    val peakUtil = CreditMath.maximumUtilization(dailyUtil) ?: currentUtil
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account?.name ?: "Account", style = MzTypography.title)
                    Text(
                        if (card) "Amount owed" else "Total at today's rates",
                        style = MzTypography.bodySmall,
                        color = LocalMzTokens.current.textDim,
                    )
                    AmountText(
                        if (card) (-totalEgp).coerceAtLeast(0L) else totalEgp,
                        "EGP",
                        hideAmounts = hideAmounts,
                        textStyle = MzTypography.hero,
                    )
                    if (card) {
                        val limit = account?.creditLimitMinor ?: 0L
                        val utilization = CreditMath.perCardUtilization(CreditCardBalance(limit, totalEgp)) ?: 0L
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Limit", style = MzTypography.bodySmall)
                            AmountText(limit, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
                            Text("Available", style = MzTypography.bodySmall)
                            AmountText(
                                (limit + totalEgp).coerceAtLeast(0L),
                                "EGP",
                                hideAmounts = hideAmounts,
                                textStyle = MzTypography.bodySmall,
                            )
                        }
                        Text(
                            if (hideAmounts) "Utilization · average ••• · peak •••" else "Utilization · average ${(averageUtil ?: utilization) / 100}% · peak ${(peakUtil ?: utilization) / 100}%",
                            style = MzTypography.bodySmall,
                        )
                        Text(
                            "Statement day ${account?.statementDay ?: "—"} · payment due day ${account?.dueDay ?: "—"}${dueIn?.let {
                                " · next payment due in $it days"
                            } ?: ""}",
                            style = MzTypography.bodySmall,
                        )
                    }
                }
            }
        }
        item(key = "trend") {
            MzCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (card) "Owed · last 30 days" else "Balance · last 30 days", style = MzTypography.title)
                    Row(
                        Modifier.fillMaxWidth().height(64.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        repeat(30) { index ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(
                                        (
                                            (8 + ((trendValues.getOrElse(index) { totalEgp } - trendLow) * 40 / trendRange).toInt()).dp
                                        ),
                                    ).background(LocalMzTokens.current.champagne, CircleShape),
                            )
                        }
                    }
                    Text(
                        if (hideAmounts) "30-day change · •••" else "30-day change · $changePct%",
                        style = MzTypography.bodySmall,
                        color = LocalMzTokens.current.textDim,
                    )
                }
            }
        }
        item(key = "bucket_heading") { Text(if (card) "Card buckets" else "Currencies", style = MzTypography.title) }
        items(balances, key = { it.first.currency }) { (bucket, minor) ->
            val rate = fx.firstOrNull { it.currency == bucket.currency }
            MzListRow(
                iconName = "currency_exchange",
                iconDescription = bucket.currency,
                title = bucket.currency,
                subtitle =
                    if (hideAmounts) {
                        "••••••"
                    } else {
                        "${formatAccountMinor(
                            minor,
                            enabledCurrencyDecimals(currencies, bucket.currency),
                        )} ${bucket.currency} · ${rate?.egpPerUnit?.let {
                            "≈ ${BigDecimal.valueOf(
                                minor,
                                enabledCurrencyDecimals(currencies, bucket.currency),
                            ).multiply(
                                BigDecimal(it),
                            ).setScale(2, java.math.RoundingMode.HALF_UP)} EGP"
                        } ?: "EGP value unavailable"}"
                    },
                trailing = {
                    Text(
                        rate?.date?.let { "Updated $it" } ?: if (bucket.currency == "EGP") "Base rate" else "Unavailable",
                        style = MzTypography.bodySmall,
                        color = LocalMzTokens.current.textDim,
                    )
                },
            )
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MzButton("Fix balance", { fixOpen = true }, Modifier.weight(1f), MzButtonStyle.SECONDARY)
                MzButton("Edit", { onNavigate(V2Route.EditAccount(accountId)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MzButton("See all", { onNavigate(V2Route.Records) }, Modifier.weight(1f), MzButtonStyle.SECONDARY)
                if (card) {
                    MzButton("Pay card", { onNavigate(V2Route.CardPayment(accountId)) }, Modifier.weight(1f))
                } else {
                    MzButton("Add record", { onNavigate(V2Route.ExpenseEntry) }, Modifier.weight(1f))
                }
            }
        }
        item(key = "recent_heading") { Text("Recent records", style = MzTypography.title) }
        items(accountRecords, key = {
            it.id
        }) { row ->
            MzListRow(
                iconName = "receipt_long",
                iconDescription = row.kind,
                title =
                    row.note.ifBlank {
                        row.kind
                            .replace('_', ' ')
                            .lowercase()
                            .replaceFirstChar(Char::uppercase)
                    },
                subtitle = "${row.bookedDate} · ${row.accountName}",
                modifier =
                    Modifier.clickable {
                        onNavigate(V2Route.RecordDetail(row.id))
                    },
                trailing = { AmountText(row.amountMinor, row.currency, hideAmounts = hideAmounts) },
            )
        }
    }
    if (fixOpen) {
        FixBalanceSheet(balances, currencies, hideAmounts, { currency, delta ->
            scope.launch {
                repository.postBalanceAdjustment(accountId, currency, delta)
                fixOpen =
                    false
            }
        }, { fixOpen = false })
    }
}

@Composable
fun V2WealthAccountsScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onOpenAccount: (String) -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val rows by remember(repository) { repository.observeAllAccountBalances() }.collectAsStateWithLifecycle(emptyList())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MzSpacing.sideMargin, 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Wealth", style = MzTypography.title) }
        item { Text("Accounts", style = MzTypography.bodySmall, color = LocalMzTokens.current.textDim) }
        items(accounts, key = { it.id }) { account ->
            val accountRows = rows.filter { it.accountId == account.id }
            val current = accountRows.sumOf { MoneyMath.baseMinor(it.openingMinor + it.entryMinor, it.decimals, BigDecimal(it.egpPerUnit)) }
            MzCard(Modifier.fillMaxWidth().clickable { onOpenAccount(account.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(account.name, style = MzTypography.title)
                        AmountText(current, "EGP", hideAmounts = hideAmounts)
                    }
                    accountRows.forEach { row ->
                        val amount = row.openingMinor + row.entryMinor
                        val value = MoneyMath.baseMinor(amount, row.decimals, BigDecimal(row.egpPerUnit))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.currency, style = MzTypography.bodySmall)
                            AmountText(amount, row.currency, hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
                            AmountText(value, "EGP", hideAmounts = hideAmounts, textStyle = MzTypography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun FixBalanceSheet(
    balances: List<Pair<app.mizan.data.db.AccountBucketEntity, Long>>,
    currencies: List<CurrencyEntity>,
    hideAmounts: Boolean,
    onSave: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var currency by remember {
        mutableStateOf(
            balances
                .firstOrNull()
                ?.first
                ?.currency
                .orEmpty(),
        )
    }
    var actual by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fix balance", style = MzTypography.title)
            balances.forEach { (bucket, balance) ->
                MzChip(
                    "${bucket.currency} · ${if (hideAmounts) {
                        "••••••"
                    } else {
                        formatAccountMinor(
                            balance,
                            enabledCurrencyDecimals(currencies, bucket.currency),
                        )
                    }}",
                    currency == bucket.currency,
                    { currency = bucket.currency },
                )
            }
            OutlinedTextField(actual, { actual = it }, label = { Text("Actual balance · $currency") }, modifier = Modifier.fillMaxWidth())
            val current = balances.firstOrNull { it.first.currency == currency }?.second ?: 0L
            val decimals = enabledCurrencyDecimals(currencies, currency)
            val delta =
                actual
                    .toBigDecimalOrNull()
                    ?.movePointRight(decimals)
                    ?.setScale(0, java.math.RoundingMode.HALF_UP)
                    ?.longValueExact()
                    ?.minus(current)
            MzButton(
                "Save adjustment",
                { delta?.let { onSave(currency, it) } },
                Modifier.fillMaxWidth(),
                enabled =
                    delta != null && delta != 0L,
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun V2AccountForm(
    repository: MizanRepository,
    accountId: String?,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val accounts by remember(repository) { repository.observeAccounts() }.collectAsStateWithLifecycle(emptyList())
    val currencies by remember(repository) { repository.observeCurrencies() }.collectAsStateWithLifecycle(emptyList())
    val existing = accounts.firstOrNull { it.id == accountId }
    val existingBuckets by remember(repository, accountId.orEmpty()) {
        repository.observeBuckets(accountId.orEmpty())
    }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var type by remember(existing) { mutableStateOf(existing?.type ?: "CASH") }
    var color by remember(existing) { mutableStateOf(existing?.color ?: accountPalette.first()) }
    var icon by remember(existing) { mutableStateOf(existing?.icon ?: "account_balance_wallet") }
    var include by remember(existing) { mutableStateOf(existing?.includeInTotals ?: true) }
    var openingDate by remember(existing) {
        mutableStateOf(existing?.openingDate ?: LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).toString())
    }
    var creditLimit by remember(existing) { mutableStateOf(existing?.creditLimitMinor?.let { formatAccountMinor(it, 2) } ?: "") }
    var statement by remember(existing) { mutableStateOf(existing?.statementDay?.toString() ?: "") }
    var due by remember(existing) { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    var bucketAmounts by remember(existingBuckets) {
        mutableStateOf(
            if (existingBuckets.isEmpty()) {
                mapOf("EGP" to "0")
            } else {
                existingBuckets.associate {
                    it.currency to formatAccountMinor(it.openingMinor, enabledCurrencyDecimals(currencies, it.currency))
                }
            },
        )
    }
    var showCurrencySheet by remember { mutableStateOf(false) }
    val enabled = currencies.filter { it.enabled }
    val firstCurrency = bucketAmounts.keys.firstOrNull() ?: "EGP"
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MzSpacing.sideMargin),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(if (existing == null) "New account" else "Edit account", style = MzTypography.title) }
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("Type", style = MzTypography.bodySmall) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accountTypes.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { value ->
                                MzChip(
                                    value.replace('_', ' '),
                                    type == value,
                                    { type = value },
                                )
                            }
                        }
                    }
                }
            }
            item { Text("Color", style = MzTypography.bodySmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accountPalette.take(6).forEach { swatch ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(android.graphics.Color.parseColor(swatch)), CircleShape)
                                .then(
                                    if (color ==
                                        swatch
                                    ) {
                                        Modifier.border(2.dp, LocalMzTokens.current.champagne, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ).clickable { color = swatch },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accountPalette.drop(6).forEach { swatch ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(android.graphics.Color.parseColor(swatch)), CircleShape)
                                .then(
                                    if (color ==
                                        swatch
                                    ) {
                                        Modifier.border(2.dp, LocalMzTokens.current.champagne, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ).clickable { color = swatch },
                        )
                    }
                }
            }
            item { Text("Icon emblem", style = MzTypography.bodySmall) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "account_balance_wallet",
                        "account_balance",
                        "savings",
                        "credit_card",
                        "payments",
                        "currency_exchange",
                    ).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { emblem ->
                                Surface(
                                    modifier = Modifier.size(48.dp).clickable { icon = emblem },
                                    shape = CircleShape,
                                    color = if (icon == emblem) LocalMzTokens.current.champagne else LocalMzTokens.current.raisedSurface,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MzIcon(
                                            emblem,
                                            "Select ${emblem.replace('_', ' ')} icon",
                                            tint =
                                                if (icon ==
                                                    emblem
                                                ) {
                                                    LocalMzTokens.current.onChampagne
                                                } else {
                                                    LocalMzTokens.current.textDim
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            items(bucketAmounts.toList(), key = { it.first }) { (code, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(code, Modifier.width(50.dp))
                    OutlinedTextField(amount, { value ->
                        bucketAmounts =
                            bucketAmounts + (code to value)
                    }, label = { Text("Opening balance") }, modifier = Modifier.weight(1f))
                }
            }
            item { MzButton("Add currency", { showCurrencySheet = true }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY) }
            item {
                OutlinedTextField(
                    openingDate,
                    { openingDate = it },
                    label = { Text("Opening date · YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { MzToggle(include, { include = it }, "Include in totals", Modifier.fillMaxWidth()) }
            if (type == "CREDIT_CARD") {
                item {
                    OutlinedTextField(
                        creditLimit,
                        { creditLimit = it },
                        label = { Text("Credit limit (EGP)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        statement,
                        { statement = it },
                        label = { Text("Statement day") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { OutlinedTextField(due, { due = it }, label = { Text("Payment due day") }, modifier = Modifier.fillMaxWidth()) }
            }
            item {
                MzButton(
                    "Save account",
                    {
                        scope.launch {
                            val bucketMap =
                                bucketAmounts.mapValues { (code, value) ->
                                    requireNotNull(accountMinorFromInput(value, enabledCurrencyDecimals(currencies, code)))
                                }
                            val savedId =
                                repository.saveAccount(
                                    id =
                                        accountId ?: java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                    name = name.trim(),
                                    type = type,
                                    color = color,
                                    icon = icon,
                                    currency = firstCurrency,
                                    openingMinor =
                                        bucketMap[firstCurrency] ?: 0L,
                                    includeInTotals = include,
                                    creditLimitMinor =
                                        if (type == "CREDIT_CARD") {
                                            accountMinorFromInput(creditLimit, 2)
                                        } else {
                                            null
                                        },
                                    statementDay = if (type == "CREDIT_CARD") statement.toIntOrNull() else null,
                                    dueDay = if (type == "CREDIT_CARD") due.toIntOrNull() else null,
                                    openingDate = openingDate,
                                    buckets = bucketMap,
                                )
                            onSaved(savedId)
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled =
                        name.isNotBlank() && bucketAmounts.isNotEmpty() && runCatching { LocalDate.parse(openingDate) }.isSuccess &&
                            bucketAmounts.all { (code, value) ->
                                accountMinorFromInput(value, enabledCurrencyDecimals(currencies, code)) !=
                                    null
                            } &&
                            (
                                type != "CREDIT_CARD" ||
                                    (
                                        (accountMinorFromInput(creditLimit, 2) ?: 0L) > 0L && statement.toIntOrNull() in 1..31 &&
                                            due.toIntOrNull() in 1..31
                                    )
                            ),
                )
            }
            if (existing !=
                null
            ) {
                item {
                    TextButton(onClick = {
                        scope.launch {
                            repository.archiveAccount(existing.id, true)
                            if (snackbar.showSnackbar("Account archived", "Undo") == SnackbarResult.ActionPerformed) {
                                repository.archiveAccount(existing.id, false)
                            } else {
                                onBack()
                            }
                        }
                    }) { Text("Archive account") }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (showCurrencySheet) {
        ModalBottomSheet(onDismissRequest = { showCurrencySheet = false }) {
            LazyColumn(contentPadding = PaddingValues(20.dp)) {
                item { Text("Add currency", style = MzTypography.title) }
                items(enabled.filter { it.code !in bucketAmounts }, key = CurrencyEntity::code) { currency ->
                    MzListRow(
                        "currency_exchange",
                        currency.code,
                        currency.code,
                        currency.name,
                        Modifier.clickable {
                            bucketAmounts =
                                bucketAmounts + (currency.code to "0")
                            ; showCurrencySheet = false
                        },
                    )
                }
            }
        }
    }
}

private fun enabledCurrencyDecimals(
    currencies: List<CurrencyEntity>,
    code: String,
): Int = currencies.firstOrNull { it.code == code }?.decimals ?: 2

private fun formatAccountMinor(
    minor: Long,
    decimals: Int,
): String = BigDecimal.valueOf(minor, decimals).toPlainString()

private fun accountMinorFromInput(
    value: String,
    decimals: Int,
): Long? =
    value
        .toBigDecimalOrNull()
        ?.let { amount ->
            runCatching {
                amount.movePointRight(decimals).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            }.getOrNull()
        }
