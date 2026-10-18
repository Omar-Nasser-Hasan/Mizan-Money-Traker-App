// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.PlannedOccurrenceEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.db.StatsEntryRow
import app.mizan.data.db.StatsRecordRow
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.BudgetMath
import app.mizan.domain.plan.BudgetMathInput
import app.mizan.domain.plan.BudgetPeriod
import app.mizan.domain.plan.BudgetStatus
import app.mizan.domain.plan.Frequency
import app.mizan.domain.plan.OutlookMath
import app.mizan.domain.plan.Recurrence
import app.mizan.domain.plan.RecurrenceEngine
import app.mizan.domain.plan.Rollover
import app.mizan.domain.plan.budgetWindow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class BudgetBreakdown(
    val name: String,
    val amountMinor: Long,
)

internal data class BudgetCardData(
    val budget: BudgetEntity,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val spentMinor: Long,
    val result: app.mizan.domain.plan.BudgetMathResult,
    val status: BudgetStatus,
    val paceMinor: Long,
    val remainingDays: Int,
    val categories: List<BudgetBreakdown>,
    val payees: List<BudgetBreakdown>,
)

internal data class BudgetsPageData(
    val spentMinor: Long,
    val limitMinor: Long,
    val percentBasisPoints: Long,
    val paceMinor: Long,
    val leftMinor: Long,
    val remainingDays: Int,
    val cards: List<BudgetCardData>,
)

internal data class PlannedOccurrence(
    val payment: PlannedPaymentEntity?,
    val debt: DebtEntity?,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val isIncome: Boolean,
    val isOut: Boolean,
    val isReadOnly: Boolean,
) {
    val name: String get() = payment?.name ?: "Debt · ${debt?.person.orEmpty()}"
}

internal data class PlannedPageData(
    val next7OutMinor: Long,
    val next7InMinor: Long,
    val overdueCount: Int,
    val overdue: List<PlannedOccurrence>,
    val next7: List<PlannedOccurrence>,
    val later: List<PlannedOccurrence>,
    val forecastEndMinor: Long,
)

