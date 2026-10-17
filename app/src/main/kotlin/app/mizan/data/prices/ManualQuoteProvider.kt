// Copyright (c) 2026 Mizan contributors
package app.mizan.data.prices

import app.mizan.domain.ports.Quote
import app.mizan.domain.ports.QuoteProvider

/** SPIKE-S1 failed: this intentionally keeps EGX and funds on the manual-price path. */
class ManualQuoteProvider : QuoteProvider {
    override suspend fun quotes(tickers: List<String>): List<Quote> = emptyList()
}
