package com.forgequest.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.app.domain.TrendPoint
import com.forgequest.app.domain.Trends
import com.forgequest.app.ui.theme.Fit

/**
 * One draw-on reveal, played once when a chart first appears, then still. Progress 0..1.
 * Shared by every chart so the screen animates as one orchestrated moment.
 */
@Composable
fun rememberReveal(key: Any?): Float {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.snapTo(0f); anim.animateTo(1f, tween(900)) }
    return anim.value
}

/** Tiny trend line for metric tiles: 2 dp line, faint area, emphasised last point. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, reveal: Float = 1f) {
    val card = Fit.colors.card
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val range = (hi - lo).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        fun pos(i: Int) = Offset(i * stepX, 4.dp.toPx() + (1f - (values[i] - lo) / range) * (size.height - 10.dp.toPx()))
        val line = Path().apply { values.indices.forEach { i -> if (i == 0) moveTo(pos(0).x, pos(0).y) else lineTo(pos(i).x, pos(i).y) } }
        val area = Path().apply { addPath(line); lineTo(pos(values.lastIndex).x, size.height); lineTo(0f, size.height); close() }
        clipRect(right = size.width * reveal) {
            drawPath(area, color.copy(alpha = 0.12f))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (reveal >= 0.99f) {
            drawCircle(color, 4.dp.toPx(), pos(values.lastIndex))
            drawCircle(card, 1.8.dp.toPx(), pos(values.lastIndex))
        }
    }
}

/** Weekly bars with the latest one emphasised. 4 dp rounded tops anchored to the baseline. */
@Composable
fun BarRow(values: List<Int>, color: Color, track: Color, modifier: Modifier = Modifier, reveal: Float = 1f, highlightLast: Boolean = true) {
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max) * size.height * reveal
            val isLast = i == values.lastIndex
            drawRoundRect(
                if (isLast && highlightLast) color else track,
                topLeft = Offset(i * (w + gap), size.height - h.coerceAtLeast(3.dp.toPx())),
                size = Size(w, h.coerceAtLeast(3.dp.toPx())),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }
    }
}

