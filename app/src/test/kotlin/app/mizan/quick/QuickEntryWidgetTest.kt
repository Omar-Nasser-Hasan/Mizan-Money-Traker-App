// Copyright (c) 2026 Mizan contributors
package app.mizan.quick

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickEntryWidgetTest {
    @Test fun glance_balance_honors_hidden_amounts_and_uses_currency_decimals() {
        assertEquals("EGP 1,234.56", widgetAmountText(123_456, "EGP", false))
        assertEquals("EGP ••••••", widgetAmountText(123_456, "EGP", true))
        assertEquals("KWD 1.235", widgetAmountText(1_235, "KWD", false))
    }
}
