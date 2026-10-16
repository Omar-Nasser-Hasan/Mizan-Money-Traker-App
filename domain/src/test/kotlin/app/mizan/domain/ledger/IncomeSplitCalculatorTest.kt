// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ledger

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeSplitCalculatorTest {
    @Test fun percent_split_posts_exact_three_leg_income() {
        val result =
            IncomeSplitCalculator.allocate(
                1_258_000,
                listOf(percent("essential", 1_000), percent("fun", 4_000), percent("investment", 5_000)),
            )
        assertEquals(listOf(125_800L, 503_200L, 629_000L), result.allocations.map { it.amountMinor })
        val posting =
            LedgerService { "id" }.postSplitIncome(
                SplitIncomeRequest(result.allocations, "EGP", 2, BigDecimal.ONE, Instant.EPOCH, LocalDate.of(2026, 1, 1)),
            )
        assertEquals(TxnKind.INCOME, posting.transaction.kind)
        assertEquals(1_258_000L, posting.entries.sumOf { it.amountMinor })
    }

    @Test fun fixed_lines_clamp_then_remainder_receives_unallocated_income() {
        val result =
            IncomeSplitCalculator.allocate(
                100,
                listOf(fixed("a", 80), fixed("b", 80)),
                remainderAccountId = "a",
            )
        assertTrue(result.fixedAmountsClamped)
        assertEquals(mapOf("a" to 80L, "b" to 20L), result.allocations.associate { it.accountId to it.amountMinor })
    }

    @Test fun percentage_rounding_leftover_goes_to_largest_line() {
        val result = IncomeSplitCalculator.allocate(101, listOf(percent("a", 5_000), percent("b", 5_000)))
        assertEquals(listOf(51L, 50L), result.allocations.map { it.amountMinor })
    }

    @Test fun property_allocations_always_sum_to_income() {
        val random = Random(42)
        repeat(1_000) {
            val amount = random.nextLong(1, 10_000_000)
            val first = random.nextLong(0, 10_001)
            val second = random.nextLong(0, 10_001 - first)
            val lines =
                listOf(
                    fixed("fixed", random.nextLong(0, amount * 2)),
                    percent("one", first),
                    percent("two", second),
                )
            val result = IncomeSplitCalculator.allocate(amount, lines, "fixed")
            assertEquals(amount, result.allocations.sumOf { allocation -> allocation.amountMinor })
        }
    }

    private fun fixed(account: String, amount: Long) = SplitLine(account, "template", account, SplitMode.FIXED, amount)

    private fun percent(account: String, basisPoints: Long) = SplitLine(account, "template", account, SplitMode.PERCENT, basisPoints)
}
