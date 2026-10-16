// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import app.mizan.data.db.RecordRow
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {
    @Test fun emits_excel_friendly_bom_and_escaped_text() {
        val row =
            RecordRow(
                id = "id",
                entryId = "entry",
                kind = "EXPENSE",
                bookedAt = 0,
                bookedDate = "2026-10-16",
                categoryId = null,
                categoryName = null,
                payeeId = null,
                payeeName = null,
                note = "say \"hi\"",
                status = "CLEARED",
                paymentType = "CASH",
                plannedId = null,
                debtId = null,
                assetTxnId = null,
                createdAt = 0,
                updatedAt = 0,
                accountId = "a",
                accountName = "Cash",
                currency = "EGP",
                amountMinor = -100,
                rateToBase = "1",
                baseMinor = -100,
                labels = "",
                attachments = "",
            )
        val bytes = CsvExport.records(listOf(row))
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("\uFEFFtransactionId,entryId"))
        assertTrue(text.contains("\"say \"\"hi\"\"\""))
    }
}
