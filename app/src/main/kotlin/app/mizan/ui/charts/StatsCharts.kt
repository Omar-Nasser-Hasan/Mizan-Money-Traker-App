// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class ChartPoint(
    val label: String,
    val value: Long,
)

@Composable
fun lineAreaChart(
    points: List<ChartPoint>,
    valueLabel: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    var selected by remember(points) { mutableIntStateOf(points.lastIndex.coerceAtLeast(0)) }
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primaryContainer
    Column(modifier) {
        if (points.isEmpty()) {
            Text("No values in this period.")
            return@Column
        }
        Text("${points[selected].label} · ${valueLabel(points[selected].value)}")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        selected = ((tap.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                    }
                },
        ) {
            val min = points.minOf { it.value }
            val max = points.maxOf { it.value }
            val range = (max - min).coerceAtLeast(1L)

            fun x(index: Int): Float = if (points.size == 1) size.width / 2 else index * size.width / (points.size - 1)

            fun y(value: Long): Float = size.height - ((value - min).toFloat() / range) * size.height
            val path =
                Path().apply {
                    moveTo(x(0), y(points[0].value))
                    points.drop(1).forEachIndexed { index, point -> lineTo(x(index + 1), y(point.value)) }
                }
            val area =
                Path().apply {
                    addPath(path)
                    lineTo(x(points.lastIndex), size.height)
                    lineTo(x(0), size.height)
                    close()
                }
            drawPath(area, fillColor)
            drawPath(path, lineColor, style = Stroke(width = 4f))
            drawCircle(
                lineColor,
                radius = 8f,
                center =
                    androidx.compose.ui.geometry
                        .Offset(x(selected), y(points[selected].value)),
            )
        }
    }
}

@Composable
fun spendingDonut(
    values: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    val total = values.sumOf { it.second }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            if (total == 0L) return@Canvas
            var angle = -90f
            values.forEachIndexed { index, (_, amount) ->
                val sweep = amount.toFloat() / total * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = angle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(size.minDimension * 0.24f),
                )
                angle += sweep
            }
        }
        values.take(5).forEach { (name, amount) -> Text("$name · $amount") }
    }
}
