// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.plan.CreditCardBalance
import app.mizan.domain.plan.CreditMath
import app.mizan.domain.plan.ForecastInput
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.OutlookMath
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import app.mizan.domain.plan.SavingsMath
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatRecord
import app.mizan.domain.stats.StatsEngine
import app.mizan.ui.charts.ChartPoint
import app.mizan.ui.charts.lineAreaChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun outlookScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val planned by repository.observePlannedPayments().collectAsStateWithLifecycle(emptyList())
    val balances by repository.observeAccountBalances().collectAsStateWithLifecycle(emptyList())
    val records by repository.observeStatsRows().collectAsStateWithLifecycle(emptyList())
    val categories by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    var typicalIncome by remember { mutableStateOf(false) }
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val end = today.withDayOfMonth(today.lengthOfMonth())
    val forecast by produceState<app.mizan.domain.plan.Forecast?>(null, planned, balances, records, categories, typicalIncome) {
        value =
            withContext(Dispatchers.Default) {
                val occurrences = plannedOccurrences(planned, today.plusDays(1), end)
                val plannedIncome = occurrences.filter { it.payment.kind == "INCOME" }.sumOf { it.payment.amountMinor }
                val plannedExpense = occurrences.filter { it.payment.kind == "EXPENSE" }.sumOf { it.payment.amountMinor }
                val excluded = categories.filter { it.excludeFromForecast }.map { it.id }.toSet()
                val daily =
                    records
                        .filter {
                            it.kind == "EXPENSE" && it.plannedId == null &&
                                LocalDate.parse(it.bookedDate) >= today.minusDays(89) && it.categoryId !in excluded
                        }.groupBy { it.bookedDate }
                        .values
                        .map { day -> day.sumOf { kotlin.math.abs(it.baseMinor) } }
                val start =
                    balances.sumOf { row ->
                        app.mizan.domain.money.MoneyMath.baseMinor(
                            row.openingMinor + row.entryMinor,
                            row.decimals,
                            java.math.BigDecimal(row.egpPerUnit),
                        )
                    }
                val currentMonth =
                    StatsEngine.cashFlow(
                        records.map {
                            StatRecord(
                                it.id,
                                LocalDate.parse(it.bookedDate),
                                runCatching {
                                    StatKind.valueOf(it.kind)
                                }.getOrDefault(StatKind.OTHER),
                                it.baseMinor,
                                it.accountId,
                            )
                        },
                        Period(today.withDayOfMonth(1), today),
                    )
                OutlookMath.forecast(
                    ForecastInput(
                        start,
                        plannedIncome,
                        plannedExpense,
                        OutlookMath.cappedDailyRunRate(daily),
                        java.time.temporal.ChronoUnit.DAYS
                            .between(today, end)
                            .toInt(),
                        currentMonth.incomeMinor,
                        typicalIncome,
                    ),
                )
            }
    }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Outlook")
        FilterChip(typicalIncome, { typicalIncome = !typicalIncome }, { Text("Include typical income (assumption)") })
        forecast?.let { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Forecast waterfall through $end")
                    Text("Start ${result.startMinor} · planned ${result.plannedMinor}")
                    Text("Unplanned −${result.unplannedMinor} · expected income ${result.expectedIncomeMinor}")
                    Text("Projected end ${result.endMinor} EGP minor")
                }
            }
        } ?: Text("Calculating forecast…")
        Text("Planned timeline")
        var running = forecast?.startMinor ?: 0L
        plannedOccurrences(planned, today, end).take(20).forEach { occurrence ->
            running +=
                when (occurrence.payment.kind) {
                    "INCOME" -> occurrence.payment.amountMinor
                    "EXPENSE" -> -occurrence.payment.amountMinor
                    else -> 0L
                }
            Text(
                "${occurrence.date} · ${occurrence.payment.name} · ${occurrence.payment.kind.lowercase()} · running $running EGP minor",
            )
        }
    }
}

