// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.GoalLinkEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.domain.plan.DebtBalance
import app.mizan.domain.plan.GoalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalDebtCalculationsTest {
    @Test
    fun linked_asset_value_matches_wealth_valuation_and_allocation() {
        val goal = GoalEntity("gold-goal", "Gold reserve", 1_000_000, icon = "savings", color = "#D8AF5A", createdAt = 0)
        val result =
            GoalDebtCalculations
                .goals(
                    goals = listOf(goal),
                    links = listOf(GoalLinkEntity("gold-goal", assetId = "gold", allocationPct = 10)),
                    contributions = emptyList(),
                    balances = emptyList(),
                    accounts = emptyList(),
                    assets = listOf(AssetEntity("gold", "Gold holdings", "GOLD", "21K", "USD", "g", "10", createdAt = 0, updatedAt = 0)),
                    trades = listOf(AssetTransactionEntity("buy", "gold", "BUY", "2026-09-01", "2", "100", 0, 20_000)),
                    prices = listOf(PriceSnapshotEntity("GOLD_21K", "gold", "100", "USD", 0, "manual", true)),
                    currencies = listOf(CurrencyEntity("USD", "US dollar", "$", 2, true)),
                    rates = listOf(FxRateEntity("2026-10-01", "USD", "50", "manual", 0)),
                    today = LocalDate.parse("2026-10-01"),
                ).single()

        assertEquals(90_000L, result.progress.linkedMinor)
    }

    @Test
    fun linked_egp_and_usd_balances_and_manual_contribution_match_golden_values() {
        val goal = GoalEntity("goal", "Trip", 1_000_000, "2026-12-01", "flight", "#D8AF5A", createdAt = 0)
        val balances =
            listOf(
                AccountBalanceRow("egp", "Cash", "EGP", 2, 100_000, 0, "1", "1"),
                AccountBalanceRow("usd", "USD wallet", "USD", 2, 10_000, 0, "50", "50"),
            )
        val links =
            listOf(
                GoalLinkEntity("goal", accountId = "egp", allocationPct = 50),
                GoalLinkEntity("goal", accountId = "usd", allocationPct = 50),
            )
        val result =
            GoalDebtCalculations
                .goals(
                    listOf(goal),
                    links,
                    listOf(GoalContributionEntity("deposit", "goal", "2026-10-01", 20_000)),
                    balances,
                    listOf(
                        AccountEntity("egp", "Cash", "CASH", "#000000", "wallet", "EGP", "2026-01-01", true, false, 0),
                        AccountEntity("usd", "USD wallet", "CASH", "#000000", "wallet", "USD", "2026-01-01", true, false, 1),
                    ),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    listOf(CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true), CurrencyEntity("USD", "US dollar", "$", 2, true)),
                    listOf(FxRateEntity("2026-10-01", "USD", "50", "manual", 0)),
                    LocalDate.parse("2026-10-01"),
                ).single()

        assertEquals(300_000L, result.progress.linkedMinor)
        assertEquals(20_000L, result.progress.manualMinor)
        assertEquals(320_000L, result.progress.progressMinor)
        assertEquals(340_000L, result.progress.monthlyNeeded(LocalDate.parse("2026-10-01")))
        assertEquals(LocalDate.parse("2026-12-01"), result.progress.eta(LocalDate.parse("2026-10-01"), 340_000))
        assertTrue(result.monthsBehind > 0L)
    }

    @Test
    fun allocation_cap_and_partial_debt_repayment_are_respected() {
        assertTrue(GoalMath.allocationIsValid(65, 35))
        assertFalse(GoalMath.allocationIsValid(65, 36))
        val balance = DebtBalance(25_000, 8_000)
        assertEquals(17_000L, balance.outstandingMinor)
        assertFalse(balance.isClosed)
        assertTrue(DebtBalance(25_000, 25_000).isClosed)
    }
}
