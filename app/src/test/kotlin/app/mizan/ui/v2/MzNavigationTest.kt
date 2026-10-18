// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class MzNavigationTest {
    @Test
    fun `bottom navigation matches the approved mizan home labels and icon ligatures`() {
        assertEquals(
            listOf(
                MzNavItem("Home", "account_balance_wallet"),
                MzNavItem("Records", "receipt_long"),
                MzNavItem("Stats", "monitoring"),
                MzNavItem("Plan", "pie_chart"),
                MzNavItem("Wealth", "savings"),
            ),
            MzRootNavItems,
        )
    }
}
