// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

data class PaymentSourceReconciliation(
    val cashMinor: Long,
    val bankMinor: Long,
    val creditOwedMinor: Long,
    val unallocatedMinor: Long,
)

object PaymentSourceBalancesCalculator {
    /** Card debt is a liability, so it is shown separately and never reduces unclassified account cash. */
    fun reconcile(
        includedNonCardAccountsMinor: Long,
        cashMinor: Long,
        bankMinor: Long,
        creditOwedMinor: Long,
    ): PaymentSourceReconciliation {
        require(creditOwedMinor >= 0) { "Credit card amount owed cannot be negative" }
        val classified = Math.addExact(cashMinor, bankMinor)
        return PaymentSourceReconciliation(
            cashMinor = cashMinor,
            bankMinor = bankMinor,
            creditOwedMinor = creditOwedMinor,
            unallocatedMinor = Math.subtractExact(includedNonCardAccountsMinor, classified),
        )
    }
}
