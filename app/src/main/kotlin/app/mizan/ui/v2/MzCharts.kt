// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

data class MzDonutSlice(
    val label: String,
    val value: Long,
    val color: Color,
)

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzChartCard(
    title: String,
    axisLabels: List<String> = listOf("Jan", "Apr", "Jun"),
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 180.dp,
    squareChart: Boolean = false,
    showGrid: Boolean = false,
    showAxisLabels: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val tokens = LocalMzTokens.current
    val selectedAxisLabels =
        when {
            axisLabels.size <= 3 -> axisLabels
            else -> listOf(axisLabels.first(), axisLabels[axisLabels.lastIndex / 2], axisLabels.last())
        }
    MzCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MzTypography.title)
            HorizontalDivider(Modifier.padding(top = 10.dp), color = tokens.outline.copy(alpha = 0.35f), thickness = 1.dp)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val chartBoxModifier =
                    if (squareChart) {
                        Modifier.size(minOf(maxWidth - 24.dp, 200.dp)).align(Alignment.Center)
                    } else {
                        Modifier.fillMaxWidth().height(chartHeight)
                    }
                Box(
                    modifier =
                        if (showGrid) {
                            chartBoxModifier.drawBehind {
                                repeat(3) { index ->
                                    val y = size.height * (index + 1) / 4f
                                    drawLine(
                                        color = tokens.outline.copy(alpha = 0.25f),
                                        start = Offset.Zero.copy(y = y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            }
                        } else {
                            chartBoxModifier
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    content(Modifier.fillMaxSize())
                }
            }
            if (showAxisLabels) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    selectedAxisLabels.forEach { label ->
                        Text(label, color = tokens.textDim, style = MzTypography.bodySmall)
                    }
                }
            }
        }
    }
}

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
fun MzLineChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val chartPoints = points(values)
        if (chartPoints.size < 2) return@Canvas
        drawPath(pathFor(chartPoints), tokens.champagne, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
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
    changeText: String? = null,
    selectedIndex: Int? = null,
    onSliceSelected: (Int) -> Unit = {},
) {
    val tokens = LocalMzTokens.current
    val total = slices.sumOf { it.value.coerceAtLeast(0L) }.coerceAtLeast(1L)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf((maxWidth - 24.dp).coerceAtLeast(0.dp), 200.dp)
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier =
                        Modifier.fillMaxSize().pointerInput(slices) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val angle =
                                    (
                                        (
                                            Math.toDegrees(
                                                kotlin.math.atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble()),
                                            ) +
                                                450
                                        ) %
                                            360
                                    ).toFloat()
                                var running = 0f
                                slices.forEachIndexed { index, slice ->
                                    val segmentStart = running
                                    running += slice.value.coerceAtLeast(0L).toFloat() / total * 360f
                                    if (angle >= segmentStart && (angle < running || index == slices.lastIndex)) {
                                        return@forEachIndexed onSliceSelected(index)
                                    }
                                }
                            }
                        },
                ) {
                    val diameter = size.minDimension
                    // The resulting inner diameter is 56% of the ring diameter.
                    val stroke = diameter * 0.22f
                    val arcDiameter = diameter - stroke
                    val inset = (diameter - arcDiameter) / 2f
                    val arcTopLeft = Offset(inset, inset)
                    val arcSize = Size(arcDiameter, arcDiameter)
                    drawCircle(tokens.surface, radius = diameter * 0.28f)
                    val gapDegrees = 2.dp.toPx() / (diameter * 0.39f) * (180f / Math.PI.toFloat())
                    var start = -90f
                    slices.forEachIndexed { index, slice ->
                        val sweep = slice.value.coerceAtLeast(0L).toFloat() / total * 360f
                        val hasGaps = slices.size > 1
                        drawArc(
                            color = if (selectedIndex != null && selectedIndex != index) slice.color.copy(alpha = 0.28f) else slice.color,
                            startAngle = start + if (hasGaps) gapDegrees / 2f else 0f,
                            sweepAngle = (sweep - if (hasGaps) gapDegrees else 0f).coerceAtLeast(0f),
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Butt),
                        )
                        start += sweep
                    }
                }
                Column(
                    modifier = Modifier.fillMaxSize(0.55f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    chartCenterText(centerText, MzTypography.amountSmall)
                    changeText?.let {
                        chartCenterText(it, MzTypography.bodySmall, LocalMzTokens.current.income)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(slice.color))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label,
                        color = tokens.text,
                        style = MzTypography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text("${slice.value.coerceAtLeast(0L) * 100 / total}%", color = tokens.textDim, style = MzTypography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Text(legendAmount(slice.value), color = tokens.text, style = MzTypography.amountSmall)
                }
            }
        }
    }
}

@Composable
private fun chartCenterText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = LocalMzTokens.current.text,
) {
    val textMeasurer =
        androidx.compose.ui.text
            .rememberTextMeasurer()
    val measuredWidth = remember(text, style) { textMeasurer.measure(AnnotatedString(text), style).size.width }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val availableWidth = constraints.maxWidth.toFloat()
        val fit = if (availableWidth > 0f && measuredWidth > availableWidth) availableWidth / measuredWidth else 1f
        Text(
            text,
            color = color,
            style = if (fit < 1f) style.copy(fontSize = style.fontSize * fit) else style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
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
        val maxValue =
            values
                .fold(0L) { total, value -> max(kotlin.math.abs(total), kotlin.math.abs(total + value)) }
                .coerceAtLeast(1L)
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            val gap = 2.dp.toPx()
            val available = (size.width - gap * (slices.size - 1).coerceAtLeast(0)).coerceAtLeast(0f)
            var start = 0f
            slices.forEach { slice ->
                val width = available * slice.value.coerceAtLeast(0L).toFloat() / total
                drawRoundRect(
                    slice.color,
                    Offset(start, 0f),
                    Size(width, size.height),
                    androidx.compose.ui.geometry
                        .CornerRadius(size.height / 2),
                )
                start += width + gap
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(slice.color))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label,
                        color = LocalMzTokens.current.text,
                        style = MzTypography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(legendAmount(slice.value), color = LocalMzTokens.current.text, style = MzTypography.amountSmall)
                }
            }
        }
    }
}

private fun legendAmount(value: Long): String =
    java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(
        java.math.BigDecimal
            .valueOf(value)
            .movePointLeft(2),
    )

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
            drawArc(
                tokens.champagne,
                -90f,
                360f * progress.coerceIn(0f, 1f),
                false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = tokens.text,
                style = MzTypography.title.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            )
            Text(
                "of budget",
                color = tokens.textDim,
                style = MzTypography.bodySmall.copy(fontSize = 10.sp),
                textAlign = TextAlign.Center,
            )
        }
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
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, points.first().y)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val previous = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 2).coerceAtMost(points.lastIndex)]
            val deltaX = end.x - start.x
            val firstControl = Offset(start.x + deltaX / 3f, start.y + (end.y - previous.y) / 6f)
            val secondControl = Offset(end.x - deltaX / 3f, end.y - (next.y - start.y) / 6f)
            val lowY = min(start.y, end.y)
            val highY = max(start.y, end.y)
            cubicTo(
                firstControl.x,
                firstControl.y.coerceIn(lowY, highY),
                secondControl.x,
                secondControl.y.coerceIn(lowY, highY),
                end.x,
                end.y,
            )
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
