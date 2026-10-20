// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mizan.R
import app.mizan.domain.plan.Forecast
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEBT_TO_INCOME_CEILING_BPS = 3_500L

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun V2StatsOutlookPage(
    data: OutlookStats,
    hideAmounts: Boolean,
    includeTypicalIncome: Boolean,
    onIncludeTypicalIncomeChanged: (Boolean) -> Unit,
    share: (String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val tokens = LocalMzTokens.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShareableCard("Planned payments", share) {
                Text("What do I have to pay soon?", color = tokens.textDim, style = MzTypography.bodySmall)
                val out = data.timeline.sumOf { it.outMinor }
                val incoming = data.timeline.sumOf { it.inMinor }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Out · ${displayAmount(out, hideAmounts)}", color = tokens.expense, style = MzTypography.amountSmall)
                    Text("In · ${displayAmount(incoming, hideAmounts)}", color = tokens.income, style = MzTypography.amountSmall)
                }
                if (data.timeline.isEmpty()) {
                    Text("No upcoming planned payments in this period.", color = tokens.textDim, style = MzTypography.body)
                }
                data.timeline.forEach { day ->
                    Text(
                        day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase(),
                        color = tokens.textDim,
                        style = MzTypography.labelCaps,
                    )
                    day.items.forEach { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(tokens.raisedSurface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        when (item.kind) {
                                            "INCOME" -> "↑"
                                            "TRANSFER" -> "↔"
                                            else -> "↓"
                                        },
                                        color =
                                            when (item.kind) {
                                                "INCOME" -> tokens.income
                                                "TRANSFER" -> tokens.transfer
                                                else -> tokens.expense
                                            },
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(item.name, style = MzTypography.body)
                                    Text(
                                        "Balance after · ${displayAmount(item.balanceAfterMinor, hideAmounts)}",
                                        color = tokens.textDim,
                                        style = MzTypography.bodySmall,
                                    )
                                }
                                Text(
                                    (if (item.signedMinor >= 0L) "+" else "−") +
                                        displayAmount(kotlin.math.abs(item.signedMinor), hideAmounts),
                                    color = if (item.signedMinor >= 0L) tokens.income else tokens.expense,
                                    style = MzTypography.amountSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            ShareableCard("Balance forecast", share) {
                Text("Will I have enough for the rest of the period?", color = tokens.textDim, style = MzTypography.bodySmall)
                Text(
                    "PROJECTED PERIOD-END · ${data.projectedThrough}",
                    color = tokens.textDim,
                    style = MzTypography.labelCaps.copy(color = tokens.textDim),
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text("Ends around ${displayAmount(data.forecast.endMinor, hideAmounts)}", style = MzTypography.amountLarge)
                ForecastWaterfall(data.forecast, hideAmounts)
                MzToggle(includeTypicalIncome, onIncludeTypicalIncomeChanged, "Include typical income (assumption)")
                Text(
                    if (includeTypicalIncome) {
                        "The forecast only counts income you scheduled, plus typical income as an assumption."
                    } else {
                        "The forecast only counts income you scheduled."
                    },
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ForecastWaterfall(
    forecast: Forecast,
    hideAmounts: Boolean,
) {
    val tokens = LocalMzTokens.current
    val steps =
        listOf(
            "Start" to forecast.startMinor,
            "Planned" to forecast.plannedMinor,
            "Unplanned" to -forecast.unplannedMinor,
            "Expected income" to forecast.expectedIncomeMinor,
            "End" to forecast.endMinor,
        )
    val running = mutableListOf<Long>()
    var total = forecast.startMinor
    steps.forEachIndexed { index, (_, value) ->
        if (index == 0) {
            total = value
        } else if (index < steps.lastIndex) {
            total += value
        }
        running += total
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Forecast waterfall chart" }) {
            val values = listOf(0L, forecast.startMinor, running[1], running[2], running[3], forecast.endMinor)
            val minValue = values.minOrNull() ?: 0L
            val maxValue = values.maxOrNull() ?: 0L
            val range = (maxValue - minValue).coerceAtLeast(1L).toFloat()
            val count = steps.size
            val slot = size.width / count
            val barWidth = slot * 0.48f
            val yFor = { value: Long -> size.height - ((value - minValue).toFloat() / range * size.height) }
            drawLine(tokens.outline, Offset(0f, yFor(0L)), Offset(size.width, yFor(0L)), 1.dp.toPx())
            steps.forEachIndexed { index, (_, change) ->
                val (from, to) =
                    when (index) {
                        0 -> 0L to forecast.startMinor
                        steps.lastIndex -> 0L to forecast.endMinor
                        else -> running[index] - change to running[index]
                    }
                val top = minOf(yFor(from), yFor(to))
                val bottom = maxOf(yFor(from), yFor(to))
                val color =
                    if (index == 0 ||
                        index == steps.lastIndex
                    ) {
                        tokens.champagne
                    } else if (change < 0L) {
                        tokens.expense
                    } else {
                        tokens.income
                    }
                drawRoundRect(
                    color,
                    Offset(slot * index + (slot - barWidth) / 2f, top),
                    Size(barWidth, (bottom - top).coerceAtLeast(3.dp.toPx())),
                    androidx.compose.ui.geometry
                        .CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            steps.forEachIndexed { index, (label, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (hideAmounts) {
                            "••••"
                        } else {
                            compactAmount(
                                if (index == 0 ||
                                    index == steps.lastIndex
                                ) {
                                    value
                                } else {
                                    value
                                },
                            )
                        },
                        style = MzTypography.labelCaps,
                        color =
                            if (value <
                                0
                            ) {
                                tokens.expense
                            } else {
                                tokens.text
                            },
                    )
                    Text(label, style = MzTypography.labelCaps, color = tokens.textDim)
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun V2StatsReportsPage(
    data: ReportsStats,
    period: app.mizan.domain.stats.Period,
    investmentRateTargetBps: Long,
    hideAmounts: Boolean,
    goDeeper: (String) -> Unit,
    share: (String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val tokens = LocalMzTokens.current
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val report = data.report
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShareableCard("Cash flow table", share) {
                Text(
                    "Counts and averages use elapsed days in the selected period.",
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Metric", Modifier.weight(1f), style = MzTypography.labelCaps)
                    Text("Income", Modifier.weight(1f), style = MzTypography.labelCaps)
                    Text("Expenses", Modifier.weight(1f), style = MzTypography.labelCaps)
                }
                ReportRow("Count", report.incomeCount.toString(), report.expenseCount.toString())
                ReportRow(
                    "Avg / day",
                    displayAmount(report.averageIncomePerDay, hideAmounts),
                    displayAmount(report.averageExpensePerDay, hideAmounts),
                )
                ReportRow(
                    "Avg / record",
                    displayAmount(report.averageIncomePerRecord, hideAmounts),
                    displayAmount(report.averageExpensePerRecord, hideAmounts),
                )
                ReportRow(
                    "Total",
                    displayAmount(report.cashFlow.incomeMinor, hideAmounts),
                    displaySigned(-report.cashFlow.expenseMinor, hideAmounts),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net cash flow", style = MzTypography.body)
                    Text(
                        displaySigned(report.cashFlow.netMinor, hideAmounts),
                        color =
                            if (report.cashFlow.netMinor >=
                                0L
                            ) {
                                tokens.income
                            } else {
                                tokens.expense
                            },
                        style = MzTypography.amountSmall,
                    )
                }
            }
        }
        item {
            ShareableCard("Income & expenses book", share) {
                Text("Where did it come from and go?", color = tokens.textDim, style = MzTypography.bodySmall)
                ReportCategoryTree(
                    "Income",
                    data.incomeCategories,
                    expanded,
                    { expanded = toggle(expanded, it) },
                    goDeeper,
                    hideAmounts,
                )
                ReportCategoryTree(
                    "Expenses",
                    data.expenseCategories,
                    expanded,
                    { expanded = toggle(expanded, it) },
                    goDeeper,
                    hideAmounts,
                )
            }
        }
        item {
            ShareableCard("Savings & investing", share) {
                Text(stringResource(R.string.reports_savings_prompt), color = tokens.textDim, style = MzTypography.bodySmall)
                Text(
                    stringResource(R.string.reports_selected_period, period.start, period.end),
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
                Text(
                    stringResource(R.string.reports_rates_period_explanation),
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
                Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RateTile(
                        "Savings rate",
                        data.savingsRate,
                        data.savingsSeries,
                        null,
                        stringResource(
                            R.string.reports_saved_from_income,
                            displaySigned(data.savingsMinor, hideAmounts),
                            displayAmount(data.incomeMinor, hideAmounts),
                        ),
                        hideAmounts,
                        Modifier.fillMaxWidth(),
                    )
                    RateTile(
                        "Invest rate",
                        data.investRate,
                        data.investSeries,
                        investmentRateTargetBps,
                        stringResource(
                            R.string.reports_invested_from_income,
                            displayAmount(data.investedMinor, hideAmounts),
                            displayAmount(data.incomeMinor, hideAmounts),
                        ),
                        hideAmounts,
                        Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.reports_invest_rate_explanation),
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
            }
        }
        item {
            ShareableCard("Debt to income", share) {
                Text("Is my debt load sustainable?", color = tokens.textDim, style = MzTypography.bodySmall)
                RatioBenchmark(data.debtToIncome, DEBT_TO_INCOME_CEILING_BPS, hideAmounts)
                Text(
                    "Debt-service expenses · ${displayAmount(data.debtServiceMinor, hideAmounts)}",
                    color = tokens.textDim,
                    style = MzTypography.bodySmall,
                )
            }
        }
        if (data.hasCreditCards) {
            item {
                ShareableCard("Credit utilization", share) {
                    Text("Current owed versus total card limits", color = tokens.textDim, style = MzTypography.bodySmall)
                    Text(if (hideAmounts) "•••" else data.creditUtilization?.let { percent(it) } ?: "—", style = MzTypography.amountLarge)
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ReportRow(
    label: String,
    income: String,
    expense: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MzTypography.bodySmall)
        Text(income, Modifier.weight(1f), color = LocalMzTokens.current.income, style = MzTypography.amountSmall)
        Text(expense, Modifier.weight(1f), color = LocalMzTokens.current.expense, style = MzTypography.amountSmall)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ReportCategoryTree(
    title: String,
    categories: List<ReportCategory>,
    expanded: Set<String>,
    onExpand: (String) -> Unit,
    goDeeper: (String) -> Unit,
    hideAmounts: Boolean,
) {
    val tokens = LocalMzTokens.current
    val total = categories.sumOf { it.amountMinor }
    Text(
        "$title (${categories.size})",
        color =
            if (title ==
                "Income"
            ) {
                tokens.income
            } else {
                tokens.expense
            },
        style = MzTypography.title,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        displaySigned(
            if (title ==
                "Income"
            ) {
                total
            } else {
                -total
            },
            hideAmounts,
        ),
        color =
            if (title ==
                "Income"
            ) {
                tokens.income
            } else {
                tokens.expense
            },
        style = MzTypography.amountSmall,
    )
    ReportCategoryRows(categories, expanded, onExpand, goDeeper, hideAmounts, title)
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ReportCategoryRows(
    categories: List<ReportCategory>,
    expanded: Set<String>,
    onExpand: (String) -> Unit,
    goDeeper: (String) -> Unit,
    hideAmounts: Boolean,
    title: String,
    level: Int = 0,
) {
    val tokens = LocalMzTokens.current
    val sign = if (title == "Income") 1 else -1
    val color = if (title == "Income") tokens.income else tokens.expense
    categories.forEach { category ->
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(start = (level * 16).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable { goDeeper(category.id) }.padding(vertical = 4.dp)) {
                Text(category.name, style = MzTypography.body)
                Text(changeText(category.change, hideAmounts), color = tokens.textDim, style = MzTypography.labelCaps)
            }
            Text(displaySigned(sign * category.amountMinor, hideAmounts), color = color, style = MzTypography.amountSmall)
            if (category.children.isNotEmpty()) {
                IconButton(onClick = { onExpand(category.id) }) {
                    MzIcon(
                        if (category.id in expanded) "expand_less" else "expand_more",
                        if (category.id in expanded) "Collapse ${category.name}" else "Expand ${category.name}",
                    )
                }
            }
        }
        if (category.id in expanded) {
            ReportCategoryRows(category.children, expanded, onExpand, goDeeper, hideAmounts, title, level + 1)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun RateTile(
    label: String,
    rate: Long?,
    history: List<Long>,
    target: Long?,
    detail: String,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Column(modifier.background(tokens.raisedSurface, MzShapes.inner).padding(12.dp)) {
        Text(label.uppercase(), color = tokens.textDim, style = MzTypography.labelCaps)
        Text(if (hideAmounts) "•••" else rate?.let(::percent) ?: "—", style = MzTypography.amountLarge)
        if (target != null) Text("Target · ${percent(target)}", color = tokens.accentText, style = MzTypography.bodySmall)
        Text(detail, color = tokens.textDim, style = MzTypography.bodySmall)
        MzLineChart(
            history,
            Modifier.fillMaxWidth(),
            hideAmounts = hideAmounts,
            chartHeight = 82.dp,
            xAxisLabels = listOf("6 mo ago", "Today"),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun RatioBenchmark(
    value: Long?,
    ceiling: Long,
    hideAmounts: Boolean,
) {
    val tokens = LocalMzTokens.current
    val ratio = (value ?: 0L).coerceAtLeast(0L)
    val max = maxOf(ceiling * 2, ratio, 1L)
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(if (hideAmounts) "••••" else value?.let(::percent) ?: "—", style = MzTypography.amountLarge)
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(tokens.raisedSurface),
            )
            Spacer(
                Modifier
                    .fillMaxWidth((ratio.toFloat() / max).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(tokens.income),
            )
            Box(
                Modifier.fillMaxWidth((ceiling.toFloat() / max).coerceIn(0f, 1f)).height(18.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Spacer(Modifier.size(2.dp, 16.dp).background(tokens.warning))
            }
        }
        Text("Safe ceiling · ${percent(ceiling)}", color = tokens.textDim, style = MzTypography.labelCaps)
    }
}

private fun toggle(
    expanded: Set<String>,
    key: String,
): Set<String> = if (key in expanded) expanded - key else expanded + key

private fun displaySigned(
    amount: Long,
    hidden: Boolean,
): String = if (hidden) "••••" else (if (amount >= 0L) "+" else "−") + amount(kotlin.math.abs(amount))

private fun displayAmount(
    amount: Long,
    hidden: Boolean,
): String = if (hidden) "••••" else amount(amount)

private fun compactAmount(amount: Long): String =
    "${BigDecimal.valueOf(amount).movePointLeft(5).setScale(1, java.math.RoundingMode.HALF_UP)}k"

private fun percent(basisPoints: Long): String {
    val absolute = kotlin.math.abs(basisPoints)
    val sign = if (basisPoints < 0L) "−" else ""
    return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}%"
}
