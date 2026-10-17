// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SmsDraftKind { EXPENSE, INCOME, TRANSFER, REVERSAL }

data class SmsDraft(
    val sender: String,
    val kind: SmsDraftKind,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLastFour: String?,
    val reference: String?,
    val balanceMinor: Long?,
    val occurredAt: Instant,
    val rawMessage: String,
)

/** Only templates backed by a redacted sample are eligible for automatic Inbox drafts. */
object SmsParser {
    private data class SenderTemplate(
        val aliases: Set<String>,
        val enabled: Boolean,
    )

    private val senderTemplates =
        listOf(
            SenderTemplate(setOf("BANQUE MISR", "BM"), enabled = true),
            SenderTemplate(setOf("HSBC"), enabled = true),
            SenderTemplate(setOf("CIB", "CIB BANK"), enabled = false),
            SenderTemplate(setOf("NBE", "NATIONAL BANK OF EGYPT"), enabled = false),
            SenderTemplate(setOf("QNB"), enabled = false),
            SenderTemplate(setOf("INSTAPAY"), enabled = false),
            SenderTemplate(setOf("VODAFONE CASH", "VODAFONE"), enabled = false),
        )

    val disabledUnverifiedSenders: Set<String> =
        senderTemplates.filterNot(SenderTemplate::enabled).flatMap(SenderTemplate::aliases).toSet()

    fun parse(
        sender: String,
        message: String,
        receivedAt: Instant,
    ): SmsDraft? {
        val template =
            senderTemplates.firstOrNull { candidate -> candidate.aliases.any { it.equals(sender, ignoreCase = true) } } ?: return null
        if (!template.enabled) return null
        return when (sender.trim().uppercase(Locale.ROOT)) {
            "BANQUE MISR", "BM" -> banqueMisr(sender, message, receivedAt)
            "HSBC" -> hsbc(sender, message, receivedAt)
            else -> null
        }
    }

    /** Same account, amount, payee and time is suspicious unless distinct balances prove separate events. */
    fun isPossibleDuplicate(
        draft: SmsDraft,
        accountId: String?,
        existing: Iterable<DuplicateCandidate>,
    ): Boolean =
        existing.any {
            it.accountId == accountId &&
                it.amountMinor == draft.amountMinor &&
                it.currency == draft.currency &&
                it.payee?.equals(draft.merchant, ignoreCase = true) == true &&
                balanceMatches(draft.balanceMinor, it.balanceMinor) &&
                kotlin.math.abs(it.occurredAt.epochSecond - draft.occurredAt.epochSecond) <= 120
        }

    private fun balanceMatches(
        draftBalance: Long?,
        existingBalance: Long?,
    ): Boolean = draftBalance == null || existingBalance == null || draftBalance == existingBalance

    private fun banqueMisr(
        sender: String,
        message: String,
        receivedAt: Instant,
    ): SmsDraft? {
        if ("otp:" in message.lowercase(Locale.ROOT) || " declined " in message.lowercase(Locale.ROOT)) return null
        val reversal =
            Regex(
                "Your Transaction of (EGP) ([0-9,.]+) with card \\*+([0-9]{4}) on ([0-9]{2}/[0-9]{2}/[0-9]{4}) at (.+?) has been reversed",
                RegexOption.IGNORE_CASE,
            ).find(message)
        if (reversal != null) {
            return draft(
                sender,
                SmsDraftKind.REVERSAL,
                reversal.groupValues[2],
                reversal.groupValues[1],
                reversal.groupValues[5].trim(),
                reversal.groupValues[3],
                null,
                null,
                slashDate(reversal.groupValues[4]),
                message,
            )
        }
        val debit =
            Regex(
                "BM credit card \\*+([0-9]{4}) now debited by (EGP) ([0-9,.]+) at (.+?)\\s+on ([0-9]{2}/[0-9]{2}/[0-9]{4})\\s*, available now EGP ([0-9,.]+)",
                RegexOption.IGNORE_CASE,
            ).find(message) ?: return null
        return draft(
            sender,
            SmsDraftKind.EXPENSE,
            debit.groupValues[3],
            debit.groupValues[2],
            debit.groupValues[4].trim(),
            debit.groupValues[1],
            null,
            debit.groupValues[6],
            slashDate(debit.groupValues[5]),
            message,
        )
    }