internal object PlanCalculations {
    fun budgets(
        budgets: List<BudgetEntity>,
        rows: List<StatsRecordRow>,
        entries: List<StatsEntryRow>,
        payments: List<PlannedPaymentEntity>,
        occurrences: List<PlannedOccurrenceEntity>,
        categories: List<CategoryEntity>,
        payees: List<PayeeEntity>,
        currencies: List<CurrencyEntity>,
        rates: List<FxRateEntity>,
        period: BudgetPeriod,
        anchor: LocalDate,
        today: LocalDate,
    ): BudgetsPageData {
        val entryAccounts = entries.groupBy { it.txnId }
        val categoryNames = categories.associate { it.id to it.name }
        val payeeNames = payees.associate { it.id to it.name }
        val cards =
            budgets
                .filter { !it.archived && it.period == period.name }
                .mapNotNull { budget ->
                    runCatching {
                        val window =
                            if (period == BudgetPeriod.ONE_TIME) {
                                budgetWindow(period, anchor, budget.startDate?.let(LocalDate::parse), budget.endDate?.let(LocalDate::parse))
                            } else {
                                budgetWindow(period, anchor)
                            }
                        val ids =
                            budget.scopeIds
                                .split(',')
                                .filter(String::isNotBlank)
                                .toSet()

                        fun inScope(row: StatsRecordRow): Boolean {
                            if (budget.scope == "ALL") return true
                            return when (budget.scope) {
                                "CATEGORY" -> row.categoryId in ids
                                "ACCOUNT" -> entryAccounts[row.id].orEmpty().any { it.accountId in ids }
                                else -> false
                            }
                        }
                        val currentThrough = today.coerceIn(window.start, window.end)
                        val priorWindow =
                            if (period == BudgetPeriod.ONE_TIME) {
                                null
                            } else {
                                budgetWindow(period, window.start.minusDays(1))
                            }

                        fun spend(
                            start: LocalDate,
                            end: LocalDate,
                        ): Long =
                            rows
                                .filter { it.kind == "EXPENSE" && LocalDate.parse(it.bookedDate) in start..end && inScope(it) }
                                .sumOf { kotlin.math.abs(it.baseMinor) }
                        val spent = if (today < window.start) 0L else spend(window.start, currentThrough)
                        val previousSpent = priorWindow?.let { spend(it.start, it.end) } ?: 0L
                        val elapsedDays = maxOf(1, ChronoUnit.DAYS.between(window.start, currentThrough).toInt() + 1)
                        val remainingDays = maxOf(0, ChronoUnit.DAYS.between(maxOf(today, window.start), window.end).toInt())
                        val plannedRemaining =
                            if (today >= window.end) {
                                0L
                            } else {
                                payments
                                    .filter { it.active && it.kind == "EXPENSE" && inScopePayment(it, budget, ids) }
                                    .sumOf { payment ->
                                        val dates =
                                            RecurrenceEngine.occurrences(
                                                Recurrence(
                                                    Frequency.valueOf(payment.frequency),
                                                    payment.interval,
                                                    LocalDate.parse(payment.startDate),
                                                    payment.endDate?.let(LocalDate::parse),
                                                ),
                                                maxOf(today.plusDays(1), window.start),
                                                window.end,
                                            )
                                        dates
                                            .filterNot { date ->
                                                occurrences.any {
                                                    it.plannedId == payment.id && it.dueDate == date.toString() &&
                                                        it.status in setOf("PAID", "SKIPPED")
                                                }
                                            }.sumOf { convert(payment.amountMinor, payment.currency, currencies, rates) }
                                    }
                            }
                        val result =
                            BudgetMath.calculate(
                                BudgetMathInput(
                                    budget.limitMinor,
                                    spent,
                                    budget.limitMinor,
                                    previousSpent,
                                    Rollover.valueOf(budget.rollover),
                                    plannedRemaining,
                                    elapsedDays,
                                    remainingDays,
                                ),
                            )
                        val periodRows =
                            rows.filter {
                                it.kind == "EXPENSE" && LocalDate.parse(it.bookedDate) in window.start..currentThrough && inScope(it)
                            }
                        val categoryBreakdown =
                            periodRows
                                .groupBy { it.categoryId ?: "uncategorized" }
                                .map { (id, grouped) ->
                                    BudgetBreakdown(categoryNames[id] ?: "Uncategorized", grouped.sumOf { kotlin.math.abs(it.baseMinor) })
                                }.sortedByDescending { it.amountMinor }
                        val payeeBreakdown =
                            periodRows
                                .filter { it.payeeId != null }
                                .groupBy { it.payeeId!! }
                                .map { (id, grouped) ->
                                    BudgetBreakdown(payeeNames[id] ?: "Unknown payee", grouped.sumOf { kotlin.math.abs(it.baseMinor) })
                                }.sortedByDescending { it.amountMinor }
                        BudgetCardData(
                            budget,
                            window.start,
                            window.end,
                            spent,
                            result,
                            BudgetMath.status(spent, result),
                            if (today <
                                window.start
                            ) {
                                0L
                            } else {
                                spent * (ChronoUnit.DAYS.between(window.start, window.end).toInt() + 1) / elapsedDays
                            },
                            remainingDays,
                            categoryBreakdown,
                            payeeBreakdown,
                        )
                    }.getOrNull()
                }
        val spent = cards.sumOf { it.spentMinor }
        val limit = cards.sumOf { it.result.effectiveLimitMinor }
        val days = cards.maxOfOrNull { it.remainingDays } ?: 0
        return BudgetsPageData(
            spent,
            limit,
            if (limit > 0L) spent * 10_000L / limit else 0L,
            cards.sumOf { it.paceMinor },
            limit - spent,
            days,
            cards,
        )
    }

