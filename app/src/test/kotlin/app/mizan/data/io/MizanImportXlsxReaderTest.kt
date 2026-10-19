// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MizanImportXlsxReaderTest {
    @Test fun reads_the_named_sheet_and_shared_strings_as_escaped_csv() {
        val workbook =
            zip(
                "xl/workbook.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Start here" sheetId="1" r:id="rId1"/><sheet name="Mizan Import" sheetId="2" r:id="rId2"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet7.xml"/></Relationships>""",
                "xl/sharedStrings.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>event_id</t></si><si><t>note</t></si><si><t>payee</t></si><si><t>event_kind</t></si><si><t>tea, coffee</t></si><si><t>said "keep"</t></si><si><t>EXPENSE</t></si></sst>""",
                "xl/worksheets/sheet7.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>3</v></c><c r="C1" t="s"><v>1</v></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>row-1</t></is></c><c r="B2" t="s"><v>6</v></c><c r="C2" t="s"><v>4</v></c></row><row r="3"><c r="A3" t="inlineStr"><is><t>row-2</t></is></c><c r="B3" t="s"><v>6</v></c><c r="C3" t="s"><v>5</v></c></row></sheetData></worksheet>""",
            )

        val csv = MizanImportXlsxReader.toCsv(workbook)

        assertEquals(
            "\"event_id\",\"event_kind\",\"note\"\n\"row-1\",\"EXPENSE\",\"tea, coffee\"\n\"row-2\",\"EXPENSE\",\"said \"\"keep\"\"\"",
            csv,
        )
    }

    @Test fun rejects_doctype_workbook_parts() {
        val workbook =
            zip(
                "xl/workbook.xml" to
                    """<!DOCTYPE workbook [<!ENTITY x "blocked">]><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Mizan Import" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to
                    """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>""",
            )

        val error = runCatching { MizanImportXlsxReader.toCsv(workbook) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("DOCTYPE declarations are not allowed in workbook XML", error?.message)
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
