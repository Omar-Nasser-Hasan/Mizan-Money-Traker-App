// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.io

import java.math.RoundingMode
import java.time.LocalDate

enum class WalletImportKind { EXPENSE, INCOME, TRANSFER, ASSET_BUY }

data class WalletImportRecord(
    val date: LocalDate,
    val account: String,
    val category: String,
    val currency: String,
    val amountMinor: Long,
    val kind: WalletImportKind,
    val note: String?,
    val destination: String? = null,
)

data class WalletBucket(
    val account: String,
    val currency: String,
)

data class WalletImportPreview(
    val records: List<WalletImportRecord>,
    val accountTotalsMinor: Map<String, Long>,
    val errors: List<String>,
    val bucketTotalsMinor: Map<WalletBucket, Long> = emptyMap(),
)

/** Wallet transaction kinds come from the export's type/transfer fields, never its category label. */
object WalletCsvImporter {
    val fields = listOf("date", "account", "category", "amount", "currency", "type", "note", "destination")
    private val aliases =
        mapOf(
            "date" to "transaction date",
            "account" to "account name",
            "category" to "category name",
            "amount" to "amount in account currency",
            "type" to "transaction type",
            "note" to "description",
            "destination" to "to account",
        )

    fun headers(csv: String): List<String> = parse(csv).firstOrNull().orEmpty()

    fun preview(
        csv: String,
        mapping: Map<String, String> = emptyMap(),
    ): WalletImportPreview {
        val rows =
            runCatching { parse(csv) }.getOrElse {
                return WalletImportPreview(emptyList(), emptyMap(), listOf(it.message ?: "Invalid CSV"))
            }
        if (rows.size < 2) return WalletImportPreview(emptyList(), emptyMap(), listOf("The CSV has no records."))
        val headers = rows.first().map { it.trim().lowercase() }
        val errors = mutableListOf<String>()
        val raw =
            rows.drop(1).mapIndexedNotNull { index, row ->
                runCatching { record(headers, row, mapping) }.onFailure { errors += "Row ${index + 2}: ${it.message}" }.getOrNull()
            }
        val consumed = mutableSetOf<Int>()
        val records =
            raw.mapIndexedNotNull { index, record ->
                if (index in consumed) return@mapIndexedNotNull null
                if (record.kind != WalletImportKind.TRANSFER || record.destination != null) return@mapIndexedNotNull record
                val partner =
                    raw.indices.firstOrNull { other ->
                        other != index && other !in consumed &&
                            raw[other].kind == WalletImportKind.TRANSFER && raw[other].destination == null &&
                            raw[other].date == record.date && raw[other].currency == record.currency &&
                            raw[other].account != record.account && raw[other].amountMinor == -record.amountMinor
                    }
                if (partner == null) {
                    errors += "Transfer from ${record.account} needs a destination or matching opposite leg."
                    null
                } else {
                    consumed += partner
                    val from = if (record.amountMinor < 0) record else raw[partner]
                    val to = if (record.amountMinor < 0) raw[partner] else record
                    from.copy(destination = to.account)
                }
            }
        val totals = linkedMapOf<WalletBucket, Long>()

        fun add(
            bucket: WalletBucket,
            amount: Long,
        ) {
            totals[bucket] = Math.addExact(totals[bucket] ?: 0, amount)
        }
        records.forEach {
            add(WalletBucket(it.account, it.currency), it.amountMinor)
            it.destination?.let { destination -> add(WalletBucket(destination, it.currency), -it.amountMinor) }
        }
        // Compatibility field is EGP only: different currencies must never be summed.
        return WalletImportPreview(records, totals.filterKeys { it.currency == "EGP" }.mapKeys { it.key.account }, errors, totals)
    }

    private fun record(
        headers: List<String>,
        row: List<String>,
        mapping: Map<String, String>,
    ): WalletImportRecord {
        fun value(name: String): String? {
            val selected = mapping[name]?.lowercase() ?: name
            val index = headers.indexOf(selected).takeIf { it >= 0 } ?: headers.indexOf(aliases[name]).takeIf { it >= 0 }
            return index?.let { row.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) }
        }
        val date = LocalDate.parse(requireNotNull(value("date")) { "Missing date" })
        val account = requireNotNull(value("account")) { "Missing account" }
        val category = value("category") ?: "Uncategorized"
        val currency = (value("currency") ?: "EGP").uppercase()
        require(currency.matches(Regex("[A-Z]{3}"))) { "Invalid currency" }
        val amount = requireNotNull(value("amount")) { "Missing amount" }.replace(",", "").toBigDecimal()
        val decimals =
            when (currency) {
                "JPY" -> 0
                "KWD" -> 3
                else -> 2
            }
        val minor =
            amount
                .abs()
                .movePointRight(decimals)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        require(minor > 0) { "Amount must be nonzero" }
        val type = value("type")?.lowercase().orEmpty()
        val transfer =
            type.contains("transfer") ||
                value("transfer")?.lowercase() in setOf("true", "1", "yes")
        val kind =
            when {
                transfer -> WalletImportKind.TRANSFER
                type.contains("expense") -> WalletImportKind.EXPENSE
                type.contains("income") -> WalletImportKind.INCOME
                amount.signum() < 0 -> WalletImportKind.EXPENSE
                else -> WalletImportKind.INCOME
            }
        val destination = value("destination")
        require(destination == null || destination != account) { "Transfer destination must differ from source" }
        val signed =
            if (kind == WalletImportKind.EXPENSE || destination != null ||
                (kind == WalletImportKind.TRANSFER && amount.signum() < 0)
            ) {
                -minor
            } else {
                minor
            }
        return WalletImportRecord(date, account, category, currency, signed, kind, value("note"), destination)
    }

    private fun parse(csv: String): List<List<String>> {
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
                    Unit
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
