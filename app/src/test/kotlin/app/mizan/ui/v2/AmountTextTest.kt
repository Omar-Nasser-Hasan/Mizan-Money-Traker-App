// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {
    @Test
    fun financial_change_captions_mask_absolute_amounts_and_percentages() {
        val absolute =
            app.mizan.domain.stats
                .compare(630_000, -450_000, cashFlow = true)
        val percent =
            app.mizan.domain.stats
                .compare(630_000, 450_000)
        assertEquals("EGP 10,800.00 absolute change", changeText(absolute, false))
        assertEquals("↑ 40.00% vs previous period", changeText(percent, false))
        assertEquals("••••••", changeText(absolute, true))
        assertEquals("••••••", changeText(percent, true))
    }

    @Test
    fun rendersTwoDecimalWesternDigitAmountsWithTheRequiredSigns() {
        assertEquals("− EGP 12,580.00", formatMizanAmount(-1_258_000, "EGP", AmountStyle.EXPENSE, false))
        assertEquals("+ EGP 12,580.00", formatMizanAmount(1_258_000, "EGP", AmountStyle.INCOME, false))
        assertEquals("→ USD 125.80", formatMizanAmount(12_580, "USD", AmountStyle.TRANSFER, false))
        assertEquals("Invested EGP 125.80", formatMizanAmount(12_580, "EGP", AmountStyle.INVESTMENT, false))
        assertEquals("KWD 12.580", formatMizanAmount(12_580, "KWD", AmountStyle.NEUTRAL, false))
        assertEquals("JPY 12,580", formatMizanAmount(12_580, "JPY", AmountStyle.NEUTRAL, false))
    }

    @Test
    fun hidesButRetainsCurrencyWhenAmountsAreHidden() {
        assertEquals("EGP ••••••", formatMizanAmount(Long.MIN_VALUE, "EGP", AmountStyle.EXPENSE, true))
    }

    @Test
    fun formatsDomainMoneyAtItsCurrencyMinorUnitScale() {
        assertEquals("EGP 12,580.00", formatMizanAmount(Money(1_258_000, "EGP")))
    }

    @Test
    fun supportsCurrencyOptionalListRowsHeroPartsHiddenAndNegativeValues() {
        assertEquals("− 280.00", formatMizanAmount(-28_000, "EGP", AmountStyle.EXPENSE, false, showCurrency = false))
        assertEquals("+ 100.00", formatMizanAmount(10_000, "EGP", AmountStyle.INCOME, false, showCurrency = false))
        assertEquals("USD 0.05", formatMizanAmount(5, "USD", AmountStyle.NEUTRAL, false))
        assertEquals("EGP 1,234,567.89", formatMizanAmount(123_456_789, "EGP", AmountStyle.NEUTRAL, false))
        assertEquals("EGP ••••••", formatMizanAmount(1_258_000, "EGP", AmountStyle.NEUTRAL, true))
        assertEquals("••••••", formatMizanAmount(1_258_000, "EGP", AmountStyle.NEUTRAL, true, showCurrency = false))
        assertEquals("12,580" to ".00", heroAmountParts(1_258_000, "EGP"))
        assertEquals("1,234,567" to ".89", heroAmountParts(123_456_789, "EGP"))
        assertEquals("− 280" to ".00", heroAmountParts(-28_000, "EGP"))
    }
}
