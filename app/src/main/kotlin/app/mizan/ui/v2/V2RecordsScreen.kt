// Copyright (c) 2026 Mizan contributors
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.PagedRecordRow
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.ports.RecordQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun V2RecordsScreen(
    repository: MizanRepository,
    period: PeriodState,
    selectedAccountId: String?,
    hideAmounts: Boolean,
    onOpenPeriod: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenInbox: () -> Unit,
    onAddExpense: () -> Unit,
    onManageLabels: () -> Unit,
    onAccountSelected: (String?) -> Unit,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val categories by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val incomeCategories by repository.observeCategories("INCOME").collectAsStateWithLifecycle(emptyList())
    val labels by repository.observeLabels().collectAsStateWithLifecycle(emptyList())
    val drafts by repository.observeInboxDrafts().collectAsStateWithLifecycle(emptyList())
    val uncategorized by repository.observeUncategorizedExpenseCount().collectAsStateWithLifecycle(0)
    var query by remember(period, selectedAccountId) {
        mutableStateOf(
            RecordQuery(
                accountId = selectedAccountId?.takeUnless { '|' in it },
                accountIds = selectedAccountId?.takeIf { '|' in it },
                dateStart = periodBounds(period).first,
                dateEnd = periodBounds(period).second,
            ),
        )
    }
    val dailyNet by remember(query) { repository.observeDailyRecordNet(query) }.collectAsStateWithLifecycle(emptyList())
    var searchText by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pageFlow =
        remember(query) {
            Pager(
                PagingConfig(
                    pageSize = 40,
                    prefetchDistance = 20,
                    enablePlaceholders = false,
                    maxSize = 160,
                    jumpThreshold = 200,
                ),
            ) { repository.records(query) }.flow.cachedIn(scope)
        }
    val records = pageFlow.collectAsLazyPagingItems()
    val resultCount by remember(query) { repository.observeRecordCount(query) }.collectAsState(initial = 0)

    LaunchedEffect(searchText) {
        delay(300)
        query = query.copy(search = searchText.trim().takeIf(String::isNotEmpty))
    }
    LaunchedEffect(period, selectedAccountId) {
        val (start, end) = periodBounds(period)
        query =
            query.copy(
                accountId = selectedAccountId?.takeUnless { '|' in it },
                accountIds = selectedAccountId?.takeIf { '|' in it },
                dateStart = start,
                dateEnd = end,
            )
    }

    Column(Modifier.fillMaxSize().background(LocalMzTokens.current.canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MzSpacing.sideMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MzChip(periodLabel(period), selected = false, onClick = onOpenPeriod, modifier = Modifier.weight(1f))
            MzChip(
                when {
                    selectedAccountId == null -> "All accounts"
                    '|' in selectedAccountId -> "${selectedAccountId.split('|').size} accounts"
                    else -> accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "All accounts"
                },
                selected = selectedAccountId != null,
                onClick = { showAccountMenu = !showAccountMenu },
                modifier = Modifier.weight(1f),
            )
        }
        if (showAccountMenu) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MzSpacing.sideMargin).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MzChip("All", selectedAccountId == null, { onAccountSelected(null) })
                accounts.forEach { account ->
                    MzChip(account.name, selectedAccountId == account.id, { onAccountSelected(account.id) })
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MzSpacing.sideMargin,
                ).padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MzChip("Inbox · ${uncategorized + drafts.size}", false, onOpenInbox)
            MzChip("Kind", query.kind != null, { showFilters = true })
            MzChip("Account", query.accountId != null, { showFilters = true })
            MzChip("Category", query.categoryId != null, { showFilters = true })
            MzChip("Date", query.dateStart != null || query.dateEnd != null, { showFilters = true })
        }
        MzInput(searchText, { searchText = it }, "Search note or payee", Modifier.padding(horizontal = MzSpacing.sideMargin))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MzSpacing.sideMargin, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$resultCount records", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
            Spacer(Modifier.weight(1f))
            MzChip("Filters", showFilters, { showFilters = true })
        }

        val netByDate = remember(dailyNet) { dailyNet.associate { it.date to it.baseMinor } }
        val loadedRows = records.itemSnapshotList.items
        val rowsByDay by produceState(initialValue = emptyList<Pair<String, List<PagedRecordRow>>>(), query, loadedRows) {
            value =
                withContext(Dispatchers.Default) {
                    loadedRows.groupBy(PagedRecordRow::bookedDate).map { (date, rows) -> date to rows }
                }
        }
        val accountColors = remember(accounts) { accounts.associate { it.id to it.color } }
        val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
        val listState = rememberLazyListState()
        val appendState = records.loadState.append
        LaunchedEffect(listState, records.itemCount, appendState) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
            }.collect { lastVisible ->
                val totalItems = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && lastVisible >= totalItems - 4 && appendState is androidx.paging.LoadState.NotLoading &&
                    !appendState.endOfPaginationReached && loadedRows.isNotEmpty()
                ) {
                    records[loadedRows.lastIndex]
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = MzSpacing.sideMargin, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rowsByDay.forEach { (date, dayRows) ->
                stickyHeader(key = "records-day-$date") {
                    RecordDayHeader(date, netByDate[date] ?: 0L, hideAmounts)
                }
                items(dayRows, key = PagedRecordRow::id) { row ->
                    SwipeRecordRow(
                        row = row,
                        accountColors = accountColors,
                        accountNames = accountNames,
                        hideAmounts = hideAmounts,
                        onOpen = { onOpenDetail(row.id) },
                        onEdit = { onEdit(row.id) },
                        onDelete = {
                            scope.launch {
                                repository.deleteRecord(row.id)
                                val result = snackbar.showSnackbar("Record deleted", actionLabel = "Undo")
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) repository.restoreRecord(row.id)
                            }
                        },
                    )
                }
            }
            if (records.loadState.refresh is androidx.paging.LoadState.Loading && records.itemCount == 0) {
                items(5) { MzCard(Modifier.fillMaxWidth()) { MzSkeleton(Modifier.fillMaxWidth().padding(18.dp), 52.dp) } }
            }
            if (records.loadState.refresh is androidx.paging.LoadState.Error) {
                item { MzEmptyState("Couldn't load records", "Check your data and try again.", "Retry", records::retry) }
            }
            if (records.loadState.append.endOfPaginationReached && records.itemCount > 0) {
                item {
                    Text(
                        "🔒  END OF SETTLED ENTRIES",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        color = LocalMzTokens.current.textDim,
                        style = MzTypography.labelCaps,
                    )
                }
            }
            if (
                records.itemCount == 0 &&
                records.loadState.refresh !is androidx.paging.LoadState.Loading &&
                records.loadState.refresh !is androidx.paging.LoadState.Error
            ) {
                item { MzEmptyState("No records yet", "Records for this period will appear here.", "Add expense", onAddExpense) }
            }
        }
        SnackbarHost(snackbar, Modifier.fillMaxWidth())
    }
    if (showFilters) {
        RecordFiltersSheet(
            query = query,
            count = resultCount,
            accounts = accounts,
            categories = categories + incomeCategories,
            labels = labels,
            onChange = { query = it },
            onAddLabel = onManageLabels,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun RecordDayHeader(
    date: String,
    netMinor: Long,
    hideAmounts: Boolean,
) {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            parsed?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: date,
            style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold),
        )
        AmountText(
            netMinor,
            "EGP",
            if (netMinor <
                0
            ) {
                AmountStyle.EXPENSE
            } else {
                AmountStyle.INCOME
            },
            hideAmounts,
            textStyle = MzTypography.bodySmall,
        )
    }
}

