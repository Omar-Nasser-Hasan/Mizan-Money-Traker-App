// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeSummaryCalculatorTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun `calculates every Home headline from ledger data`() {
        val records =
            listOf(
                StatRecord("income", today.minusDays(2), StatKind.INCOME, 12_580_00, "cash"),
                StatRecord("expense", today.minusDays(1), StatKind.EXPENSE, -2_500_00, "cash", categoryId = "food"),
                StatRecord("uncategorized", today, StatKind.EXPENSE, -500_00, "cash"),
                StatRecord("buy", today, StatKind.OTHER, -1_000_00, "cash"),
                StatRecord("prior", today.minusMonths(1).withDayOfMonth(3), StatKind.EXPENSE, -1_000_00, "cash"),
            )
        val home =
            HomeSummaryCalculator.calculate(
                records = records,
                accounts = listOf(HomeAccountValue("cash", "Cash", 20_000_00), HomeAccountValue("bank", "Bank", 5_000_00)),
                holdingsMinor = 3_000_00,
                debts = listOf(HomeDebtValue("LENT", 1_000_00, 200_00), HomeDebtValue("BORROWED", 700_00, 100_00)),
                investedMinor = 1_000_00,
                month = Period(today.withDayOfMonth(1), today),
                comparison = Period(today.minusMonths(1).withDayOfMonth(1), today.minusMonths(1).withDayOfMonth(18)),
                thirtyDays = Period(today.minusDays(29), today),
            )
        assertEquals(28_200_00, home.netWorthMinor)
        assertEquals(25_000_00, home.cashMinor)
        assertEquals(12_580_00, home.cashFlow.incomeMinor)
        assertEquals(3_000_00, home.cashFlow.expenseMinor)
        assertEquals(9_580_00, home.cashFlow.netMinor)
        assertEquals(1_000_00, home.investedMinor)
        assertEquals(2, home.uncategorizedExpenseCount)
        assertEquals(listOf(20_000_00L, 5_000_00L), home.accountTotals.map { it.egpMinor })
        assertEquals(30, home.sparkline.size)
    }

    @Test
    fun `Home banners appear only for actionable data`() {
        assertEquals(true, HomeBanners.showUncategorized(1))
        assertEquals(false, HomeBanners.showUncategorized(0))
        assertEquals(true, HomeBanners.showStaleGold(24))
        assertEquals(false, HomeBanners.showStaleGold(23))
        assertEquals(false, HomeBanners.showStaleGold(null))
    }
}