    private fun hsbc(
        sender: String,
        message: String,
        receivedAt: Instant,
    ): SmsDraft? {
        if (message.startsWith("Your HSBC Account")) return hsbcIpn(sender, message)
        val statement =
            Regex(
                "From HSBC: ([0-9]{2}[A-Z]{3}[0-9]{2}) (.+?) from .+? (EGP) ([0-9,.]+)- Your available balance is EGP ([0-9,.]+)",
                RegexOption.IGNORE_CASE,
            ).find(message) ?: return null
        val activity = statement.groupValues[2].trim()
        val kind = if (activity.equals("ATM Cash Withdrawal", true)) SmsDraftKind.TRANSFER else SmsDraftKind.EXPENSE
        val merchant = if (kind == SmsDraftKind.TRANSFER) "ATM" else activity.removeSuffix(" Purchase").trim()
        return draft(
            sender,
            kind,
            statement.groupValues[4],
            statement.groupValues[3],
            merchant,
            null,
            null,
            statement.groupValues[5],
            hsbcDate(statement.groupValues[1]),
            message,
        )
    }

    private fun hsbcIpn(
        sender: String,
        message: String,
    ): SmsDraft? {
        val match =
            Regex(
                "Your HSBC Account \\*+([0-9]{4}) was (credited|debited|reversed) with IPN (inward|outward) transfer for (EGP) ([0-9,.]+) on ([0-9]{2}-[0-9]{2}-[0-9]{4}) ([0-9]{2}:[0-9]{2}) (?:from|to) (.+?) with reference ([A-Za-z0-9]+)\\.",
                RegexOption.IGNORE_CASE,
            ).find(message) ?: return null
        val action = match.groupValues[2].lowercase(Locale.ROOT)
        val counterparty = match.groupValues[8].trim()
        val kind =
            when (action) {
                "credited" -> SmsDraftKind.INCOME
                "reversed" -> SmsDraftKind.REVERSAL
                else -> if (counterparty.equals("Thndr Securities Brokerage", true)) SmsDraftKind.TRANSFER else SmsDraftKind.EXPENSE
            }
        return draft(
            sender,
            kind,
            match.groupValues[5],
            match.groupValues[4],
            counterparty,
            match.groupValues[1],
            match.groupValues[9],
            null,
            dashDateTime(match.groupValues[6], match.groupValues[7]),
            message,
        )
    }

    private fun draft(
        sender: String,
        kind: SmsDraftKind,
        amount: String,
        currency: String,
        merchant: String?,
        card: String?,
        ref: String?,
        balance: String?,
        occurredAt: Instant,
        message: String,
    ) = SmsDraft(
        sender,
        kind,
        minor(
            amount,
            currency,
        ),
        currency,
        merchant?.trim()?.takeIf(
            String::isNotEmpty,
        ),
        card?.takeIf(String::isNotEmpty),
        ref?.takeIf(String::isNotEmpty),
        balance?.let {
            minor(it, currency)
        },
        occurredAt,
        message,
    )

    private fun slashDate(value: String): Instant =
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/uuuu")).atStartOfDay().toInstant(ZoneOffset.UTC)

    private fun dashDateTime(
        date: String,
        time: String,
    ): Instant =
        java.time.LocalDateTime
            .parse("$date $time", DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm"))
            .toInstant(ZoneOffset.UTC)

    private fun hsbcDate(value: String): Instant {
        val normalized = value.uppercase(Locale.ROOT)
        val day = normalized.take(2).toInt()
        val month =
            listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
                .indexOf(normalized.substring(2, 5)) + 1
        return LocalDate.of(2000 + normalized.takeLast(2).toInt(), month, day).atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    private fun minor(
        amount: String,
        currency: String,
    ): Long =
        amount
            .replace(",", "")
            .toBigDecimal()
            .movePointRight(
                if (currency ==
                    "JPY"
                ) {
                    0
                } else {
                    2
                },
            ).setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
}

data class DuplicateCandidate(
    val amountMinor: Long,
    val currency: String,
    val accountId: String?,
    val payee: String?,
    val balanceMinor: Long?,
    val occurredAt: Instant,
)
