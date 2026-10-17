// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

data class MzDonutSlice(
    val label: String,
    val value: Long,
    val color: Color,
)

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzSparkline(
    values: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = LocalMzTokens.current.champagne,
) {
    Canvas(modifier = modifier.height(36.dp)) { drawLineSeries(values, color, 2.dp.toPx()) }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzAreaChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    onPointSelected: (Int) -> Unit = {},
) {
    val tokens = LocalMzTokens.current
    Canvas(
        modifier =
            modifier.fillMaxWidth().height(180.dp).pointerInput(values) {
                detectTapGestures { offset ->
                    if (values.isNotEmpty()) {
                        onPointSelected(
                            ((offset.x / size.width) * values.lastIndex).toInt().coerceIn(0, values.lastIndex),
                        )
                    }
                }
            },
    ) {
        val points = points(values)
        if (points.isEmpty()) return@Canvas
        val path = pathFor(points)
        val fill =
            Path().apply {
                addPath(path)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
        drawPath(fill, tokens.champagne.copy(alpha = 0.24f))
        drawPath(path, tokens.champagne, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzBarChart(
    income: List<Long>,
    expense: List<Long>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val count = max(income.size, expense.size)
        if (count == 0) return@Canvas
        val absolute = (income + expense).maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1L) ?: 1L
        val midpoint = size.height / 2f
        val slot = size.width / count
        val bar = min(slot * 0.28f, 18.dp.toPx())
        drawLine(tokens.outline.copy(alpha = 0.4f), Offset.Zero.copy(y = midpoint), Offset(size.width, midpoint), 1.dp.toPx())
        repeat(count) { index ->
            val x = index * slot + slot / 2f
            val incomeHeight = (income.getOrElse(index) { 0L }.toFloat() / absolute) * (midpoint - 8.dp.toPx())
            val expenseHeight = (expense.getOrElse(index) { 0L }.toFloat() / absolute) * (midpoint - 8.dp.toPx())
            drawRect(tokens.income, Offset(x - bar - 2.dp.toPx(), midpoint - incomeHeight), Size(bar, incomeHeight))
            drawRect(tokens.expense, Offset(x + 2.dp.toPx(), midpoint), Size(bar, expenseHeight))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzMultiLine(
    current: List<Long>,
    previous: List<Long>,
    lastYear: List<Long>?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val all = current + previous + (lastYear ?: emptyList())
        val scale = scaleFor(all)
        drawLineSeries(current, tokens.champagne, 2.dp.toPx(), scale)
        drawLineSeries(previous, tokens.textDim, 1.dp.toPx(), scale)
        lastYear?.let { drawLineSeries(it, tokens.transfer, 1.dp.toPx(), scale, dashed = true) }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzDonut(
    slices: List<MzDonutSlice>,
    modifier: Modifier = Modifier,
    centerText: String,
    selectedIndex: Int? = null,
    onSliceSelected: (Int) -> Unit = {},
) {
    val total = slices.sumOf { it.value.coerceAtLeast(0L) }.coerceAtLeast(1L)
    Box(modifier = modifier.height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier =
                Modifier.matchParentSize().pointerInput(slices) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle =
                            (
                                (
                                    Math.toDegrees(kotlin.math.atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())) +
                                        450
                                ) %
                                    360
                            ).toFloat()
                        var running = 0f
                        slices.forEachIndexed { index, slice ->
                            running += slice.value.coerceAtLeast(0L).toFloat() / total * 360f
                            if (angle <= running) return@forEachIndexed onSliceSelected(index)
                        }
                    }
                },
        ) {
            val stroke = size.minDimension * 0.22f
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.value.coerceAtLeast(0L).toFloat() / total * 360f
                drawArc(
                    color = if (selectedIndex == null || selectedIndex == index) slice.color else slice.color.copy(alpha = 0.28f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Text(centerText, style = MzTypography.amountMedium, textAlign = TextAlign.Center)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzWaterfall(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxValue = values.fold(0L) { total, value -> max(kotlin.math.abs(total), kotlin.math.abs(total + value)) }.coerceAtLeast(1L)
        var cumulative = 0L
        val width = size.width / values.size
        values.forEachIndexed { index, value ->
            val before = cumulative.toFloat() / maxValue
            cumulative += value
            val after = cumulative.toFloat() / maxValue
            val top = size.height / 2f - max(before, after) * size.height * 0.42f
            val height = kotlin.math.abs(after - before) * size.height * 0.42f
            drawRect(
                if (value >=
                    0
                ) {
                    tokens.income
                } else {
                    tokens.expense
                },
                Offset(index * width + width * 0.18f, top),
                Size(width * 0.64f, height.coerceAtLeast(2.dp.toPx())),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzCompositionBar(
    slices: List<MzDonutSlice>,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value.coerceAtLeast(0L) }.coerceAtLeast(1L)
    Canvas(modifier = modifier.fillMaxWidth().height(12.dp).clip(MzShapes.utility)) {
        var start = 0f
        slices.forEach { slice ->
            val width = size.width * slice.value.coerceAtLeast(0L).toFloat() / total
            drawRect(slice.color, Offset(start, 0f), Size(width, size.height))
            start += width
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzProgressRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Box(modifier = modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 8.dp.toPx()
            drawArc(tokens.raisedSurface, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(tokens.champagne, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, style = MzTypography.bodySmall, textAlign = TextAlign.Center)
    }
}

private data class SeriesScale(
    val min: Long,
    val max: Long,
)

private fun scaleFor(values: List<Long>): SeriesScale {
    if (values.isEmpty()) return SeriesScale(0L, 1L)
    val minimum = values.minOrNull() ?: 0L
    val maximum = values.maxOrNull() ?: 1L
    return if (minimum == maximum) SeriesScale(minimum - 1L, maximum + 1L) else SeriesScale(minimum, maximum)
}

private fun DrawScope.points(
    values: List<Long>,
    scale: SeriesScale = scaleFor(values),
): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val span = (scale.max - scale.min).toFloat().coerceAtLeast(1f)
    return values.mapIndexed { index, value ->
        val x = if (values.size == 1) size.width / 2f else size.width * index / values.lastIndex
        val y = size.height - ((value - scale.min) / span) * size.height
        Offset(x, y)
    }
}

private fun pathFor(points: List<Offset>): Path =
    Path().apply {
        points.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }

private fun DrawScope.drawLineSeries(
    values: List<Long>,
    color: Color,
    strokeWidth: Float,
    scale: SeriesScale = scaleFor(values),
    dashed: Boolean = false,
) {
    val points = points(values, scale)
    if (points.size < 2) return
    drawPath(
        pathFor(points),
        color,
        style =
            if (dashed) {
                Stroke(
                    strokeWidth,
                    pathEffect =
                        androidx.compose.ui.graphics.PathEffect
                            .dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            } else {
                Stroke(strokeWidth, cap = StrokeCap.Round)
            },
    )
}
