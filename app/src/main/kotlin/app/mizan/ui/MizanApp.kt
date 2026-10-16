// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ledger.EntryCalculator
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeAllocation
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.SplitIncomeRequest
import app.mizan.domain.ledger.TransferRequest
import app.mizan.domain.ledger.TxnStatus
import app.mizan.domain.ports.RecordQuery
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val cairo = ZoneId.of("Africa/Cairo")
private val tabs = listOf("Home", "Records", "Stats", "Plan", "Wealth")
private val LocalHideAmounts = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mizanApp(repository: MizanRepository) {
    var tab by remember { mutableStateOf("Home") }
    var route by remember { mutableStateOf<String?>(null) }
    val initialAccounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val hideAmounts by repository.observeHideAmounts().collectAsStateWithLifecycle(false)
    var firstRunChecked by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(initialAccounts) {
        if (!firstRunChecked && initialAccounts.isEmpty()) route = "onboarding"
        if (initialAccounts.isNotEmpty()) firstRunChecked = true
    }
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalHideAmounts provides hideAmounts) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            route?.replaceFirstChar { it.uppercase() } ?: tab,
                            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { if (tab == "Wealth") route = "dev" }),
                        )
                    },
                    navigationIcon = { if (route != null) TextButton(onClick = { route = null }) { Text("Back") } },
                    actions = { if (route == null) TextButton(onClick = { route = "settings" }) { Text("Settings") } },
                )
            },
            bottomBar = {
                if (route == null) {
                    NavigationBar {
                        tabs.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(tabSymbol(item)) },
                                label = { Text(item) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (route == null &&
                    tab in setOf("Home", "Records", "Plan")
                ) {
                    FloatingActionButton(onClick = { route = "entry" }) { Text("+") }
                }
            },
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (route) {
                "entry" -> {
                    entryScreen(repository, modifier, onDone = { route = null })
                }

                "onboarding" -> {
                    onboardingScreen(repository, modifier, onDone = { route = null })
                }

                "settings" -> {
                    settingsScreen(repository, modifier, onOpen = { route = it })
                }

                "accounts" -> {
                    accountsScreen(repository, modifier, onDetail = { route = "account:$it" })
                }

                "currencies" -> {
                    currenciesScreen(repository, modifier)
                }

                "categories" -> {
                    referenceScreen(repository, "categories", modifier)
                }

                "labels" -> {
                    referenceScreen(repository, "labels", modifier)
                }

                "payees" -> {
                    referenceScreen(repository, "payees", modifier)
                }

                "battery-guide" -> {
                    hyperOsGuide(modifier)
                }

                "security" -> {
                    securityScreen(repository, modifier)
                }

                "trash" -> {
                    trashScreen(repository, modifier)
                }

                "backup" -> {
                    backupScreen(repository, modifier)
                }

                "dev" -> {
                    devScreen(repository, modifier)
                }

                else -> {
                    val currentRoute = route
                    if (currentRoute?.startsWith("account:") ==
                        true
                    ) {
                        accountDetail(repository, currentRoute.removePrefix("account:"), modifier)
                    } else {
                        tabScreen(repository, tab, modifier, onAccount = {
                            route =
                                "account:$it"
                        }, onEntry = { route = "entry" })
                    }
                }
            }
        }
    }
}

@Composable
private fun tabScreen(
    repository: MizanRepository,
    tab: String,
    modifier: Modifier,
    onAccount: (String) -> Unit,
    onEntry: () -> Unit,
) {
    when (tab) {
        "Home" -> homeScreen(repository, modifier, onAccount, onEntry)
        "Records" -> recordsScreen(repository, modifier)
        "Plan" -> planScreen(repository, modifier)
        "Stats" -> statsScreen(repository, modifier)
        else -> emptyScreen(tab, modifier)
    }
}