@Composable
private fun SwipeRecordRow(
    row: PagedRecordRow,
    accountColors: Map<String, String>,
    accountNames: Map<String, String>,
    hideAmounts: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var drag by remember(row.entryId) { mutableFloatStateOf(0f) }
    val allocations = remember(row.allocationData) { row.incomeAllocations() }
    val tokens = LocalMzTokens.current
    val actionRevealWidth = 212.dp
    val style =
        when {
            row.kind == "TRANSFER" -> AmountStyle.TRANSFER
            row.kind.startsWith("ASSET_") -> AmountStyle.INVESTMENT
            row.amountMinor < 0 -> AmountStyle.EXPENSE
            else -> AmountStyle.INCOME
        }
    val title =
        row.categoryName ?: row.payeeName ?: row.kind
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar(Char::uppercase)
    val subtitle =
        when {
            row.kind == "TRANSFER" -> {
                if (row.counterAmountMinor == null || row.counterCurrency == null) {
                    "${row.accountName} · Transfer"
                } else {
                    val rate = impliedTransferRate(row)
                    "${row.accountName} → ${row.counterAccountName.orEmpty()} · 1 ${row.currency} = $rate ${row.counterCurrency}"
                }
            }

            row.kind == "INCOME" && allocations.size > 1 -> {
                val names = allocations.mapNotNull { accountNames[it.accountId] }.distinct().joinToString(" · ")
                "${allocations.size} allocations · $names"
            }

            else -> {
                row.accountName + if (row.status == "UNCLEARED") " · Uncleared" else ""
            }
        }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .background(tokens.lowestSurface)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MzButton("Edit", onEdit, Modifier.width(96.dp), MzButtonStyle.SECONDARY)
            MzButton("Delete", onDelete, Modifier.width(104.dp), MzButtonStyle.SECONDARY)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .offset { IntOffset(drag.roundToInt(), 0) }
                .clip(MzShapes.exterior)
                .background(tokens.surface)
                .border(1.dp, tokens.outline.copy(alpha = 0.4f), MzShapes.exterior)
                .pointerInput(row.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = drag.coerceIn(-actionRevealWidth.toPx(), 0f) },
                        onHorizontalDrag = { _, delta -> drag = (drag + delta).coerceIn(-actionRevealWidth.toPx(), 0f) },
                        onDragEnd = {
                            drag = if (drag < -72.dp.toPx()) -actionRevealWidth.toPx() else 0f
                        },
                    )
                }.clickable(onClick = onOpen)
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconColor =
                when (style) {
                    AmountStyle.EXPENSE -> tokens.expense
                    AmountStyle.INCOME -> tokens.income
                    AmountStyle.TRANSFER -> tokens.transfer
                    AmountStyle.INVESTMENT -> tokens.investment
                    AmountStyle.NEUTRAL -> tokens.textDim
                }
            val iconName =
                when (style) {
                    AmountStyle.EXPENSE -> "shopping_bag"
                    AmountStyle.INCOME -> "payments"
                    AmountStyle.TRANSFER -> "swap_horiz"
                    AmountStyle.INVESTMENT -> "diamond"
                    AmountStyle.NEUTRAL -> "receipt_long"
                }
            Box(Modifier.size(40.dp).background(iconColor.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                MzIcon(iconName, row.kind, tint = iconColor, size = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, maxLines = 1, style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    subtitle +
                        if (row.status ==
                            "UNCLEARED"
                        ) {
                            " · •"
                        } else {
                            ""
                        },
                    maxLines = 1,
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (row.kind == "INCOME" && allocations.size > 1) {
                    IncomeSplitBar(allocations, accountColors, Modifier.width(76.dp).padding(bottom = 4.dp))
                }
                AmountText(
                    row.amountMinor,
                    row.currency,
                    style,
                    hideAmounts,
                    textStyle = MzTypography.amountMedium,
                    showCurrency = row.currency != "EGP",
                )
                if (row.kind.startsWith("ASSET_")) {
                    Text("INVESTED", color = tokens.investment, style = MzTypography.labelCaps.copy(fontSize = 9.sp))
                }
            }
        }
    }
}

