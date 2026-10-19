// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.DebtRepaymentRow
import app.mizan.data.db.FxRateEntity
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.DebtBalance
import app.mizan.domain.stats.HomeDebtValue
import java.math.BigDecimal
import java.time.LocalDate

/** Maps remaining debt units to EGP once; the domain calculators own outstanding and FX math. */
internal fun valuedDebtBalances(
    debts: List<DebtEntity>,
    repayments: List<DebtRepaymentRow>,
    currencies: List<CurrencyEntity>,
    rates: List<FxRateEntity>,
    date: LocalDate,
): List<HomeDebtValue> =
    debts.filter { it.status != "DELETED" && LocalDate.parse(it.openedAt) <= date }.mapNotNull { debt ->
        val rate =
            if (debt.currency ==
                "EGP"
            ) {
                BigDecimal.ONE
            } else {
                rates
                    .filter {
                        it.currency == debt.currency && LocalDate.parse(
                            it.date,
                        ) <= date
                    }.maxByOrNull { it.date }
                    ?.egpPerUnit
                    ?.toBigDecimal()
                    ?: return@mapNotNull null
            }
        val paid =
            repayments
                .filter {
                    it.debtId == debt.id && LocalDate.parse(it.bookedDate) <= date
                }.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
        val decimals =
            currencies.firstOrNull { it.code == debt.currency }?.decimals ?: java.util.Currency
                .getInstance(debt.currency)
                .defaultFractionDigits
        val remaining = MoneyMath.baseMinor(DebtBalance(debt.principalMinor, paid).outstandingMinor, decimals, rate)
        HomeDebtValue(debt.direction, remaining, 0L)
    }