@Composable
private fun homeScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onAccount: (String) -> Unit,
    onEntry: () -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Accounts", style = MaterialTheme.typography.headlineMedium) }
        if (accounts.isEmpty()) item { emptyState("Create your first account to start recording.", "Create account", onEntry) }
        items(accounts, key = { it.id }) { account ->
            Card(onClick = { onAccount(account.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleLarge)
                    Text("${account.type.replace('_', ' ')} · ${account.defaultCurrency}")
                }
            }
        }
    }
}

@Composable
private fun recordsScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    var search by remember { mutableStateOf("") }
    val query = remember(search) { RecordQuery(search = search.takeIf { it.isNotBlank() }) }
    val records =
        remember(query) {
            Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) { repository.records(query) }.flow
        }.collectAsLazyPagingItems()
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(search, { search = it }, label = { Text("Search note or payee") }, modifier = Modifier.fillMaxWidth()) }
        items(records.itemCount, key = { index -> records[index]?.id ?: index }) { index ->
            records[index]?.let { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.bookedDate, style = MaterialTheme.typography.labelMedium)
                        Text(row.categoryName ?: row.kind, style = MaterialTheme.typography.titleMedium)
                        Text("${row.accountName} · ${row.currency}")
                        amountText(row.amountMinor, row.currency)
                    }
                }
            }
        }
    }
}

