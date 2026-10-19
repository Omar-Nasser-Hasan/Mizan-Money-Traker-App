// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import app.mizan.domain.ledger.IncomeAllocation
import app.mizan.domain.ledger.IncomeSplitCalculator
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.SplitLine
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class DraftKind { EXPENSE, INCOME }

/** A proposed record only. The parser has no persistence dependency and can never post a transaction. */
data class DraftRecord(
    val kind: DraftKind,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val accountId: String?,
    val categoryId: String?,
    val payee: String?,
    val confidence: Int,
    val sourceClause: String,
    val paymentType: PaymentType? = null,
    val incomeAllocations: List<IncomeAllocation> = emptyList(),
    val incomeSplitLines: List<SplitLine> = emptyList(),
    val incomeSplitRemainderAccountId: String? = null,
)

data class ParserAccount(
    val id: String,
    val names: Set<String>,
)

data class ParserCategory(
    val id: String,
    val names: Set<String>,
)

data class ParserContext(
    val accounts: List<ParserAccount>,
    val categories: List<ParserCategory>,
    val learnedPayees: Map<String, String> = emptyMap(),
    val learnedPhrases: Map<String, String> = emptyMap(),
    val defaultAccountId: String? = null,
    val defaultCurrency: String = "EGP",
    val defaultIncomeSplitLines: List<SplitLine> = emptyList(),
    val defaultIncomeRemainderAccountId: String? = null,
    val today: LocalDate,
    val decimalsByCurrency: Map<String, Int> = mapOf("EGP" to 2, "USD" to 2, "EUR" to 2, "KWD" to 3, "JPY" to 0),
)

object VoiceParser {
    private val numberRegex = Regex("(?<![\\p{L}\\d])(\\d+(?:[.,]\\d+)?)(?:\\s*([kK]))?")
    private val payeeRegex = Regex("(?:at|to|on|في|عند)\\s+([\\p{L}][\\p{L}\\d-]{1,30})")
    private val arabicDigits =
        mapOf(
            '٠' to '0',
            '١' to '1',
            '٢' to '2',
            '٣' to '3',
            '٤' to '4',
            '٥' to '5',
            '٦' to '6',
            '٧' to '7',
            '٨' to '8',
            '٩' to '9',
        )
    private val keywordCategories =
        mapOf(
            "food" to
                setOf(
                    "lunch",
                    "dinner",
                    "breakfast",
                    "coffee",
                    "supermarket",
                    "groceries",
                    "غدا",
                    "غداء",
                    "عشا",
                    "فطار",
                    "قهوه",
                    "سوبر ماركت",
                ),
            "transport" to setOf("uber", "taxi", "careem", "indrive", "metro", "microbus", "تاكسي", "اوبر", "مترو", "ميكروباص"),
            "fuel" to setOf("fuel", "petrol", "بنزين", "بنزينة"),
            "utilities" to setOf("electricity", "water bill", "gas bill", "كهرباء", "ميه", "غاز"),
            "charity" to setOf("sadaqa", "zakat", "صدقه", "زكاه"),
        )
    private val currencyWords =
        mapOf(
            "usd" to "USD",
            "dollar" to "USD",
            "dollars" to "USD",
            "دولار" to "USD",
            "eur" to "EUR",
            "euro" to "EUR",
            "euros" to "EUR",
            "يورو" to "EUR",
            "kwd" to "KWD",
            "دينار" to "KWD",
            "دنانير" to "KWD",
            "jpy" to "JPY",
            "yen" to "JPY",
            "ين" to "JPY",
            "egp" to "EGP",
            "pound" to "EGP",
            "pounds" to "EGP",
            "جنيه" to "EGP",
            "جنيهات" to "EGP",
        )
    private val wordNumbers =
        mapOf(
            "zero" to 0,
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5,
            "ten" to 10,
            "twenty" to 20,
            "thirty" to 30,
            "fifty" to 50,
            "hundred" to 100,
            "صفر" to 0,
            "واحد" to 1,
            "واحدة" to 1,
            "اتنين" to 2,
            "اثنين" to 2,
            "ثلاثة" to 3,
            "خمسة" to 5,
            "عشرة" to 10,
            "عشرين" to 20,
            "ثلاثين" to 30,
            "خمسين" to 50,
            "مية" to 100,
            "مائة" to 100,
        )

