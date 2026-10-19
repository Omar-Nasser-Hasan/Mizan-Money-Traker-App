// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Reads the app's flat `Mizan Import` sheet from an OOXML workbook and exposes its cells as CSV. */
object MizanImportXlsxReader {
    private const val OFFICE_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun toCsv(bytes: ByteArray): String {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val workbookXml = requireNotNull(entries["xl/workbook.xml"]) { "Not an Excel workbook" }
        val relationshipsXml = requireNotNull(entries["xl/_rels/workbook.xml.rels"]) { "Workbook sheet map is missing" }
        val workbook = parseXml(workbookXml)
        val sheet =
            workbook.getElementsByTagNameNS("*", "sheet").let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .firstOrNull { it.getAttribute("name") == "Mizan Import" }
            } ?: error("This workbook does not contain a Mizan Import sheet")
        val relationId = sheet.getAttributeNS(OFFICE_REL_NS, "id").ifBlank { sheet.getAttribute("r:id") }
        require(relationId.isNotBlank()) { "Mizan Import sheet relationship is missing" }
        val relationships = parseXml(relationshipsXml).getElementsByTagNameNS("*", "Relationship")
        val target =
            (0 until relationships.length)
                .map { relationships.item(it) as Element }
                .firstOrNull { it.getAttribute("Id") == relationId }
                ?.getAttribute("Target")
                ?.takeIf(String::isNotBlank)
                ?: error("Mizan Import sheet cannot be located")
        val sheetPath = normalizeSheetPath(target)
        val sheetBytes = requireNotNull(entries[sheetPath]) { "Mizan Import sheet data is missing" }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val document = parseXml(sheetBytes)
        val rowNodes = document.getElementsByTagNameNS("*", "row")
        val rows =
            (0 until rowNodes.length).map { rowIndex ->
                val rowNode = rowNodes.item(rowIndex) as Element
                val cellNodes = rowNode.getElementsByTagNameNS("*", "c")
                val cells = mutableMapOf<Int, String>()
                var maxColumn = -1
                (0 until cellNodes.length).forEach { cellIndex ->
                    val cell = cellNodes.item(cellIndex) as Element
                    val reference = cell.getAttribute("r")
                    val column = columnIndex(reference)
                    if (column >= 0) {
                        cells[column] = cellValue(cell, sharedStrings)
                        maxColumn = maxOf(maxColumn, column)
                    }
                }
                if (maxColumn < 0) emptyList() else (0..maxColumn).map { cells[it].orEmpty() }
            }
        return rows.joinToString("\n") { row -> row.joinToString(",") { csvCell(it) } }
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val items = parseXml(bytes).getElementsByTagNameNS("*", "si")
        return (0 until items.length).map { index ->
            val textNodes = (items.item(index) as Element).getElementsByTagNameNS("*", "t")
            (0 until textNodes.length).joinToString("") { textNodes.item(it).textContent }
        }
    }

    private fun cellValue(
        cell: Element,
        sharedStrings: List<String>,
    ): String =
        when (cell.getAttribute("t")) {
            "s" -> {
                val sharedIndex =
                    cell
                        .getElementsByTagNameNS("*", "v")
                        .item(0)
                        ?.textContent
                        ?.toIntOrNull()
                        ?: error("Invalid shared string cell")
                sharedStrings.getOrNull(sharedIndex) ?: error("Shared string index is out of range")
            }

            "inlineStr" -> {
                val textNodes = cell.getElementsByTagNameNS("*", "t")
                (0 until textNodes.length).joinToString("") { textNodes.item(it).textContent }
            }

            else -> {
                cell
                    .getElementsByTagNameNS("*", "v")
                    .item(0)
                    ?.textContent
                    .orEmpty()
            }
        }

    private fun columnIndex(reference: String): Int {
        var index = 0
        var hasColumn = false
        for (char in reference) {
            if (char !in 'A'..'Z') break
            hasColumn = true
            index = index * 26 + (char - 'A' + 1)
        }
        return if (hasColumn) index - 1 else -1
    }

    private fun normalizeSheetPath(target: String): String {
        val normalized = target.replace('\\', '/').removePrefix("/")
        return if (normalized.startsWith("xl/")) normalized else "xl/$normalized"
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }.newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private object XMLConstants {
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    }
}
