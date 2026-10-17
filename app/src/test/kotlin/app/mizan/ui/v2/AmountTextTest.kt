// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {
    @Test
    fun rendersTwoDecimalWesternDigitAmountsWithTheRequiredSigns() {
        assertEquals("− EGP 12,580.00", formatMizanAmount(-1_258_000, "EGP", AmountStyle.EXPENSE, false))
        assertEquals("+ EGP 12,580.00", formatMizanAmount(1_258_000, "EGP", AmountStyle.INCOME, false))
        assertEquals("→ USD 125.80", formatMizanAmount(12_580, "USD", AmountStyle.TRANSFER, false))
        assertEquals("Invested EGP 125.80", formatMizanAmount(12_580, "EGP", AmountStyle.INVESTMENT, false))
    }

    @Test
    fun hidesButRetainsCurrencyWhenAmountsAreHidden() {
        assertEquals("EGP ••••••", formatMizanAmount(Long.MIN_VALUE, "EGP", AmountStyle.EXPENSE, true))
    }
}
