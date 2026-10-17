// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class SmsDraft(
    val sender: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLastFour: String?,
    val balanceMinor: Long?,
    val occurredAt: Instant,
    val rawMessage: String,
)

data class SmsTemplate(
    val senderAliases: Set<String>,
    val debitRegex: Regex,
)

object SmsParser {
    private val templates =
        listOf(
            template(setOf("CIB", "CIB BANK"), "purchase|spent|debit"),
            template(setOf("NBE", "NATIONAL BANK OF EGYPT"), "purchase|payment|خصم"),
            template(setOf("BANQUE MISR", "BM"), "purchase|paid|شراء"),
            template(setOf("QNB"), "purchase|payment|عملية"),
            template(setOf("INSTAPAY"), "sent|transfer|تم تحويل"),
            template(setOf("VODAFONE CASH", "VODAFONE"), "payment|paid|تم الدفع"),
        )

    fun parse(
        sender: String,
        message: String,
        occurredAt: Instant,
    ): SmsDraft? {
        val template = templates.firstOrNull { template -> template.senderAliases.any { it.equals(sender, ignoreCase = true) } } ?: return null
        if (template.debitRegex.find(message) == null) return null
        val normalized = message.replace('،', ',')
        val money = Regex("(?:(?<currency>EGP|USD|EUR|جنيه|دولار)\\s*(?<amount>[0-9,.]+)|(?<amount2>[0-9,.]+)\\s*(?<currency2>EGP|USD|EUR|جنيه|دولار))", RegexOption.IGNORE_CASE).find(normalized) ?: return null
        val currency = currency(money.groups["currency"]?.value ?: money.groups["currency2"]?.value ?: return null)
        val amount = (money.groups["amount"]?.value ?: money.groups["amount2"]?.value)?.replace(",", "")?.toBigDecimalOrNull() ?: return null
        val merchant = Regex("(?:at|merchant|to|لدى|الى|من)\\s+([A-Za-z0-9 .&-]+)", RegexOption.IGNORE_CASE).find(normalized)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')?.takeIf(String::isNotBlank)
        return SmsDraft(sender, minor(amount, currency), currency, merchant, Regex("(?:card|بطاقة)\\s*([0-9]{4})", RegexOption.IGNORE_CASE).find(normalized)?.groupValues?.get(1), null, occurredAt, message)
    }

    /** Same amount, account and payee within two minutes requires human review. */
    fun isPossibleDuplicate(
        draft: SmsDraft,
        accountId: String?,
        existing: Iterable<DuplicateCandidate>,
    ): Boolean =
        existing.any {
            it.accountId == accountId && it.amountMinor == draft.amountMinor && it.currency == draft.currency &&
                it.payee?.equals(draft.merchant, ignoreCase = true) == true &&
                kotlin.math.abs(it.occurredAt.epochSecond - draft.occurredAt.epochSecond) <= 120
        }

    private fun template(
        senders: Set<String>,
        pattern: String,
    ) = SmsTemplate(senders, Regex(pattern, RegexOption.IGNORE_CASE))

    private fun currency(value: String): String = when (value.lowercase()) { "جنيه", "egp" -> "EGP"; "دولار", "usd" -> "USD"; else -> "EUR" }

    private fun minor(amount: BigDecimal, currency: String): Long = amount.movePointRight(if (currency == "JPY") 0 else 2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

data class DuplicateCandidate(
    val amountMinor: Long,
    val currency: String,
    val accountId: String?,
    val payee: String?,
    val occurredAt: Instant,
)
