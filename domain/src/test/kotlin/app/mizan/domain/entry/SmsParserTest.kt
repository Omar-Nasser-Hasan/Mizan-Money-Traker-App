// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset

class SmsParserTest {
    @Test
    fun `every redacted SMS sample parses to its expectation`() {
        val cases = loadSamples()
        assertFalse("No SMS sample cases were found", cases.isEmpty())
        cases.forEach { sample ->
            val draft = SmsParser.parse(sample.sender, sample.text, Instant.parse("2026-10-17T10:00:00Z"))
            if (sample.expect.kind == "ignore") {
                assertEquals("${sample.file}: ${sample.text}", null, draft)
            } else {
                requireNotNull(draft) { "${sample.file}: ${sample.text}" }
                assertEquals(sample.expect.kind.uppercase(), draft.kind.name)
                assertEquals(toMinor(sample.expect.amount!!), draft.amountMinor)
                assertEquals(sample.expect.currency, draft.currency)
                assertEquals(
                    sample.expect.merchant
                        ?.trim()
                        ?.ifEmpty { null },
                    draft.merchant,
                )
                assertEquals(sample.expect.last4?.ifEmpty { null }, draft.cardLastFour)
                sample.expect.date?.let {
                    assertEquals(
                        it,
                        draft.occurredAt
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString(),
                    )
                }
                sample.expect.time?.let {
                    assertEquals(
                        it,
                        draft.occurredAt
                            .atZone(ZoneOffset.UTC)
                            .toLocalTime()
                            .toString(),
                    )
                }
                sample.expect.ref?.let { assertEquals(it, draft.reference) }
                sample.expect.balance?.let { assertEquals(toMinor(it), draft.balanceMinor) }
            }
        }
    }

    @Test
    fun `duplicate guard allows distinct bank balances`() {
        val draft =
            SmsParser.parse(
                "HSBC",
                "From HSBC: 25JUL26 ATM Cash Withdrawal from 103-114***-001 EGP 4,000.00- Your available balance is EGP 24,724.74",
                Instant.now(),
            )!!
        val existing = DuplicateCandidate(400_000, "EGP", "cash", "ATM", 2_872_474, draft.occurredAt)
        assertFalse(SmsParser.isPossibleDuplicate(draft, "cash", listOf(existing)))
        assertTrue(SmsParser.isPossibleDuplicate(draft, "cash", listOf(existing.copy(balanceMinor = draft.balanceMinor))))
    }

    @Test
    fun `unverified sender templates are disabled`() {
        SmsParser.disabledUnverifiedSenders.forEach { sender ->
            assertEquals(null, SmsParser.parse(sender, "Payment EGP 50 at Merchant", Instant.now()))
        }
    }

    private fun loadSamples(): List<Sample> {
        val directory =
            listOf(Paths.get("docs/sms-samples"), Paths.get("../docs/sms-samples")).firstOrNull(Files::isDirectory)
                ?: error("docs/sms-samples is missing")
        return Files.list(directory).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && !it.fileName.toString().startsWith("_") && !it.fileName.toString().startsWith(".") }
                .flatMap { loadFile(it).stream() }
                .toList()
        }
    }

    private fun loadFile(path: Path): List<Sample> {
        val values = Files.readAllLines(path)
        var sender: String? = null
        var text: String? = null
        val samples = mutableListOf<Sample>()
        values.forEach { line ->
            when {
                line.startsWith("sender:") -> {
                    sender = line.removePrefix("sender:").trim()
                }

                line.startsWith("text:") -> {
                    text = line.removePrefix("text:").trimStart()
                }

                line.startsWith("expect:") -> {
                    val raw = requireNotNull(text) { "expect without text in $path" }
                    samples +=
                        Sample(
                            path.fileName.toString(),
                            requireNotNull(sender) {
                                "sender missing in $path"
                            },
                            raw,
                            expectation(line.removePrefix("expect:").trim()),
                        )
                    text = null
                }
            }
        }
        return samples
    }

    private fun expectation(raw: String): Expectation {
        if (raw == "ignore") return Expectation("ignore", null, null, null, null, null, null, null, null)
        val parts = raw.split('|').map(String::trim)
        require(parts.size >= 5) { "Malformed expectation: $raw" }
        val optional = parts.drop(5).associate { value -> value.substringBefore('=').trim() to value.substringAfter('=').trim() }
        return Expectation(
            parts[0],
            parts[1],
            parts[2],
            parts[3],
            parts[4],
            optional["date"],
            optional["time"],
            optional["ref"],
            optional["balance"],
        )
    }

    private fun toMinor(value: String): Long =
        value
            .replace(",", "")
            .toBigDecimal()
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private data class Sample(
        val file: String,
        val sender: String,
        val text: String,
        val expect: Expectation,
    )

    private data class Expectation(
        val kind: String,
        val amount: String?,
        val currency: String?,
        val merchant: String?,
        val last4: String?,
        val date: String?,
        val time: String?,
        val ref: String?,
        val balance: String?,
    )
}
