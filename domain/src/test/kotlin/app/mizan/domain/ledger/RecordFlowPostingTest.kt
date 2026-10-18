// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class RecordFlowPostingTest {
    private var nextId = 0
    private val service = LedgerService { "p-${++nextId}" }
    private val instant = Instant.parse("2026-10-18T10:00:00Z")
    private val date = LocalDate.parse("2026-10-18")

    @Test
    fun split_income_conserves_every_minor_unit_in_one_income_transaction() {
        val posting =
            service.postSplitIncome(
                SplitIncomeRequest(
                    allocations =
                        listOf(
                            IncomeAllocation("fun", 1_258_00),
                            IncomeAllocation("essential", 5_032_00),
                            IncomeAllocation("invest", 6_290_00),
                        ),
                    currency = "EGP",
                    currencyDecimals = 2,
                    rateToBase = BigDecimal.ONE,
                    bookedAt = instant,
                    bookedDate = date,
                ),
            )

        assertEquals(TxnKind.INCOME, posting.transaction.kind)
        assertEquals(12_580_00, posting.entries.sumOf { it.amountMinor })
        assertEquals(12_580_00, posting.entries.sumOf { it.baseMinor })
    }

    @Test
    fun transfer_fee_is_a_separate_expense_posting() {
        val fee = ExpenseRequest("cash", "EGP", 100, 2, BigDecimal.ONE, "fees", instant, date)
        val posting =
            service.postTransfer(
                TransferRequest("cash", "EGP", 5_000, 2, BigDecimal.ONE, "bank", "USD", 100, 2, BigDecimal("50"), instant, date, fee = fee),
            )

        assertEquals(listOf(-5_000L, 100L), posting.entries.map { it.amountMinor })
        assertEquals(TxnKind.EXPENSE, posting.feePosting?.transaction?.kind)
        assertEquals(
            -100L,
            posting.feePosting
                ?.entries
                ?.single()
                ?.amountMinor,
        )
    }
}