@Composable
private fun entryScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    var kind by remember { mutableStateOf("EXPENSE") }
    val categories by repository.observeCategories(if (kind == "INCOME") "INCOME" else "EXPENSE").collectAsStateWithLifecycle(emptyList())
    val incomeSources by repository.observeIncomeSources().collectAsStateWithLifecycle(emptyList())
    val splitTemplates by repository.observeSplitTemplates().collectAsStateWithLifecycle(emptyList())
    var selectedSplitTemplate by remember { mutableStateOf<String?>(null) }
    val splitLines by repository.observeSplitLines(selectedSplitTemplate ?: "").collectAsStateWithLifecycle(emptyList())
    val recordTemplates by repository.observeRecordTemplates(kind).collectAsStateWithLifecycle(emptyList())
    var splitAllocations by remember { mutableStateOf<List<IncomeAllocation>>(emptyList()) }
    var amount by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: MizanRepository.UNCATEGORIZED_ID) }
    var message by remember { mutableStateOf("") }
    val account = accounts.firstOrNull { it.id == selectedAccount }
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EXPENSE", "INCOME", "TRANSFER").forEach { option ->
                    FilterChip(
                        selected =
                            kind == option,
                        onClick = { kind = option },
                        label = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
        if (recordTemplates.isNotEmpty()) {
            item { Text("One-tap presets") }
            items(recordTemplates, key = { it.id }) { preset ->
                FilterChip(
                    selected = false,
                    onClick = {
                        preset.amountMinor?.let { amount = BigDecimal.valueOf(it).movePointLeft(2).toPlainString() }
                        preset.accountId?.let { selectedAccount = it }
                        preset.categoryId?.let { selectedCategory = it }
                        selectedSplitTemplate = preset.splitTemplateId
                    },
                    label = { Text(preset.name) },
                )
            }
        }
        item { amountText(amountToMinor(amount, 2), account?.defaultCurrency ?: "EGP", big = true) }
        item { calculatorKeypad(amount, { amount = it }) }
        item { Text("Account") }
        items(accounts, key = { it.id }) { candidate ->
            FilterChip(selected = selectedAccount == candidate.id, onClick = {
                selectedAccount =
                    candidate.id
            }, label = { Text(candidate.name) })
        }
        if (kind != "TRANSFER") {
            item { Text("Category") }
            items(categories, key = { it.id }) { category ->
                FilterChip(selected = selectedCategory == category.id, onClick = {
                    selectedCategory =
                        category.id
                }, label = { Text(category.name) })
            }
        }
        if (kind == "INCOME") {
            item { Text("Income source") }
            items(incomeSources, key = { it.id }) { source ->
                FilterChip(
                    selected = source.defaultTemplateId == selectedSplitTemplate,
                    onClick = {
                        selectedSplitTemplate = source.defaultTemplateId
                        source.defaultCategoryId?.let { selectedCategory = it }
                    },
                    label = { Text(source.name) },
                )
            }
            item {
                incomeSplitSheet(
                    amountMinor = amountToMinor(amount, 2),
                    accounts = accounts.filter { it.defaultCurrency == account?.defaultCurrency },
                    templates = splitTemplates,
                    selectedTemplateId = selectedSplitTemplate,
                    templateLines = splitLines,
                    onTemplateSelected = { selectedSplitTemplate = it },
                    onAllocationsChanged = { splitAllocations = it },
                )
            }
        }
        if (kind != "TRANSFER") {
            item {
                TextButton(
                    onClick = {
                        val current = account ?: return@TextButton
                        scope.launch {
                            repository.saveRecordTemplate(
                                RecordTemplateEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = "${kind.lowercase().replaceFirstChar { it.uppercase() }} preset",
                                    kind = kind,
                                    accountId = current.id,
                                    currency = current.defaultCurrency,
                                    amountMinor = amountToMinor(amount, 2).takeIf { it > 0 },
                                    categoryId = selectedCategory,
                                    splitTemplateId = selectedSplitTemplate,
                                ),
                            )
                            message = "Preset saved"
                        }
                    },
                ) { Text("Save as preset") }
            }
        }
        item {
            Button(onClick = {
                val current = account
                if (current == null) {
                    message = "Create an account first."
                } else {
                    scope.launch {
                        val minor = amountToMinor(amount, 2)
                        val now = Instant.now()
                        val date = LocalDate.now(cairo)
                        val service = LedgerService()
                        val posting =
                            when (kind) {
                                "INCOME" -> {
                                    if (splitAllocations.isNotEmpty()) {
                                        service.postSplitIncome(
                                            SplitIncomeRequest(
                                                splitAllocations,
                                                current.defaultCurrency,
                                                2,
                                                BigDecimal.ONE,
                                                now,
                                                date,
                                                selectedCategory,
                                            ),
                                        )
                                    } else {
                                        service.postIncome(
                                            IncomeRequest(
                                                current.id,
                                                current.defaultCurrency,
                                                minor,
                                                2,
                                                BigDecimal.ONE,
                                                now,
                                                date,
                                                selectedCategory,
                                            ),
                                        )
                                    }
                                }

                                "TRANSFER" -> {
                                    val target = accounts.firstOrNull { it.id != current.id }
                                    if (target ==
                                        null
                                    ) {
                                        null
                                    } else {
                                        service.postTransfer(
                                            TransferRequest(
                                                current.id,
                                                current.defaultCurrency,
                                                minor,
                                                2,
                                                BigDecimal.ONE,
                                                target.id,
                                                target.defaultCurrency,
                                                minor,
                                                2,
                                                BigDecimal.ONE,
                                                now,
                                                date,
                                            ),
                                        )
                                    }
                                }

                                else -> {
                                    service.postExpense(
                                        ExpenseRequest(
                                            current.id,
                                            current.defaultCurrency,
                                            minor,
                                            2,
                                            BigDecimal.ONE,
                                            selectedCategory,
                                            now,
                                            date,
                                        ),
                                    )
                                }
                            }
                        if (posting == null) {
                            message = "Transfers need a second account."
                        } else {
                            repository.post(posting)
                            message = "Saved"
                        }
                    }
                }
            }, enabled = amountToMinor(amount, 2) > 0 && account != null, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
        if (message.isNotBlank()) item { Text(message) }
    }
}

