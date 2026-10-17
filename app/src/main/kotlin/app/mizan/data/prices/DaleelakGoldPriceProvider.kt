// Copyright (c) 2026 Mizan contributors
package app.mizan.data.prices

import app.mizan.domain.ports.BuySellPrice
import app.mizan.domain.ports.GoldPriceProvider
import app.mizan.domain.ports.GoldPriceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** Product implementation of SPIKE-G1's conditional-pass provider. It returns null on every failure. */
class DaleelakGoldPriceProvider(
    private val client: OkHttpClient,
    private val feedUrl: String = FEED_URL,
) : GoldPriceProvider {
    override suspend fun latest(): GoldPriceSnapshot? =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(feedUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val assets =
                        Json
                            .parseToJsonElement(response.body.string())
                            .jsonObject
                            .getValue("assets")
                            .jsonArray

                    fun item(id: String) = assets.first { it.jsonObject["id"]?.jsonPrimitive?.content == id }.jsonObject

                    fun price(id: String): BuySellPrice {
                        val directions = item(id).getValue("directions").jsonObject

                        fun side(name: String): Long =
                            BigDecimal(
                                directions
                                    .getValue(name)
                                    .jsonObject
                                    .getValue("best")
                                    .jsonObject
                                    .getValue("value")
                                    .jsonPrimitive.content,
                            ).movePointRight(2)
                                .setScale(0, RoundingMode.HALF_UP)
                                .longValueExact()
                        return BuySellPrice(side("buy"), side("sell"))
                    }
                    GoldPriceSnapshot(
                        karats = mapOf(24 to price("GOLD24K-EGP"), 21 to price("GOLD21K-EGP"), 18 to price("GOLD18K-EGP")),
                        pound = price("GOLDPOUND-EGP"),
                        asOf = Instant.parse(item("GOLD24K-EGP").getValue("observedAt").jsonPrimitive.content),
                        source = "Daleelak public feed (dealer aggregation)",
                    )
                }
            }.getOrNull()
        }

    private companion object {
        const val FEED_URL = "https://getdaleelak.com/api/v1/feed.json"
    }
}
