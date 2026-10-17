// Copyright (c) 2026 Mizan contributors
package app.mizan.data.prices

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DaleelakGoldPriceProviderTest {
    @Test fun `unavailable dealer feed returns null without fabricating a price`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(429))
            server.start()
            try {
                assertNull(DaleelakGoldPriceProvider(OkHttpClient(), server.url("/").toString()).latest())
            } finally {
                server.shutdown()
            }
        }

    @Test fun `manual quote provider always preserves manual path`() =
        runBlocking {
            assertEquals(emptyList<Any>(), ManualQuoteProvider().quotes(listOf("COMI", "TMGH")))
        }

    @Test fun `dealer feed maps buy and sell prices in EGP minor units`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(FEED))
            server.start()
            try {
                val snapshot = DaleelakGoldPriceProvider(OkHttpClient(), server.url("/").toString()).latest()
                assertNotNull(snapshot)
                assertEquals(6_230_00L, snapshot!!.karats.getValue(21).buyEgp)
                assertEquals(50_000_00L, snapshot.pound.sellEgp)
            } finally {
                server.shutdown()
            }
        }

    private companion object {
        const val FEED = """{"assets":[
            {"id":"GOLD24K-EGP","observedAt":"2026-10-17T10:00:00Z","directions":{"buy":{"best":{"value":"7120"}},"sell":{"best":{"value":"7132"}}}},
            {"id":"GOLD21K-EGP","directions":{"buy":{"best":{"value":"6230"}},"sell":{"best":{"value":"6242"}}}},
            {"id":"GOLD18K-EGP","directions":{"buy":{"best":{"value":"5340"}},"sell":{"best":{"value":"5323"}}}},
            {"id":"GOLDPOUND-EGP","directions":{"buy":{"best":{"value":"49840"}},"sell":{"best":{"value":"50000"}}}}
        ]}"""
    }
}