@Composable
private fun calculatorKeypad(
    value: String,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("123", "456", "789", ".0⌫").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    Button(onClick = {
                        onChange(
                            if (key ==
                                '⌫'
                            ) {
                                value.dropLast(1)
                            } else {
                                value + key
                            },
                        )
                    }, modifier = Modifier.weight(1f)) { Text(key.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("÷", "×", "−", "+", "=").forEach { key ->
                Button(
                    onClick = {
                        if (key == "=") {
                            runCatching { EntryCalculator.evaluate(value).stripTrailingZeros().toPlainString() }.onSuccess(onChange)
                        } else {
                            onChange(value + key)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(key) }
            }
        }
    }
}

@Composable
private fun settingsScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onOpen: (String) -> Unit,
) {
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        listOf(
            "accounts" to "Accounts",
            "currencies" to "Currencies and rates",
            "categories" to "Categories",
            "labels" to "Labels",
            "payees" to "Payees",
            "security" to "Security",
            "trash" to "Trash",
            "backup" to "Backup and restore",
            "battery-guide" to "HyperOS battery and Autostart guide",
        ).forEach { (route, label) ->
            item { Button(onClick = { onOpen(route) }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
        }
        item { Text("Rates By Exchange Rate API", color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun accountsScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onDetail: (String) -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CASH") }
    var currency by remember { mutableStateOf("EGP") }
    var opening by remember { mutableStateOf("0") }
    var creditLimit by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("1") }
    var dueDay by remember { mutableStateOf("1") }
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(name, { name = it }, label = { Text("New account name") }, modifier = Modifier.fillMaxWidth()) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("CASH", "BANK", "EWALLET", "SAVINGS", "CREDIT_CARD", "INVESTMENT", "OTHER")) { option ->
                    FilterChip(type == option, { type = option }, label = { Text(option.replace('_', ' ')) })
                }
            }
        }
        item {
            OutlinedTextField(currency, {
                currency = it.uppercase().take(3)
            }, label = { Text("Default currency") }, modifier = Modifier.fillMaxWidth())
        }
        item { OutlinedTextField(opening, { opening = it }, label = { Text("Opening balance") }, modifier = Modifier.fillMaxWidth()) }
        if (type == "CREDIT_CARD") {
            item {
                OutlinedTextField(
                    creditLimit,
                    { creditLimit = it },
                    label = { Text("Credit limit") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(statementDay, {
                    statementDay = it.filter(Char::isDigit).take(2)
                }, label = { Text("Statement day (1–31)") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(dueDay, {
                    dueDay = it.filter(Char::isDigit).take(2)
                }, label = { Text("Due day (1–31)") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(onClick = {
                scope.launch {
                    if (name.isNotBlank()) {
                        repository.saveAccount(
                            name = name,
                            type = type,
                            color = "#F2B84B",
                            icon = "wallet",
                            currency = currency.ifBlank { "EGP" },
                            openingMinor =
                                amountToMinor(
                                    opening,
                                    if (currency == "KWD") {
                                        3
                                    } else if (currency == "JPY") {
                                        0
                                    } else {
                                        2
                                    },
                                ),
                            creditLimitMinor = creditLimit.takeIf { type == "CREDIT_CARD" }?.let { amountToMinor(it, 2) },
                            statementDay = statementDay.toIntOrNull()?.takeIf { it in 1..31 },
                            dueDay = dueDay.toIntOrNull()?.takeIf { it in 1..31 },
                        )
                        name =
                            ""
                    }
                }
            }) { Text("Add account") }
        }
        items(accounts, key = { it.id }) { account ->
            Button(onClick = { onDetail(account.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("${account.name} · ${account.type.replace('_', ' ')}")
            }
        }
    }
}

@Composable
private fun currenciesScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val currencies by repository.observeCurrencies().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Currencies and rates", style = MaterialTheme.typography.headlineMedium) }
        items(currencies, key = { it.code }) { currency ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${currency.code} · ${currency.name}")
                        Text("${currency.decimals} decimal places")
                    }
                    TextButton(onClick = {
                        scope.launch { repository.setCurrencyEnabled(currency.code, !currency.enabled) }
                    }) { Text(if (currency.enabled) "Disable" else "Enable") }
                }
            }
        }
        item { Text("Automatic rates refresh daily. Manual pins are available after a rate has been retrieved.") }
    }
}

@Composable
private fun referenceScreen(
    repository: MizanRepository,
    type: String,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val categories by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val labels by repository.observeLabels().collectAsStateWithLifecycle(emptyList())
    val payees by repository.observePayees().collectAsStateWithLifecycle(emptyList())
    val values: List<Pair<String, String>> =
        when (type) {
            "categories" -> {
                categories.map { it.id to it.name }
            }

            "labels" -> {
                labels.map {
                    it.id to
                        it.name
                }
            }

            else -> {
                payees.map { it.id to it.name }
            }
        }
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(text, { text = it }, label = { Text("New ${type.dropLast(1)}") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Button(onClick = {
                scope.launch {
                    if (text.isNotBlank()) {
                        when (type) {
                            "categories" -> {
                                repository.saveCategory(
                                    CategoryEntity(
                                        java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                        name = text,
                                        icon = "tag",
                                        color = "#F2B84B",
                                        kind = "EXPENSE",
                                    ),
                                )
                            }

                            "labels" -> {
                                repository.saveLabel(
                                    LabelEntity(
                                        java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                        text,
                                        "#F2B84B",
                                    ),
                                )
                            }

                            else -> {
                                repository.savePayee(
                                    PayeeEntity(
                                        java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                        text,
                                    ),
                                )
                            }
                        }
                        text =
                            ""
                    }
                }
            }) { Text("Add") }
        }
        items(values, key = { it.first }) { value -> Text(value.second, modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
private fun accountDetail(
    repository: MizanRepository,
    accountId: String,
    modifier: Modifier,
) {
    val account by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val buckets by repository.observeBuckets(accountId).collectAsStateWithLifecycle(emptyList())
    val deltas by repository.observeBucketDeltas(accountId).collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(cairo)
    val daily by repository.observeDailyDeltas(accountId, today.minusDays(29).toString()).collectAsStateWithLifecycle(emptyList())
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(account.firstOrNull { it.id == accountId }?.name ?: "Account", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Per-currency balance") }
        items(buckets, key = { it.currency }) { bucket ->
            val delta = deltas.firstOrNull { it.currency == bucket.currency }?.amountMinor ?: 0
            amountText(bucket.openingMinor + delta, bucket.currency)
        }
        item { HorizontalDivider() }
        item { Text("30-day trend · ${daily.size} recorded days") }
        items(daily, key = { it.date }) { point -> Text("${point.date}: ${point.baseMinor} EGP minor") }
    }
}

@Composable
private fun devScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Dev-only: no product data is stored by probes.") }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dev tools", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = {
            scope.launch {
                status = "Seeding…"
                status = "Seeded 5,000 records in ${repository.seedFiveThousand()} ms"
            }
        }) { Text("Seed 5,000 records") }
        Button(
            onClick = { scope.launch { status = "Removed ${repository.clearDevSeed()} seeded records" } },
        ) { Text("Clear seeded records") }
        Text(status)
    }
}

@Composable
private fun emptyScreen(
    label: String,
    modifier: Modifier,
) = emptyState("$label is planned for a later milestone.", "Add record", {})

@Composable
private fun emptyState(
    message: String,
    action: String,
    onAction: () -> Unit,
) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text(message)
    Button(onClick = onAction) { Text(action) }
}

@Composable
fun amountText(
    minor: Long,
    currency: String,
    big: Boolean = false,
) {
    if (LocalHideAmounts.current) {
        Text("••••", style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium)
        return
    }
    val scale =
        if (currency == "JPY") {
            0
        } else if (currency == "KWD") {
            3
        } else {
            2
        }
    val value =
        BigDecimal
            .valueOf(minor)
            .movePointLeft(scale)
            .setScale(scale, RoundingMode.HALF_UP)
            .toPlainString()
    Text(
        "$value $currency",
        style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Default,
    )
}

fun amountToMinor(
    value: String,
    decimals: Int,
): Long =
    runCatching {
        EntryCalculator.evaluate(
            value,
        )
    }.getOrNull()?.movePointRight(decimals)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()
        ?: 0L

private fun tabSymbol(tab: String): String =
    when (tab) {
        "Home" -> "⌂"
        "Records" -> "≡"
        "Stats" -> "◌"
        "Plan" -> "□"
        else -> "◇"
    }
