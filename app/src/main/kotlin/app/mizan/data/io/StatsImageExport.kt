// Copyright (c) 2026 Mizan contributors
package app.mizan.data.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StatsImageExport {
    suspend fun write(
        context: Context,
        bitmap: Bitmap,
    ): File =
        withContext(Dispatchers.IO) {
            val output = File(context.filesDir, "exports").also { it.mkdirs() }
            File(output, "mizan-stats-${System.currentTimeMillis()}.png").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

    suspend fun write(
        context: Context,
        title: String,
        lines: List<String>,
    ): File =
        withContext(Dispatchers.IO) {
            val lineHeight = 56
            val bitmap = Bitmap.createBitmap(1080, 180 + lines.size * lineHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(22, 27, 38))
            val titlePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(233, 178, 53)
                    textSize = 48f
                }
            val bodyPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(244, 245, 247)
                    textSize = 32f
                }
            canvas.drawText("Mizan", 56f, 64f, titlePaint)
            canvas.drawText(title, 56f, 124f, bodyPaint)
            lines.forEachIndexed { index, line -> canvas.drawText(line, 56f, (180 + index * lineHeight).toFloat(), bodyPaint) }
            val output = File(context.filesDir, "exports").also { it.mkdirs() }
            File(output, "mizan-stats-${System.currentTimeMillis()}.png").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
}