/**
 * The strength chart: best set per session, gold dots on records, a dashed all-time-high line, a recessive grid,
 * and drag-to-read. [format] turns a value into the label shown while scrubbing.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    reveal: Float = 1f,
    average: List<TrendPoint>? = null,
    showRecords: Boolean = true,
    format: (Float) -> String = { "%.0f".format(it) },
    dateLabel: (Long) -> String,
) {
    val c = Fit.colors
    val measurer = rememberTextMeasurer()
    var scrub by remember { mutableIntStateOf(-1) }
    val labelStyle = TextStyle(fontSize = 10.sp, color = c.textSecondary)
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .pointerInput(points) {
                    detectDragGestures(onDragEnd = { scrub = -1 }, onDragCancel = { scrub = -1 }) { change, _ ->
                        if (points.size >= 2) scrub = ((change.position.x - 28.dp.toPx()) / ((size.width - 40.dp.toPx()) / (points.size - 1))).toInt().coerceIn(0, points.lastIndex)
                    }
                }
                .pointerInput(points) { detectTapGestures(onPress = { scrub = -1 }) },
        ) {
            if (points.size < 2) return@Canvas
            val left = 28.dp.toPx(); val right = size.width - 12.dp.toPx(); val top = 14.dp.toPx(); val bottom = size.height - 22.dp.toPx()
            val all = points.map { it.value } + (average?.map { it.value } ?: emptyList())
            val lo = all.min(); val hi = all.max()
            val margin = ((hi - lo) * 0.25f).coerceAtLeast(if (hi >= 20f) hi * 0.03f else 1f)
            val yMin = lo - margin; val yMax = hi + margin
            val x0 = points.first().epochDay; val x1 = points.last().epochDay
            fun x(day: Long) = left + (day - x0).toFloat() / (x1 - x0).coerceAtLeast(1) * (right - left)
            fun y(v: Float) = top + (1f - (v - yMin) / (yMax - yMin)) * (bottom - top)
            // Grid: three recessive lines with values that exist on the scale.
            for (g in 0..2) {
                val v = yMin + (yMax - yMin) * g / 2f
                drawLine(c.divider, Offset(left, y(v)), Offset(right, y(v)), 1f)
                drawText(measurer, format(v), Offset(0f, y(v) - 6.sp.toPx()), labelStyle)
            }
            // All-time high
            val best = points.maxOf { it.value }
            drawLine(c.gold.copy(alpha = 0.6f), Offset(left, y(best)), Offset(right, y(best)), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            // Average line (body weight)
            average?.takeIf { it.size >= 2 }?.let { avg ->
                val p = Path().apply { avg.forEachIndexed { i, pt -> if (i == 0) moveTo(x(pt.epochDay), y(pt.value)) else lineTo(x(pt.epochDay), y(pt.value)) } }
                clipRect(right = left + (right - left) * reveal) { drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)) }
            }
            val line = Path().apply { points.forEachIndexed { i, pt -> if (i == 0) moveTo(x(pt.epochDay), y(pt.value)) else lineTo(x(pt.epochDay), y(pt.value)) } }
            val area = Path().apply { addPath(line); lineTo(x(x1), bottom); lineTo(x(x0), bottom); close() }
            val lineColor = if (average != null) color.copy(alpha = 0.45f) else color
            clipRect(right = left + (right - left) * reveal) {
                if (average == null) drawPath(area, color.copy(alpha = 0.10f))
                drawPath(line, lineColor, style = Stroke(if (average != null) 1.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round))
            }
            if (reveal >= 0.99f) points.forEachIndexed { i, pt ->
                val isRecord = showRecords && pt.isRecord
                if (isRecord || i == points.lastIndex || average == null) {
                    drawCircle(if (isRecord) c.gold else lineColor, (if (isRecord) 5.dp else 3.dp).toPx(), Offset(x(pt.epochDay), y(pt.value)))
                    drawCircle(c.card, (if (isRecord) 2.5.dp else 1.5.dp).toPx(), Offset(x(pt.epochDay), y(pt.value)))
                }
            }
            // Date labels at both ends.
            drawText(measurer, dateLabel(x0), Offset(left, bottom + 6.dp.toPx()), labelStyle)
            val endLabel = measurer.measure(dateLabel(x1), labelStyle)
            drawText(measurer, dateLabel(x1), Offset(right - endLabel.size.width, bottom + 6.dp.toPx()), labelStyle)
            // Scrub readout
            if (scrub in points.indices) {
                val pt = points[scrub]
                drawLine(c.textSecondary, Offset(x(pt.epochDay), top), Offset(x(pt.epochDay), bottom), 1f)
                drawCircle(c.textPrimary, 5.dp.toPx(), Offset(x(pt.epochDay), y(pt.value)))
                val text = "${format(pt.value)} · ${dateLabel(pt.epochDay)}${if (pt.isRecord) " · PR" else ""}"
                val m = measurer.measure(text, TextStyle(fontSize = 12.sp, color = c.textPrimary))
                val bx = (x(pt.epochDay) - m.size.width / 2f).coerceIn(left, right - m.size.width)
                drawRoundRect(c.cardHigh, Offset(bx - 8.dp.toPx(), 0f), Size(m.size.width + 16.dp.toPx(), m.size.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                drawText(m, topLeft = Offset(bx, 3.dp.toPx()))
            }
        }
    }
}

/** GitHub-style consistency grid: rows are weekdays, columns are weeks, oldest on the left. */
@Composable
fun Heatmap(grid: List<List<Trends.DayCell>>, color: Color, modifier: Modifier = Modifier, reveal: Float = 1f) {
    val c = Fit.colors
    val weeks = grid.firstOrNull()?.size ?: return
    Canvas(modifier.fillMaxWidth().height((7 * 14 + 6 * 4).dp)) {
        val gap = 4.dp.toPx()
        val cell = ((size.width - gap * (weeks - 1)) / weeks).coerceAtMost(14.dp.toPx())
        val shown = (weeks * reveal).toInt().coerceIn(1, weeks)
        grid.forEachIndexed { row, cells ->
            cells.forEachIndexed { col, state ->
                if (col >= shown) return@forEachIndexed
                val fill = when (state) {
                    Trends.DayCell.FUTURE -> Color.Transparent
                    Trends.DayCell.REST -> c.cardHigh
                    Trends.DayCell.DONE -> color.copy(alpha = 0.55f)
                    Trends.DayCell.RECORD -> c.gold
                }
                drawRoundRect(fill, Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
        }
    }
}

/** A value with its change. Gains read in the gain colour, anything else is quiet grey; nothing is ever red. */
@Composable
fun DeltaChip(text: String, positive: Boolean) {
    val c = Fit.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (positive) c.chartGain else c.textSecondary,
        maxLines = 1,
        modifier = Modifier.background(if (positive) c.chartGainTrack else c.cardHigh, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