private data class IncomeAllocation(
    val accountId: String,
    val amountMinor: Long,
)

private fun PagedRecordRow.incomeAllocations(): List<IncomeAllocation> =
    allocationData.split(',').mapNotNull { allocation ->
        val separator = allocation.lastIndexOf(':')
        if (separator <= 0) {
            null
        } else {
            allocation.substring(separator + 1).toLongOrNull()?.let { amount ->
                IncomeAllocation(allocation.substring(0, separator), amount)
            }
        }
    }

@Composable
private fun IncomeSplitBar(
    rows: List<IncomeAllocation>,
    accountColors: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val nonZeroRows = rows.filter { it.amountMinor != 0L }
    val total = nonZeroRows.sumOf { kotlin.math.abs(it.amountMinor) }.coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().heightIn(min = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        nonZeroRows.forEach { row ->
            val weight =
                BigDecimal
                    .valueOf(kotlin.math.abs(row.amountMinor))
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .toFloat()
                    .coerceAtLeast(0.01f)
            val color =
                runCatching {
                    androidx.compose.ui.graphics.Color(
                        android.graphics.Color.parseColor(accountColors[row.accountId] ?: "#F2B84B"),
                    )
                }.getOrDefault(LocalMzTokens.current.champagne)
            androidx.compose.foundation.layout.Box(
                Modifier.weight(weight).height(4.dp).background(
                    color,
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(2.dp),
                ),
            )
        }
    }
}

