// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import java.time.LocalDate
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val context =
        ParserContext(
            accounts = listOf(ParserAccount("fun", setOf("fun", "الفن")), ParserAccount("cash", setOf("cash", "كاش"))),
            categories =
                listOf(
                    ParserCategory("food", setOf("food", "طعام")),
                    ParserCategory("transport", setOf("transport", "مواصلات")),
                    ParserCategory("fuel", setOf("fuel", "وقود")),
                    ParserCategory("utilities", setOf("utilities", "مرافق")),
                    ParserCategory("charity", setOf("charity", "صدقه")),
                ),
            learnedPayees = mapOf("starbucks" to "food"),
            today = today,
        )

    @Test fun `eighty English and Egyptian Arabic fixtures are exact`() {
        val english =
            listOf("lunch" to "food", "taxi" to "transport", "fuel" to "fuel", "coffee" to "food", "electricity" to "utilities")
                .flatMap { (phrase, category) ->
                    (1..8).map { amount -> Fixture("spent $amount on $phrase from fun", amount * 100L, category, "fun") }
                }
        val arabic =
            listOf("غداء" to "food", "تاكسي" to "transport", "بنزين" to "fuel", "قهوة" to "food", "كهرباء" to "utilities")
                .flatMap { (phrase, category) ->
                    (1..8).map { amount ->
                        val arabicAmount = amount.toString().map { "٠١٢٣٤٥٦٧٨٩"[it.digitToInt()] }.joinToString("")
                        Fixture("دفعت $arabicAmount جنيه في $phrase من الفن", amount * 100L, category, "fun")
                    }
                }
        val fixtures = english + arabic
        assertEquals(80, fixtures.size)
        fixtures.forEach { fixture -> assertFixture(fixture) }
    }

    @Test fun `splits multiple English and Arabic records while carrying the account`() {
        val english = VoiceParser.parse("spent 120 on lunch from fun and 50 taxi", context)
        val arabic = VoiceParser.parse("دفعت ١٢٠ غداء من الفن و ٥٠ تاكسي", context)
        listOf(english, arabic).forEach { drafts ->
            assertEquals(2, drafts.size)
            assertEquals(listOf(12_000L, 5_000L), drafts.map { it.amountMinor })
            assertEquals(listOf("food", "transport"), drafts.map { it.categoryId })
            assertEquals(listOf("fun", "fun"), drafts.map { it.accountId })
        }
    }

    @Test fun `extracts currency relative dates income and learned payee`() {
        val drafts = VoiceParser.parse("received 12.5 dollars at Starbucks yesterday from cash", context)
        assertEquals(1, drafts.size)
        assertEquals(DraftKind.INCOME, drafts.single().kind)
        assertEquals(1_250L, drafts.single().amountMinor)
        assertEquals("USD", drafts.single().currency)
        assertEquals(today.minusDays(1), drafts.single().date)
        assertEquals("cash", drafts.single().accountId)
        assertEquals("food", drafts.single().categoryId)
    }

    @Test fun `normalization and parsing stay below fifty milliseconds for fixture corpus`() {
        val inputs = (1..80).map { "spent $it on coffee from fun" }
        inputs.forEach { VoiceParser.parse(it, context) }
        val elapsed = inputs.maxOf { input -> measureNanoTime { VoiceParser.parse(input, context) } }
        assertTrue("slowest parse took ${elapsed / 1_000_000.0} ms", elapsed < 50_000_000)
    }

    private fun assertFixture(fixture: Fixture) {
        val draft = VoiceParser.parse(fixture.input, context).single()
        assertEquals(fixture.amountMinor, draft.amountMinor)
        assertEquals(fixture.input, fixture.categoryId, draft.categoryId)
        assertEquals(fixture.accountId, draft.accountId)
        assertEquals(DraftKind.EXPENSE, draft.kind)
        assertEquals("EGP", draft.currency)
    }

    private data class Fixture(
        val input: String,
        val amountMinor: Long,
        val categoryId: String,
        val accountId: String,
    )
}
