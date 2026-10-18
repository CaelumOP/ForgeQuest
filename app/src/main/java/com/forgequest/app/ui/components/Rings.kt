package com.forgequest.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class RingSpec(val progress: Float, val color: Color, val track: Color)

/**
 * Google Fit concentric rings. At 0 a ring shows a single dot at 12 o'clock;
 * it then grows clockwise as a rounded arc. Values over 100 % keep drawing past the top.
 */
@Composable
fun ConcentricRings(
    outer: RingSpec,
    inner: RingSpec,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    stroke: Dp = 16.dp,
    gap: Dp = 6.dp,
    center: @Composable () -> Unit = {},
) {
    val outerP by animateFloatAsState(outer.progress, spring(stiffness = Spring.StiffnessVeryLow), label = "outer")
    val innerP by animateFloatAsState(inner.progress, spring(stiffness = Spring.StiffnessVeryLow), label = "inner")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val outerRadius = this.size.minDimension / 2f - s / 2f
            val innerRadius = outerRadius - s - gap.toPx()
            drawRing(outerRadius, s, outerP, outer)
            drawRing(innerRadius, s, innerP, inner)
        }
        center()
    }
}

private fun DrawScope.drawRing(radius: Float, stroke: Float, progress: Float, spec: RingSpec) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawArc(spec.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
    if (progress <= 0.001f) {
        drawCircle(spec.color, radius = stroke / 2f, center = Offset(center.x, center.y - radius))
        return
    }
    val sweep = 360f * progress.coerceAtMost(1f)
    drawArc(spec.color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    if (progress > 1f) {
        // Second lap drawn slightly brighter so going over is visible but never alarming.
        val extra = 360f * (progress - 1f).coerceAtMost(1f)
        drawArc(spec.color.copy(alpha = 0.55f), -90f, extra, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Single small ring, used for the week circles and rank badge. */
@Composable
fun MiniRing(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, stroke: Dp = 3.dp, content: @Composable () -> Unit = {}) {
    val p by animateFloatAsState(progress, spring(stiffness = Spring.StiffnessLow), label = "mini")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val r = this.size.minDimension / 2f - s / 2f
            val tl = Offset(center.x - r, center.y - r)
            drawArc(track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(s))
            if (p > 0f) drawArc(color, -90f, 360f * p.coerceAtMost(1f), false, tl, Size(r * 2, r * 2), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}
