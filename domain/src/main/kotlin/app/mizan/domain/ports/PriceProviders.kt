// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ports

import java.time.Instant

/** Throwaway M0 contract: production price policy is intentionally out of scope. */
interface GoldPriceProvider {
    suspend fun latest(): GoldPriceSnapshot?
}

data class GoldPriceSnapshot(
    val karats: Map<Int, BuySellPrice>,
    val pound: BuySellPrice,
    val asOf: Instant,
    val source: String,
)

data class BuySellPrice(
    val buyEgp: Long,
    val sellEgp: Long,
)

/** Throwaway M0 contract: callers retain the last value if this returns null. */
interface QuoteProvider {
    suspend fun quotes(tickers: List<String>): List<Quote>
}

data class Quote(
    val ticker: String,
    val lastPriceMinor: Long,
    val asOf: Instant,
    val source: String,
)
