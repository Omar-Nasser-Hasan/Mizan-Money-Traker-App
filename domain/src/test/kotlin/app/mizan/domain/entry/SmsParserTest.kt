// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SmsParserTest {
    private val time = Instant.parse("2026-10-17T10:00:00Z")

    @Test fun `supported sender templates parse debit messages`() {
        listOf(
            "CIB" to "Purchase EGP 120.50 at Lunch Cafe card 1234",
            "NBE" to "Payment EGP 50 at Taxi",
            "BANQUE MISR" to "Purchase EGP 75 at Market",
            "QNB" to "Payment EGP 90 merchant Coffee",
            "INSTAPAY" to "Sent EGP 200 to Ahmed",
            "VODAFONE CASH" to "Payment EGP 30 to Metro",
        ).forEach { (sender, message) -> assertNotNull("$sender", SmsParser.parse(sender, message, time)) }
        assertEquals(12_050L, SmsParser.parse("CIB", "Purchase EGP 120.50 at Lunch Cafe card 1234", time)?.amountMinor)
    }

    @Test fun `duplicate guard uses account amount payee and two minute window`() {
        val draft = SmsParser.parse("NBE", "Payment EGP 50 at Taxi", time)!!
        assertTrue(
            SmsParser.isPossibleDuplicate(draft, "cash", listOf(DuplicateCandidate(5_000, "EGP", "cash", "Taxi", time.plusSeconds(119)))),
        )
    }
}
