// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.io

import java.math.BigDecimal
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
)

data class WalletImportPreview(
    val records: List<WalletImportRecord>,
    val accountTotalsMinor: Map<String, Long>,
    val errors: List<String>,
)

/** Header-name mapping for Wallet's CSV export. This dry run never mutates storage. */
object WalletCsvImporter {
    fun preview(csv: String): WalletImportPreview {
        val rows =
            csv
                .lineSequence()
                .filter(String::isNotBlank)
                .map(::parseRow)
                .toList()
        if (rows.isEmpty()) return WalletImportPreview(emptyList(), emptyMap(), listOf("The CSV is empty."))
        val headers = rows.first().map { it.trim().lowercase() }
        val records = mutableListOf<WalletImportRecord>()
        val errors = mutableListOf<String>()
        rows.drop(1).forEachIndexed { index, row ->
            runCatching { record(headers, row) }.onSuccess(records::add).onFailure { errors += "Line ${index + 2}: ${it.message}" }
        }
        return WalletImportPreview(
            records,
            records.groupBy(WalletImportRecord::account).mapValues { (_, records) ->
                records.sumOf(WalletImportRecord::amountMinor)
            },
            errors,
        )
    }

    private fun record(
        headers: List<String>,
        row: List<String>,
    ): WalletImportRecord {
        fun value(vararg names: String): String? =
            names
                .firstNotNullOfOrNull { name ->
                    headers.indexOf(name).takeIf { it >= 0 }?.let(row::getOrNull)
                }?.trim()
                ?.takeIf(String::isNotEmpty)
        val date = LocalDate.parse(requireNotNull(value("date", "transaction date")) { "Missing Date" })
        val account = requireNotNull(value("account", "account name")) { "Missing Account" }
        val category = value("category", "category name") ?: "Uncategorized"
        val currency = value("currency") ?: "EGP"
        val amount = requireNotNull(value("amount", "amount in account currency")) { "Missing Amount" }.replace(",", "").toBigDecimal()
        val type = value("type", "transaction type")?.lowercase().orEmpty()
        val investment = category.contains("investment", ignoreCase = true)
        val kind =
            when {
                investment -> WalletImportKind.ASSET_BUY
                type.contains("transfer") -> WalletImportKind.TRANSFER
                amount.signum() >= 0 || type.contains("income") -> WalletImportKind.INCOME
                else -> WalletImportKind.EXPENSE
            }
        val minor =
            amount
                .abs()
                .movePointRight(if (currency == "JPY") 0 else 2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        return WalletImportRecord(
            date,
            account,
            category,
            currency,
            if (kind ==
                WalletImportKind.EXPENSE
            ) {
                -minor
            } else {
                minor
            },
            kind,
            value("note", "description"),
        )
    }

    private fun parseRow(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val char = line[index]) {
                '"' -> {
                    if (quoted && line.getOrNull(index + 1) == '"') {
                        current.append(char)
                        index++
                    } else {
                        quoted = !quoted
                    }
                }

                ',' -> {
                    if (quoted) {
                        current.append(char)
                    } else {
                        fields += current.toString()
                        current.clear()
                    }
                }

                else -> {
                    current.append(char)
                }
            }
            index++
        }
        require(!quoted) { "Unclosed quoted field" }
        fields += current.toString()
        return fields
    }
}
