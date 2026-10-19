// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.io

import app.mizan.domain.assets.AssetType
import app.mizan.domain.ledger.AccountType
import app.mizan.domain.ledger.PaymentType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class MizanImportKind { EXPENSE, INCOME, TRANSFER, ASSET_BUY, DEBT_OPENING, DEBT_BORROW, DEBT_LEND, DEBT_REPAY_IN, DEBT_REPAY_OUT }

data class MizanImportEvent(
    val eventId: String,
    val sourceRef: String,
    val date: LocalDate,
    val kind: MizanImportKind,
    val account: String,
    val accountType: AccountType,
    val currency: String,
    val amountMinor: Long,
    val category: String?,
    val payee: String?,
    val paymentType: PaymentType,
    val note: String,
    val destination: String?,
    val assetId: String?,
    val assetName: String?,
    val assetType: AssetType?,
    val assetSymbol: String?,
    val assetUnit: String?,
    val assetGoldForm: String?,
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
    val feeMinor: Long,
    val debtId: String?,
    val debtPerson: String?,
    val debtDirection: String?,
    val debtPrincipalMinor: Long?,
    val debtOpenedAt: LocalDate?,
    val debtDueDate: LocalDate?,
)

data class MizanImportReview(
    val sourceRef: String,
    val note: String,
)

data class MizanImportPreview(
    val events: List<MizanImportEvent>,
    val bucketTotalsMinor: Map<WalletBucket, Long>,
    val reviews: List<MizanImportReview>,
    val errors: List<String>,
)

