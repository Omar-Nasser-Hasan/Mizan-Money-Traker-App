// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import app.mizan.data.db.RecordRow
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {
    @Test fun emits_excel_friendly_bom_and_escaped_text() {
        val bytes = CsvExport.records(listOf(RecordRow("id", "EXPENSE", 0, "2026-10-16", null, null, null, null, "say \"hi\"", "CLEARED", "a", "Cash", "EGP", -100, -100)))
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("\uFEFFid,date"))
        assertTrue(text.contains("\"say \"\"hi\"\"\""))
    }
}
