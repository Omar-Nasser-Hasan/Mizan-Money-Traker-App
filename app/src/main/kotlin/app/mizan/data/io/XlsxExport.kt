// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import app.mizan.data.db.RecordRow
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal standards-compliant XLSX workbook, written without loading a third-party spreadsheet engine. */
object XlsxExport {
    fun records(rows: List<RecordRow>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.writeEntry(
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
                )
                zip.writeEntry(
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
                )
                zip.writeEntry(
                    "xl/workbook.xml",
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Records" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                )
                zip.writeEntry(
                    "xl/_rels/workbook.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                )
                val header =
                    listOf(
                        "Transaction ID",
                        "Date",
                        "Kind",
                        "Account",
                        "Currency",
                        "Amount minor",
                        "Base minor",
                        "Category",
                        "Payee",
                        "Note",
                    )
                val data =
                    rows.map {
                        listOf(
                            it.id,
                            it.bookedDate,
                            it.kind,
                            it.accountName,
                            it.currency,
                            it.amountMinor.toString(),
                            it.baseMinor.toString(),
                            it.categoryName.orEmpty(),
                            it.payeeName.orEmpty(),
                            it.note,
                        )
                    }
                zip.writeEntry("xl/worksheets/sheet1.xml", worksheet(header, data))
            }
            bytes.toByteArray()
        }

    private fun worksheet(
        header: List<String>,
        rows: List<List<String>>,
    ): String =
        buildString {
            append(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>",
            )
            (listOf(header) + rows).forEachIndexed { rowIndex, row ->
                append("<row r=\"").append(rowIndex + 1).append("\">")
                row.forEachIndexed { columnIndex, value ->
                    append(
                        "<c r=\"",
                    ).append(
                        column(columnIndex),
                    ).append(rowIndex + 1)
                        .append("\" t=\"inlineStr\"><is><t>")
                        .append(xml(value))
                        .append("</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

    private fun column(index: Int): String {
        var value = index + 1
        val result = StringBuilder()
        while (value > 0) {
            value--
            result.insert(0, ('A'.code + value % 26).toChar())
            value /= 26
        }
        return result.toString()
    }

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun ZipOutputStream.writeEntry(
        name: String,
        value: String,
    ) {
        putNextEntry(ZipEntry(name))
        write(value.encodeToByteArray())
        closeEntry()
    }
}
