// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import app.mizan.domain.entry.ParserAccount
import app.mizan.domain.entry.ParserCategory
import app.mizan.domain.entry.ParserContext
import app.mizan.domain.entry.VoiceParser
import app.mizan.domain.ledger.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class VoiceDraftCommitterTest {
    @Test fun different_booking_dates_keep_their_own_fx_rates() {
        val date = LocalDate.parse("2026-10-19")
        val first =
            VoiceParser
                .parse(
                    "spent 5 USD",
                    ParserContext(emptyList(), emptyList(), defaultAccountId = "cash", today = date),
                ).single()
        val second = first.copy(date = date.minusDays(1))
        val baseAmounts = mutableListOf<Long>()
        val committer = VoiceDraftCommitter { postings -> baseAmounts += postings.flatMap { it.entries }.map { it.baseMinor } }
        kotlinx.coroutines.runBlocking {
            committer.saveAll(
                listOf(first, second),
                setOf("cash"),
                emptyMap(),
                bookingRates =
                    mapOf(("USD" to first.date) to BigDecimal("50"), ("USD" to second.date) to BigDecimal("55")),
            )
        }
        assertEquals(listOf(-25_000L, -27_500L), baseAmounts)
    }

    @Test fun missing_currency_rate_prevents_every_ledger_write() {
        val draft =
            VoiceParser
                .parse(
                    "spent 5 USD",
                    ParserContext(emptyList(), emptyList(), defaultAccountId = "cash", today = LocalDate.parse("2026-10-19")),
                ).single()
        var writes = 0
        val committer = VoiceDraftCommitter { writes += it.size }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { committer.saveAll(listOf(draft), setOf("cash"), emptyMap()) }
        }
        assertEquals(0, writes)
    }

    @Test fun parser_drafts_post_to_fake_ledger_only_when_save_all_is_called() {
        val draft =
            VoiceParser
                .parse(
                    "spent 12.50 EGP groceries at Carrefour",
                    ParserContext(
                        accounts = listOf(ParserAccount("cash", setOf("Cash"))),
                        categories = listOf(ParserCategory("food", setOf("groceries", "food"))),
                        defaultAccountId = "cash",
                        today = LocalDate.parse("2026-10-19"),
                    ),
                ).single()
        val rows = mutableListOf<Pair<TxnKind, List<Long>>>()
        val committer =
            VoiceDraftCommitter { postings ->
                postings.forEach { posting -> rows += posting.transaction.kind to posting.entries.map { it.amountMinor } }
            }

        assertFalse(rows.isNotEmpty())
        val saved =
            kotlinx.coroutines.runBlocking {
                committer.saveAll(listOf(draft), setOf("cash"), mapOf("EGP" to BigDecimal.ONE))
            }

        assertEquals(1, saved)
        assertEquals(listOf(TxnKind.EXPENSE to listOf(-1_250L)), rows)
    }

    @Test fun unavailable_account_prevents_the_fake_ledger_write() {
        val draft =
            VoiceParser
                .parse(
                    "coffee 5 EGP",
                    ParserContext(emptyList(), listOf(ParserCategory("food", setOf("coffee"))), today = LocalDate.parse("2026-10-19")),
                ).single()
        var writes = 0
        val committer = VoiceDraftCommitter { writes += it.size }

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { committer.saveAll(listOf(draft), emptySet(), emptyMap()) }
        }
        assertEquals(0, writes)
    }
}
