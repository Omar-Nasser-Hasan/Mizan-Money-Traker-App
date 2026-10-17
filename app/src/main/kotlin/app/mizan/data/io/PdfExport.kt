// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import app.mizan.data.db.RecordRow
import java.io.File
import java.io.FileOutputStream

object PdfExport {
    fun records(
        file: File,
        rows: List<RecordRow>,
    ) {
        val document = PdfDocument()
        try {
            val lines =
                listOf("Mizan records export") +
                    rows.map { row ->
                        "${row.bookedDate}  ${row.kind}  ${row.accountName}  ${row.amountMinor} ${row.currency}  ${row.payeeName.orEmpty()}"
                    }
            lines.chunked(45).forEachIndexed { pageIndex, pageLines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
                pageLines.forEachIndexed { lineIndex, line -> page.canvas.drawText(line.take(110), 36f, 44f + lineIndex * 16f, paint) }
                document.finishPage(page)
            }
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
