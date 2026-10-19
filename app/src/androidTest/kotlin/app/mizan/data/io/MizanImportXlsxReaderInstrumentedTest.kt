// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class MizanImportXlsxReaderInstrumentedTest {
    @Test fun reads_a_mizan_workbook_with_the_platform_xml_parser() {
        val workbook =
            zip(
                "xl/workbook.xml" to
                    """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Mizan Import" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to
                    """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>event_kind</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>EXPENSE</t></is></c></row></sheetData></worksheet>""",
            )

        assertEquals("\"event_kind\"\n\"EXPENSE\"", MizanImportXlsxReader.toCsv(workbook))
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.encodeToByteArray())
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
}
