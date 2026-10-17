// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ledger

import app.mizan.domain.money.MoneyMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class LedgerServiceTest {
    private val service = LedgerService { "id-${++nextId}" }
    private var nextId = 0
    private val instant = Instant.parse("2026-10-16T10:00:00Z")
    private val date = LocalDate.parse("2026-10-16")

    @Test fun expense_has_one_negative_entry_and_category() {
        val posting = service.postExpense(ExpenseRequest("cash", "EGP", 12_345, 2, BigDecimal.ONE, "groceries", instant, date))
        assertEquals(TxnKind.EXPENSE, posting.transaction.kind)
        assertEquals(-12_345, posting.entries.single().amountMinor)
        assertEquals("groceries", posting.transaction.categoryId)
    }

    @Test fun income_is_single_positive_entry() {
        val posting = service.postIncome(IncomeRequest("bank", "USD", 12_345, 2, BigDecimal("50"), instant, date))
        assertEquals(TxnKind.INCOME, posting.transaction.kind)
        assertEquals(12_345, posting.entries.single().amountMinor)
        assertEquals(617_250, posting.entries.single().baseMinor)
    }

    @Test fun transfer_keeps_two_signed_legs_and_implied_rate() {
        val posting =
            service.postTransfer(
                TransferRequest("cash", "USD", 10_000, 2, BigDecimal("50"), "bank", "EGP", 500_000, 2, BigDecimal.ONE, instant, date),
            )
        assertEquals(listOf(-10_000L, 500_000L), posting.entries.map { it.amountMinor })
        assertEquals(BigDecimal("50.000000000000"), posting.impliedRate)
    }

    @Test fun same_account_same_currency_transfer_is_rejected() {
        try {
            service.postTransfer(
                TransferRequest("cash", "EGP", 100, 2, BigDecimal.ONE, "cash", "EGP", 100, 2, BigDecimal.ONE, instant, date),
            )
            throw AssertionError("Expected invalid transfer")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test fun base_rounding_golden_dataset() {
        assertEquals(617_250, MoneyMath.baseMinor(12_345, 2, BigDecimal("50")))
        assertEquals(685_148, MoneyMath.baseMinor(12_345, 2, BigDecimal("55.5")))
        assertEquals(200_606, MoneyMath.baseMinor(12_345, 3, BigDecimal("162.5")))
        assertEquals(4_305, MoneyMath.baseMinor(123, 0, BigDecimal("0.35")))
    }

    @Test fun balances_include_opening_and_entries_per_bucket() {
        val balances =
            BalanceCalculator.byBucket(
                listOf(AccountBucket("cash", "EGP", 1_000), AccountBucket("cash", "USD", 200)),
                listOf(
                    Entry("1", "t", "cash", "EGP", -300, BigDecimal.ONE, -300),
                    Entry("2", "t", "cash", "USD", 50, BigDecimal("50"), 250_000),
                ),
            )
        assertEquals(700L, balances["cash" to "EGP"])
        assertEquals(250L, balances["cash" to "USD"])
    }

    @Test fun debt_postings_have_the_required_cash_direction() {
        val lent = service.postDebt(DebtRequest("cash", "EGP", 5_000, 2, BigDecimal.ONE, TxnKind.DEBT_LEND, instant, date))
        val repayment = service.postDebt(DebtRequest("cash", "EGP", 2_000, 2, BigDecimal.ONE, TxnKind.DEBT_REPAY_IN, instant, date))

        assertEquals(-5_000L, lent.entries.single().amountMinor)
        assertEquals(2_000L, repayment.entries.single().amountMinor)
    }

    @Test fun asset_buy_and_sell_are_cash_movements_not_expenses() {
        val buy = service.postAssetCash(AssetCashRequest("investment", "EGP", 12_000, 2, BigDecimal.ONE, TxnKind.ASSET_BUY, instant, date))
        val sell =
            service.postAssetCash(
                AssetCashRequest("investment", "EGP", 13_000, 2, BigDecimal.ONE, TxnKind.ASSET_SELL, instant, date),
            )
        assertEquals(TxnKind.ASSET_BUY, buy.transaction.kind)
        assertEquals(-12_000L, buy.entries.single().amountMinor)
        assertEquals(TxnKind.ASSET_SELL, sell.transaction.kind)
        assertEquals(13_000L, sell.entries.single().amountMinor)
    }

    @Test fun calculator_respects_precedence_and_guards_zero_division() {
        assertEquals(BigDecimal("7"), EntryCalculator.evaluate("1+2×3"))
        try {
            EntryCalculator.evaluate("1÷0")
            throw AssertionError("Expected division by zero")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