    fun parse(
        transcript: String,
        context: ParserContext,
    ): List<DraftRecord> {
        val rawClauses = splitClauses(normalize(transcript))
        val clauses = mutableListOf<String>()
        rawClauses.forEach { clause ->
            val targetClause =
                clauses.lastOrNull()?.let { previous ->
                    extractAmount(clause) == null &&
                        incomeWords.any(previous::contains) &&
                        listOf("put", "invest", "حط", "استثمر").any(clause::contains)
                } == true
            if (targetClause) clauses[clauses.lastIndex] = "${clauses.last()} $clause" else clauses += clause
        }
        var inheritedAccount = context.defaultAccountId
        return clauses.mapNotNull { clause ->
            val amount = extractAmount(clause) ?: return@mapNotNull null
            val currency = extractCurrency(clause) ?: context.defaultCurrency
            val explicitAccount = extractAccount(clause, context)
            val account = explicitAccount ?: inheritedAccount
            if (account != null) inheritedAccount = account
            val kind = if (incomeWords.any { word -> clause.contains(word) }) DraftKind.INCOME else DraftKind.EXPENSE
            val incomeSplitLines = if (kind == DraftKind.INCOME && explicitAccount == null) context.defaultIncomeSplitLines else emptyList()
            val amountMinor = toMinor(amount, currency, context)
            val allocations =
                when {
                    kind != DraftKind.INCOME -> {
                        emptyList()
                    }

                    explicitAccount != null -> {
                        listOf(IncomeAllocation(explicitAccount, amountMinor))
                    }

                    incomeSplitLines.isNotEmpty() -> {
                        runCatching {
                            IncomeSplitCalculator
                                .allocate(
                                    amountMinor,
                                    incomeSplitLines,
                                    context.defaultIncomeRemainderAccountId,
                                ).allocations
                        }.getOrDefault(emptyList())
                    }

                    else -> {
                        emptyList()
                    }
                }
            val payee = extractPayee(clause)
            val category = extractCategory(clause, payee, context)
            val confidence = 60 + if (account != null) 20 else 0 + if (category != null) 20 else 0
            DraftRecord(
                kind,
                amountMinor,
                currency,
                extractDate(clause, context.today),
                if (kind == DraftKind.INCOME) allocations.firstOrNull()?.accountId ?: account else account,
                category,
                payee,
                confidence,
                clause,
                extractPaymentType(clause),
                allocations,
                incomeSplitLines,
                context.defaultIncomeRemainderAccountId.takeIf { incomeSplitLines.isNotEmpty() },
            )
        }
    }

    fun normalize(value: String): String =
        value
            .map { arabicDigits[it] ?: it }
            .joinToString("")
            .replace("ـ", "")
            .replace(Regex("[ًٌٍَُِّْ]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .lowercase()
            .trim()

    private fun splitClauses(value: String): List<String> =
        value
            .split(Regex("\\s*(?:,|;|\\band\\b|\\bthen\\b|\\bafter that\\b|وكمان|و كمان|ثم|وبعدين|\\s+و\\s+)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun extractAmount(clause: String): BigDecimal? {
        val numeric = numberRegex.find(clause)
        if (numeric != null) {
            val base = numeric.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
            return if (numeric.groupValues[2].isNotEmpty()) base.multiply(BigDecimal(1000)) else base
        }
        val words = clause.split(Regex("\\s+"))
        var total = 0
        var found = false
        words.forEach { word ->
            wordNumbers[word]?.let { value ->
                found = true
                total = if (value == 100 && total > 0) total * value else total + value
            }
        }
        return total.takeIf { found && it > 0 }?.let(::BigDecimal)
    }

    private fun extractCurrency(clause: String): String? =
        currencyWords.entries
            .firstOrNull { (word, _) ->
                Regex(
                    "(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})",
                ).containsMatchIn(clause)
            }?.value

    private fun extractDate(
        clause: String,
        today: LocalDate,
    ): LocalDate =
        when {
            listOf("yesterday", "امبارح", "مبارح").any(clause::contains) -> today.minusDays(1)
            listOf("tomorrow", "بكره", "بكرة").any(clause::contains) -> today.plusDays(1)
            else -> today
        }

    private fun extractPaymentType(clause: String): PaymentType? =
        when {
            listOf("credit card", "credit", "visa", "فيزا", "كريدت").any(clause::contains) -> PaymentType.CREDIT

            listOf(
                "debit card",
                "debit",
                "bank card",
                "bank account",
                "in the bank",
                "ديبت",
                "ديبت كارد",
                "في البنك",
            ).any(clause::contains) -> PaymentType.DEBIT

            listOf("cash", "كاش", "نقدا").any(clause::contains) -> PaymentType.CASH

            else -> null
        }

    private fun extractAccount(
        clause: String,
        context: ParserContext,
    ): String? =
        context.accounts
            .firstOrNull { account ->
                account.names.map(::normalize).any { name ->
                    listOf("from ", "to ", "in ", "into ", "من ", "في ", "على ", "حطيتها في ", "حطيت في ").any { prefix ->
                        clause.contains(prefix + name)
                    }
                }
            }?.id

    private fun extractPayee(clause: String): String? =
        payeeRegex
            .find(clause)
            ?.groupValues
            ?.get(1)
            ?.trim()

    private fun extractCategory(
        clause: String,
        payee: String?,
        context: ParserContext,
    ): String? {
        context.learnedPhrases.entries
            .firstOrNull { (phrase, _) -> clause.contains(normalize(phrase)) }
            ?.let { return it.value }
        payee?.let { value -> context.learnedPayees[normalize(value)]?.let { return it } }
        keywordCategories.entries.firstOrNull { (_, words) -> words.any(clause::contains) }?.let { (key, _) ->
            context.categories
                .firstOrNull { category ->
                    category.id == key || category.names.map(::normalize).any { name -> name == key || name in clause }
                }?.let { return it.id }
        }
        return context.categories.firstOrNull { category -> category.names.map(::normalize).any { name -> name in clause } }?.id
    }

    private fun toMinor(
        amount: BigDecimal,
        currency: String,
        context: ParserContext,
    ): Long = amount.movePointRight(context.decimalsByCurrency[currency] ?: 2).setScale(0, RoundingMode.HALF_UP).longValueExact()

    private val incomeWords = setOf("got", "received", "salary", "قبضت", "جالي", "جاتلي", "دخللي", "دخلتلي", "استلمت")
}