private fun impliedTransferRate(row: PagedRecordRow): String {
    val sourceMajor = BigDecimal.valueOf(kotlin.math.abs(row.amountMinor)).movePointLeft(currencyDecimals(row.currency))
    val receivedMajor =
        BigDecimal
            .valueOf(kotlin.math.abs(row.counterAmountMinor ?: return "—"))
            .movePointLeft(currencyDecimals(row.counterCurrency ?: return "—"))
    if (sourceMajor.signum() == 0) return "—"
    return receivedMajor.divide(sourceMajor, 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

@Composable
private fun RecordFiltersSheet(
    query: RecordQuery,
    count: Int,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    labels: List<app.mizan.data.db.LabelEntity>,
    onChange: (RecordQuery) -> Unit,
    onAddLabel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minimumText by remember(query.amountMin, query.currency) {
        mutableStateOf(
            query.amountMin
                ?.let {
                    formatMinorInput(
                        it,
                        query.currency ?: "EGP",
                    )
                }.orEmpty(),
        )
    }
    var maximumText by remember(query.amountMax, query.currency) {
        mutableStateOf(
            query.amountMax
                ?.let {
                    formatMinorInput(
                        it,
                        query.currency ?: "EGP",
                    )
                }.orEmpty(),
        )
    }
    var startDateText by remember(query.dateStart) { mutableStateOf(query.dateStart?.toString().orEmpty()) }
    var endDateText by remember(query.dateEnd) { mutableStateOf(query.dateEnd?.toString().orEmpty()) }
    MzSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Filter records", style = MzTypography.title)
            Text("Kind", style = MzTypography.labelCaps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("INCOME", "EXPENSE", "TRANSFER", "INVESTMENT", "DEBT").forEach { kind ->
                    val selected = query.kindGroup == kind || query.kind?.name == kind
                    MzChip(kind.lowercase().replaceFirstChar(Char::uppercase), selected, {
                        if (selected) {
                            onChange(query.copy(kind = null, kindGroup = null))
                        } else {
                            onChange(query.copy(kind = null, kindGroup = kind))
                        }
                    })
                }
            }
            Text("Accounts", style = MzTypography.labelCaps)
            val activeAccounts =
                query.accountIds
                    ?.split('|')
                    ?.filter(String::isNotBlank)
                    ?.toSet() ?: query.accountId?.let(::setOf).orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MzChip("All", activeAccounts.isEmpty(), { onChange(query.copy(accountId = null, accountIds = null)) })
                accounts.forEach { account ->
                    val checked = account.id in activeAccounts
                    MzChip(account.name, checked, {
                        val selected = if (checked) activeAccounts - account.id else activeAccounts + account.id
                        onChange(query.copy(accountId = null, accountIds = selected.sorted().takeIf { it.isNotEmpty() }?.joinToString("|")))
                    })
                }
            }
            Text("Category", style = MzTypography.labelCaps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MzChip("All", query.categoryId == null, { onChange(query.copy(categoryId = null)) })
                categories.forEach { category ->
                    MzChip(category.name, query.categoryId == category.id, { onChange(query.copy(categoryId = category.id)) })
                }
            }
            Text("Labels", style = MzTypography.labelCaps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MzChip("All", query.labelId == null, { onChange(query.copy(labelId = null)) })
                labels.forEach { label -> MzChip(label.name, query.labelId == label.id, { onChange(query.copy(labelId = label.id)) }) }
                MzChip("+ Add label", false, onAddLabel)
            }
            Text("Status", style = MzTypography.labelCaps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("CLEARED", "UNCLEARED", "RECONCILED").forEach { status ->
                    MzChip(status, query.status == status, {
                        onChange(
                            query.copy(
                                status =
                                    if (query.status ==
                                        status
                                    ) {
                                        null
                                    } else {
                                        status
                                    },
                            ),
                        )
                    })
                }
            }
            Text("Currency", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EGP", "USD", "EUR", "KWD", "JPY").forEach { currency ->
                    MzChip(currency, query.currency == currency, {
                        val selectedCurrency = if (query.currency == currency) null else currency
                        onChange(query.copy(currency = selectedCurrency, amountMin = null, amountMax = null))
                    })
                }
            }
            val amountCurrency = query.currency ?: "EGP"
            Text("Amount range · $amountCurrency", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MzInput(minimumText, {
                    minimumText = it
                    parseMinorInput(it, amountCurrency)?.let { value -> onChange(query.copy(amountMin = value)) }
                }, "Min", Modifier.weight(1f))
                MzInput(maximumText, {
                    maximumText = it
                    parseMinorInput(it, amountCurrency)?.let { value -> onChange(query.copy(amountMax = value)) }
                }, "Max", Modifier.weight(1f))
            }
            Text("Date range", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MzInput(startDateText, {
                    startDateText = it
                    it.toLocalDateOrNull()?.let { date -> onChange(query.copy(dateStart = date)) }
                }, "From · YYYY-MM-DD", Modifier.weight(1f))
                MzInput(endDateText, {
                    endDateText = it
                    it.toLocalDateOrNull()?.let { date -> onChange(query.copy(dateEnd = date)) }
                }, "To · YYYY-MM-DD", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    MzChip("Last $days days", false, {
                        val end = LocalDate.now(ZoneId.of("Africa/Cairo"))
                        onChange(query.copy(dateStart = end.minusDays(days.toLong() - 1), dateEnd = end))
                    })
                }
                MzChip("Any date", false, { onChange(query.copy(dateStart = null, dateEnd = null)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MzButton("Reset", { onChange(RecordQuery()) }, Modifier.weight(1f), MzButtonStyle.SECONDARY)
                MzButton("Show $count records", onDismiss, Modifier.weight(2f))
            }
        }
    }
}

private fun formatMinorInput(
    minor: Long,
    currency: String,
): String =
    BigDecimal
        .valueOf(minor)
        .movePointLeft(currencyDecimals(currency))
        .stripTrailingZeros()
        .toPlainString()

private fun parseMinorInput(
    value: String,
    currency: String,
): Long? =
    runCatching {
        BigDecimal(value.trim()).movePointRight(currencyDecimals(currency)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()

private fun currencyDecimals(currency: String): Int =
    when (currency) {
        "KWD" -> 3
        "JPY" -> 0
        else -> 2
    }

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun periodBounds(period: PeriodState): Pair<LocalDate?, LocalDate?> {
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    return when (period.kind) {
        PeriodKind.DAY -> {
            val day = period.start ?: today
            day to day
        }

        PeriodKind.WEEK -> {
            val anchor = period.start ?: today
            val start = anchor.minusDays(((anchor.dayOfWeek.value + 1) % 7).toLong())
            start to start.plusDays(6)
        }

        PeriodKind.MONTH -> {
            today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        }

        PeriodKind.YEAR -> {
            today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
        }

        PeriodKind.CUSTOM -> {
            period.start to period.end
        }

        PeriodKind.ALL -> {
            null to null
        }
    }
}

private fun periodLabel(period: PeriodState): String =
    when (period.kind) {
        PeriodKind.DAY -> {
            (period.start ?: LocalDate.now(ZoneId.of("Africa/Cairo"))).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }

        PeriodKind.WEEK -> {
            val anchor = period.start ?: LocalDate.now(ZoneId.of("Africa/Cairo"))
            val start = anchor.minusDays(((anchor.dayOfWeek.value + 1) % 7).toLong())
            "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        }

        PeriodKind.MONTH -> {
            LocalDate.now(ZoneId.of("Africa/Cairo")).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }

        PeriodKind.YEAR -> {
            LocalDate.now(ZoneId.of("Africa/Cairo")).year.toString()
        }

        PeriodKind.CUSTOM -> {
            val start = period.start
            val end = period.end
            when {
                start == null || end == null -> {
                    "Custom period"
                }

                start.year == end.year && start.dayOfYear == 1 && end.dayOfYear == end.lengthOfYear() -> {
                    start.year.toString()
                }

                start.dayOfMonth == 1 && end == start.withDayOfMonth(start.lengthOfMonth()) -> {
                    start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                }

                else -> {
                    "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
                }
            }
        }

        PeriodKind.ALL -> {
            "All time"
        }
    }
