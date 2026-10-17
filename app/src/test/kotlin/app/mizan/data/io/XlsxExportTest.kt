// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

class XlsxExportTest {
    @Test
    fun `creates an office workbook with a records sheet`() {
        val names =
            ZipInputStream(XlsxExport.records(emptyList()).inputStream()).use { zip ->
                generateSequence { zip.nextEntry?.name }.toList()
            }
        assertTrue("xl/workbook.xml" in names)
        assertTrue("xl/worksheets/sheet1.xml" in names)
    }
}
