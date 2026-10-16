// Copyright (c) 2026 Mizan contributors
package app.mizan.spikes

import app.mizan.domain.ports.BuySellPrice
import app.mizan.domain.ports.GoldPriceProvider
import app.mizan.domain.ports.GoldPriceSnapshot
import app.mizan.domain.ports.Quote
import app.mizan.domain.ports.QuoteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * SPIKE-G1 only. Daleelak's public feed aggregates named Egyptian dealers (including iSagha),
 * supplies separately observed buy/sell prices, and documents its indicative-data caveat.
 */
class DaleelakGoldPriceProvider(
    private val client: OkHttpClient,
) : GoldPriceProvider {
    override suspend fun latest(): GoldPriceSnapshot? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(FEED_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val root = Json.parseToJsonElement(response.body.string()).jsonObject
                    val assets = root.getValue("assets").jsonArray

                    fun asset(id: String) = assets.first { it.jsonObject["id"]?.jsonPrimitive?.content == id }.jsonObject

                    fun price(asset: kotlinx.serialization.json.JsonObject): BuySellPrice {
                        val directions = asset.getValue("directions").jsonObject

                        fun side(name: String): Long =
                            directions
                                .getValue(name)
                                .jsonObject
                                .getValue("best")
                                .jsonObject
                                .getValue("value")
                                .jsonPrimitive.content
                                .toMinor()
                        return BuySellPrice(buyEgp = side("buy"), sellEgp = side("sell"))
                    }
                    val first = asset("GOLD24K-EGP")
                    GoldPriceSnapshot(
                        karats =
                            mapOf(
                                24 to price(first),
                                21 to price(asset("GOLD21K-EGP")),
                                18 to price(asset("GOLD18K-EGP")),
                            ),
                        pound = price(asset("GOLDPOUND-EGP")),
                        asOf = Instant.parse(first.getValue("observedAt").jsonPrimitive.content),
                        source = "Daleelak public feed (dealer aggregation)",
                    )
                }
            }.getOrNull()
        }

    private fun String.toMinor(): Long =
        BigDecimal(this)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private companion object {
        const val FEED_URL = "https://getdaleelak.com/api/v1/feed.json"
    }
}

/**
 * SPIKE-S1 only. The public ticker endpoint is deliberately allowed to fail; it returns an empty
 * list on a required token, schema drift, quota response, or network error.
 */
class EgiTickerQuoteProvider(
    private val client: OkHttpClient,
) : QuoteProvider {
    override suspend fun quotes(tickers: List<String>): List<Quote> =
        withContext(Dispatchers.IO) {
            tickers.mapNotNull { ticker -> fetch(ticker) }
        }

    private fun fetch(ticker: String): Quote? =
        runCatching {
            val url =
                BASE_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("SYMBOL_CODE", ticker)
                    .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = Json.parseToJsonElement(response.body.string()).jsonObject
                val rawPrice = body["LAST_PRICE"]?.jsonPrimitive?.content ?: return@use null
                Quote(ticker, rawPrice.toMinor(), Instant.now(), "ticker.egidegypt.com delayed feed")
            }
        }.getOrNull()

    private fun String.toMinor(): Long =
        BigDecimal(this)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private companion object {
        const val BASE_URL = "https://ticker.egidegypt.com/api/DelayedFeed/getMarketWatchForSymbol"
    }
}