    fun planned(
        payments: List<PlannedPaymentEntity>,
        occurrences: List<PlannedOccurrenceEntity>,
        debts: List<DebtEntity>,
        balances: List<AccountBalanceRow>,
        accounts: List<AccountEntity>,
        rows: List<StatsRecordRow>,
        entries: List<StatsEntryRow>,
        categories: List<CategoryEntity>,
        currencies: List<CurrencyEntity>,
        rates: List<FxRateEntity>,
        today: LocalDate,
    ): PlannedPageData {
        val to = today.plusDays(6)
        val known = occurrences.associateBy { it.plannedId to it.dueDate }
        val recurring =
            payments.filter { it.active }.flatMap { payment ->
                val end = minOf(today.plusMonths(12), payment.endDate?.let(LocalDate::parse) ?: today.plusMonths(12))
                RecurrenceEngine
                    .occurrences(
                        Recurrence(
                            Frequency.valueOf(payment.frequency),
                            payment.interval,
                            LocalDate.parse(payment.startDate),
                            payment.endDate?.let(LocalDate::parse),
                        ),
                        LocalDate.parse(payment.startDate),
                        end,
                    ).filter { date -> known[payment.id to date.toString()]?.status !in setOf("PAID", "SKIPPED") }
                    .map { date ->
                        val income = payment.kind == "INCOME"
                        PlannedOccurrence(
                            payment,
                            null,
                            date,
                            convert(payment.amountMinor, payment.currency, currencies, rates),
                            income,
                            !income && payment.kind in setOf("EXPENSE", "TRANSFER"),
                            false,
                        )
                    }
            }
        val debtItems =
            debts.filter { it.status == "ACTIVE" && it.dueDate != null }.mapNotNull { debt ->
                val date = runCatching { LocalDate.parse(debt.dueDate) }.getOrNull() ?: return@mapNotNull null
                if (date < today.minusDays(30) || date > today.plusMonths(12)) return@mapNotNull null
                val income = debt.direction == "LENT"
                PlannedOccurrence(
                    debt = debt,
                    payment = null,
                    dueDate = date,
                    amountMinor = convert(debt.principalMinor, debt.currency, currencies, rates),
                    isIncome = income,
                    isOut = !income,
                    isReadOnly = true,
                )
            }
        val all = (recurring + debtItems).sortedWith(compareBy<PlannedOccurrence> { it.dueDate }.thenBy { it.name })
        val overdue = all.filter { it.dueDate < today }
        val next = all.filter { it.dueDate in today..to }
        val later = all.filter { it.dueDate > to }
        val out = next.filter { it.isOut }.sumOf { it.amountMinor }
        val incoming = next.filter { it.isIncome }.sumOf { it.amountMinor }
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val forecast =
            OutlookStats
                .create(
                    rows,
                    balances,
                    accounts,
                    categories,
                    payments,
                    occurrences,
                    entries,
                    currencies,
                    rates,
                    app.mizan.domain.stats
                        .Period(today, monthEnd),
                    monthEnd,
                    today,
                    null,
                    false,
                ).forecast.endMinor
        return PlannedPageData(out, incoming, overdue.size, overdue, next, later, forecast)
    }

    private fun inScopePayment(
        payment: PlannedPaymentEntity,
        budget: BudgetEntity,
        ids: Set<String>,
    ): Boolean =
        when (budget.scope) {
            "ALL" -> true
            "CATEGORY" -> payment.categoryId in ids
            "ACCOUNT" -> payment.accountId in ids
            else -> false
        }

    private fun convert(
        amountMinor: Long,
        currency: String,
        currencies: List<CurrencyEntity>,
        rates: List<FxRateEntity>,
    ): Long {
        val decimals = currencies.firstOrNull { it.code == currency }?.decimals ?: 2
        val rate =
            if (currency ==
                "EGP"
            ) {
                BigDecimal.ONE
            } else {
                rates
                    .filter { it.currency == currency }
                    .maxByOrNull { it.date }
                    ?.egpPerUnit
                    ?.let(::BigDecimal)
                    ?: return 0L
            }
        return MoneyMath.baseMinor(amountMinor, decimals, rate)
    }
}
