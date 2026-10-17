// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SmsReversalMatcherTest {
    private val time = Instant.parse("2026-10-05T12:00:00Z")
    private val reversal = SmsDraft("HSBC", SmsDraftKind.REVERSAL, 143_644, "EGP", "hanyz@instapay", "2002", "ref", null, time, "")

    @Test
    fun `reference takes priority over matching amount`() {
        val byReference = candidate("original", "ref", "other merchant", time.minusSeconds(8 * 86_400))
        assertEquals(
            "original",
            SmsReversalMatcher.originalFor(reversal, listOf(byReference, candidate("near", "different", "hanyz@instapay", time)))?.id,
        )
    }

    @Test
    fun `fallback needs amount merchant card and three-day window`() {
        val withoutRef = reversal.copy(reference = null, merchant = "Uber", cardLastFour = "4417")
        assertEquals(
            "near",
            SmsReversalMatcher.originalFor(withoutRef, listOf(candidate("near", null, "Uber", time.minusSeconds(3 * 86_400), "4417")))?.id,
        )
        assertNull(
            SmsReversalMatcher.originalFor(withoutRef, listOf(candidate("old", null, "Uber", time.minusSeconds(3 * 86_400 + 1), "4417"))),
        )
    }

    private fun candidate(
        id: String,
        reference: String?,
        merchant: String,
        occurredAt: Instant,
        card: String = "2002",
    ) = InboxDraftMatch(id, "PENDING", 143_644, "EGP", merchant, card, reference, occurredAt)
}