/** Parser for the single-sheet `Mizan Import` workbook manifest. Amounts in the file are major units. */
object MizanImportParser {
    fun preview(csv: String): MizanImportPreview {
        val rows =
            runCatching { parseCsv(csv) }.getOrElse {
                return MizanImportPreview(emptyList(), emptyMap(), emptyList(), listOf(it.message ?: "Invalid CSV"))
            }
        if (rows.size < 2) return MizanImportPreview(emptyList(), emptyMap(), emptyList(), listOf("The Mizan Import sheet has no events."))
        val headers = rows.first().map { it.trim().lowercase() }
        val errors = mutableListOf<String>()
        val reviews = mutableListOf<MizanImportReview>()
        val events = mutableListOf<MizanImportEvent>()
        val seenIds = mutableSetOf<String>()

        rows.drop(1).forEachIndexed { index, row ->
            val lineNumber = index + 2

            fun raw(name: String): String =
                headers
                    .indexOf(name)
                    .takeIf { it >= 0 }
                    ?.let { row.getOrNull(it)?.trim().orEmpty() }
                    .orEmpty()
            val eventKind = raw("event_kind").uppercase()
            if (eventKind == "REVIEW") {
                reviews += MizanImportReview(raw("source_ref"), raw("note").ifBlank { "Source mapping requires review." })
                return@forEachIndexed
            }
            if (headers.none { it == "event_kind" }) {
                errors += "Row $lineNumber: missing event_kind column."
                return@forEachIndexed
            }
            runCatching {
                val eventId = raw("event_id").also { require(it.isNotBlank()) { "Missing event_id" } }
                require(seenIds.add(eventId)) { "Duplicate event_id '$eventId'" }
                val date = LocalDate.parse(raw("date"))
                val kind = MizanImportKind.valueOf(eventKind)
                val account = raw("account").also { require(it.isNotBlank()) { "Missing account" } }
                val currency = raw("currency").uppercase().also { require(it.matches(Regex("[A-Z]{3}"))) { "Invalid currency" } }
                val decimals = currencyDecimals(currency)
                val amount = amountMinor(raw("amount"), decimals)
                require(amount > 0L) { "Amount must be positive" }
                val accountType = AccountType.valueOf(raw("account_type").ifBlank { "BANK" }.uppercase())
                val paymentType = paymentType(raw("payment_type"))
                val destination = raw("destination").ifBlank { null }
                if (kind == MizanImportKind.TRANSFER) {
                    require(destination != null) { "Transfer needs a destination account" }
                    require(destination != account) { "Transfer destination must differ from source" }
                }

                val assetId: String?
                val assetName: String?
                val assetType: AssetType?
                val assetSymbol: String?
                val assetUnit: String?
                val assetGoldForm: String?
                val quantity: BigDecimal?
                val unitPrice: BigDecimal?
                val feeMinor: Long
                if (kind == MizanImportKind.ASSET_BUY) {
                    assetId = raw("asset_id").also { require(it.isNotBlank()) { "Missing asset_id" } }
                    assetName = raw("asset_name").also { require(it.isNotBlank()) { "Missing asset_name" } }
                    assetType = AssetType.valueOf(raw("asset_type").uppercase())
                    assetSymbol = raw("asset_symbol").ifBlank { assetName }
                    assetUnit = raw("asset_unit").also { require(it.isNotBlank()) { "Missing asset_unit" } }
                    assetGoldForm = raw("asset_gold_form").ifBlank { null }
                    quantity = raw("quantity").toBigDecimal().also { require(it > BigDecimal.ZERO) { "Quantity must be positive" } }
                    unitPrice = raw("unit_price").toBigDecimal().also { require(it > BigDecimal.ZERO) { "Unit price must be positive" } }
                    feeMinor = if (raw("fee").isBlank()) 0L else amountMinor(raw("fee"), decimals)
                    require(amount > 0L) { "Asset cost must be positive" }
                } else {
                    assetId = null
                    assetName = null
                    assetType = null
                    assetSymbol = null
                    assetUnit = null
                    assetGoldForm = null
                    quantity = null
                    unitPrice = null
                    feeMinor = 0L
                }

                val debtId: String?
                val debtPerson: String?
                val debtDirection: String?
                val debtPrincipal: Long?
                val debtOpenedAt: LocalDate?
                val debtDueDate: LocalDate?
                if (kind in
                    setOf(
                        MizanImportKind.DEBT_OPENING,
                        MizanImportKind.DEBT_BORROW,
                        MizanImportKind.DEBT_LEND,
                        MizanImportKind.DEBT_REPAY_IN,
                        MizanImportKind.DEBT_REPAY_OUT,
                    )
                ) {
                    debtId = raw("debt_id").also { require(it.isNotBlank()) { "Missing debt_id" } }
                    debtPerson = raw("debt_person").ifBlank { null }
                    debtDirection = raw("debt_direction").uppercase().ifBlank { null }
                    if (kind == MizanImportKind.DEBT_OPENING || kind == MizanImportKind.DEBT_BORROW || kind == MizanImportKind.DEBT_LEND) {
                        require(!debtPerson.isNullOrBlank()) { "Missing debt_person" }
                        require(debtDirection in setOf("BORROWED", "LENT")) { "Debt direction must be BORROWED or LENT" }
                        if (kind != MizanImportKind.DEBT_OPENING) {
                            require(
                                (kind == MizanImportKind.DEBT_BORROW && debtDirection == "BORROWED") ||
                                    (kind == MizanImportKind.DEBT_LEND && debtDirection == "LENT"),
                            ) { "Debt event kind and direction do not match" }
                        }
                        debtPrincipal = if (raw("debt_principal").isBlank()) amount else amountMinor(raw("debt_principal"), decimals)
                        require(debtPrincipal == amount) { "Debt principal must equal the draw amount" }
                        debtOpenedAt = raw("debt_opened_at").takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: date
                        debtDueDate = raw("debt_due_date").takeIf(String::isNotBlank)?.let(LocalDate::parse)
                    } else {
                        debtPrincipal = null
                        debtOpenedAt = null
                        debtDueDate = null
                    }
                } else {
                    debtId = null
                    debtPerson = null
                    debtDirection = null
                    debtPrincipal = null
                    debtOpenedAt = null
                    debtDueDate = null
                }

                MizanImportEvent(
                    eventId,
                    raw("source_ref"),
                    date,
                    kind,
                    account,
                    accountType,
                    currency,
                    amount,
                    raw("category").ifBlank { null },
                    raw("payee").ifBlank { null },
                    paymentType,
                    raw("note"),
                    destination,
                    assetId,
                    assetName,
                    assetType,
                    assetSymbol,
                    assetUnit,
                    assetGoldForm,
                    quantity,
                    unitPrice,
                    feeMinor,
                    debtId,
                    debtPerson,
                    debtDirection,
                    debtPrincipal,
                    debtOpenedAt,
                    debtDueDate,
                ).also(events::add)
            }.onFailure { errors += "Row $lineNumber: ${it.message ?: "Invalid event"}" }
        }

        val totals = linkedMapOf<WalletBucket, Long>()

        fun add(
            account: String,
            currency: String,
            amount: Long,
        ) {
            val bucket = WalletBucket(account, currency)
            totals[bucket] = Math.addExact(totals[bucket] ?: 0L, amount)
        }
        events.forEach { event ->
            if (event.kind == MizanImportKind.DEBT_OPENING) return@forEach
            val sign =
                when (event.kind) {
                    MizanImportKind.EXPENSE,
                    MizanImportKind.TRANSFER,
                    MizanImportKind.ASSET_BUY,
                    MizanImportKind.DEBT_LEND,
                    MizanImportKind.DEBT_REPAY_OUT,
                    -> -1L

                    else -> 1L
                }
            add(event.account, event.currency, Math.multiplyExact(event.amountMinor, sign))
            if (event.kind == MizanImportKind.TRANSFER) {
                add(requireNotNull(event.destination), event.currency, event.amountMinor)
            }
        }
        return MizanImportPreview(events, totals, reviews, errors)
    }

