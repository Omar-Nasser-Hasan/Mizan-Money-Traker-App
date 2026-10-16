// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import app.mizan.data.db.RecordRow

object CsvExport {
    fun records(rows: List<RecordRow>): ByteArray {
        val header = "id,date,kind,account,currency,amountMinor,baseMinor,category,payee,note,status\n"
        val body = rows.joinToString("\n") { row ->
            listOf(row.id, row.bookedDate, row.kind, row.accountName, row.currency, row.amountMinor, row.baseMinor, row.categoryName.orEmpty(), row.payeeName.orEmpty(), row.note, row.status).joinToString(",") { quote(it.toString()) }
        }
        return ("\uFEFF" + header + body).encodeToByteArray()
    }

    private fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
