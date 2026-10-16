// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import app.mizan.data.db.RecordRow

object CsvExport {
    fun records(rows: List<RecordRow>): ByteArray {
        val header =
            "transactionId,entryId,bookedAt,bookedDate,kind,accountId,account,currency,amountMinor,rateToBase,baseMinor,categoryId,category,payeeId,payee,note,status,paymentType,plannedId,debtId,assetTxnId,labels,attachments,createdAt,updatedAt\n"
        val body =
            rows.joinToString("\n") { row ->
                listOf(
                    row.id,
                    row.entryId,
                    row.bookedAt,
                    row.bookedDate,
                    row.kind,
                    row.accountId,
                    row.accountName,
                    row.currency,
                    row.amountMinor,
                    row.rateToBase,
                    row.baseMinor,
                    row.categoryId.orEmpty(),
                    row.categoryName.orEmpty(),
                    row.payeeId.orEmpty(),
                    row.payeeName.orEmpty(),
                    row.note,
                    row.status,
                    row.paymentType,
                    row.plannedId.orEmpty(),
                    row.debtId.orEmpty(),
                    row.assetTxnId.orEmpty(),
                    row.labels,
                    row.attachments,
                    row.createdAt,
                    row.updatedAt,
                ).joinToString(",") {
                    quote(it.toString())
                }
            }
        return ("\uFEFF" + header + body).encodeToByteArray()
    }

    private fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