@Composable
fun creditAndSavingsScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val balances by repository.observeAccountBalances().collectAsStateWithLifecycle(emptyList())
    val rows by repository.observeStatsRows().collectAsStateWithLifecycle(emptyList())
    val categories by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    val period = Period(today.withDayOfMonth(1), today)
    val records =
        rows.map {
            StatRecord(
                it.id,
                LocalDate.parse(it.bookedDate),
                runCatching {
                    StatKind.valueOf(it.kind)
                }.getOrDefault(StatKind.OTHER),
                it.baseMinor,
                it.accountId,
                it.categoryId,
            )
        }
    val flow = StatsEngine.cashFlow(records, period)
    val cards =
        accounts.filter { it.type == "CREDIT_CARD" }.map { account ->
            val balance =
                balances.filter { it.accountId == account.id }.sumOf { row ->
                    app.mizan.domain.money.MoneyMath.baseMinor(
                        row.openingMinor + row.entryMinor,
                        row.decimals,
                        java.math.BigDecimal(row.egpPerUnit),
                    )
                }
            CreditCardBalance(account.creditLimitMinor ?: 0L, balance)
        }
    val debtService =
        records
            .filter {
                it.kind == StatKind.EXPENSE && it.date in period.start..period.end &&
                    it.categoryId in categories.filter { category -> category.isDebtService }.map { category -> category.id }.toSet()
            }.sumOf { kotlin.math.abs(it.baseMinor) }
    val buys =
        rows
            .filter {
                it.kind == "ASSET_BUY" && LocalDate.parse(it.bookedDate) in period.start..period.end
            }.sumOf { kotlin.math.abs(it.baseMinor) }
    val sells =
        rows
            .filter {
                it.kind == "ASSET_SELL" && LocalDate.parse(it.bookedDate) in period.start..period.end
            }.sumOf { kotlin.math.abs(it.baseMinor) }
    val savingsSeries =
        (5 downTo 0).map { offset ->
            val month = today.minusMonths(offset.toLong())
            val monthPeriod = Period(month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth()))
            val monthFlow = StatsEngine.cashFlow(records, monthPeriod)
            ChartPoint(month.month.name.take(3), SavingsMath.savingsRate(monthFlow.netMinor, monthFlow.incomeMinor) ?: 0L)
        }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Credit & savings")
        Text("Credit utilization: ${CreditMath.utilization(cards)?.let { "$it bp" } ?: "—"}")
        accounts.filter { it.type == "CREDIT_CARD" }.forEach { account ->
            val card = cards[accounts.filter { it.type == "CREDIT_CARD" }.indexOf(account)]
            val owed = (-card.balanceMinor).coerceAtLeast(0)
            Text(
                "${account.name} · owed $owed EGP minor · limit ${card.limitMinor} minor · " +
                    "${CreditMath.perCardUtilization(card)?.let { "$it bp" } ?: "—"}",
            )
        }
        Text("Debt-to-income: ${CreditMath.debtToIncome(debtService, flow.incomeMinor)?.let { "$it bp" } ?: "—"}")
        Text("Savings rate: ${SavingsMath.savingsRate(flow.netMinor, flow.incomeMinor)?.let { "$it bp" } ?: "—"}")
        Text("Invest rate: ${SavingsMath.investRate(buys, sells, flow.incomeMinor)?.let { "$it bp" } ?: "—"}")
        Text("Six-period savings-rate trend")
        lineAreaChart(savingsSeries, { "$it bp" }, Modifier.fillMaxWidth())
    }
}

private data class TimelineOccurrence(
    val payment: PlannedPaymentEntity,
    val date: LocalDate,
)

private fun plannedOccurrences(
    payments: List<PlannedPaymentEntity>,
    start: LocalDate,
    end: LocalDate,
): List<TimelineOccurrence> =
    payments
        .flatMap { payment ->
            RecurrenceEngine
                .occurrences(
                    Recurrence(
                        Frequency.valueOf(payment.frequency),
                        payment.interval,
                        LocalDate.parse(payment.startDate),
                        payment.endDate?.let(LocalDate::parse),
                    ),
                    start,
                    end,
                ).map { TimelineOccurrence(payment, it) }
        }.sortedBy { it.date }