    private fun paymentType(value: String): PaymentType =
        when (value.trim().uppercase()) {
            "", "CASH" -> PaymentType.CASH
            "DEBIT" -> PaymentType.DEBIT
            "CREDIT" -> PaymentType.CREDIT
            "TRANSFER", "BANK_TRANSFER" -> PaymentType.BANK_TRANSFER
            "MOBILE" -> PaymentType.MOBILE
            "INSTAPAY" -> PaymentType.INSTAPAY
            "WEB_PAYMENT", "OTHER" -> PaymentType.OTHER
            else -> error("Unknown payment_type '$value'")
        }

    private fun amountMinor(
        value: String,
        decimals: Int,
    ): Long {
        val amount = value.replace(",", "").toBigDecimal()
        require(amount.signum() >= 0) { "Use positive amounts; event_kind determines the ledger sign" }
        return amount.movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun currencyDecimals(currency: String): Int =
        when (currency) {
            "JPY" -> 0
            "KWD" -> 3
            else -> 2
        }

    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val text = StringBuilder()
        var quoted = false
        var index = if (csv.startsWith('\uFEFF')) 1 else 0

        fun field() {
            fields += text.toString()
            text.clear()
        }

        fun row() {
            field()
            if (fields.any(String::isNotBlank)) rows += fields.toList()
            fields.clear()
        }

        while (index < csv.length) {
            val char = csv[index]
            when {
                char == '"' && quoted && csv.getOrNull(index + 1) == '"' -> {
                    text.append('"')
                    index++
                }

                char == '"' -> {
                    quoted = !quoted
                }

                char == ',' && !quoted -> {
                    field()
                }

                char == '\n' && !quoted -> {
                    row()
                }

                char == '\r' && !quoted -> {
                }

                else -> {
                    text.append(char)
                }
            }
            index++
        }
        require(!quoted) { "Unclosed quoted field" }
        if (text.isNotEmpty() || fields.isNotEmpty()) row()
        return rows
    }
}
