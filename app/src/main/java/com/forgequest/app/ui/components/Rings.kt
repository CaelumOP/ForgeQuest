package com.forgequest.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class RingSpec(val progress: Float, val color: Color, val track: Color)

/**
 * TalkBack: with a [description] the ring and its centre read as that one sentence ("Calories 1,850 of 2,200");
 * the drawing itself is never exposed.
 */
private fun Modifier.ringSemantics(description: String?): Modifier =
    if (description != null) clearAndSetSemantics { contentDescription = description } else this

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
    description: String? = null,
    center: @Composable () -> Unit = {},
) = ConcentricRings(listOf(outer, inner), modifier, size, stroke, gap, description, center)

/** Rings listed outer to inner. */
@Composable
fun ConcentricRings(
    rings: List<RingSpec>,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    stroke: Dp = 16.dp,
    gap: Dp = 6.dp,
    description: String? = null,
    center: @Composable () -> Unit = {},
) {
    // First show: each ring sweeps in from 0 over 650 ms, 60 ms after the one outside it. Later changes glide on a soft
    // spring. Reduced motion: the rings simply appear at their value.
    val reduced = LocalReducedMotion.current
    val progress = remember(rings.size) { List(rings.size) { i -> Animatable(if (reduced) rings[i].progress else 0f) } }
    val swept = remember(rings.size) { BooleanArray(rings.size) }
    rings.forEachIndexed { i, r ->
        LaunchedEffect(i, r.progress, rings.size) {
            val a = progress[i]
            when {
                reduced -> { swept[i] = true; a.snapTo(r.progress) }
                !swept[i] -> {
                    swept[i] = true
                    delay(60L * i)
                    a.animateTo(r.progress, tween(650, easing = ForgeMotion.Easing.EmphasizedDecelerate))
                }
                else -> a.animateTo(r.progress, spring(stiffness = Spring.StiffnessVeryLow))
            }
        }
    }
    Box(modifier.size(size).ringSemantics(description), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).clearAndSetSemantics {}) {
            val s = stroke.toPx()
            var radius = this.size.minDimension / 2f - s / 2f
            rings.forEachIndexed { i, r ->
                drawRing(radius, s, progress[i].value, r)
                radius -= s + gap.toPx()
            }
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

/** A number that glides to its new value (spring 0.8 / 400) instead of jumping; reduced motion jumps. */
@Composable
fun tweenedInt(value: Int): Int {
    val v by animateIntAsState(value, if (LocalReducedMotion.current) snap() else spring(0.8f, 400f), label = "number")
    return v
}

/** Single small ring, used for the week circles and rank badge. */
@Composable
fun MiniRing(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, stroke: Dp = 3.dp, description: String? = null, content: @Composable () -> Unit = {}) {
    val p by animateFloatAsState(progress, spring(stiffness = Spring.StiffnessLow), label = "mini")
    Box(modifier.size(size).ringSemantics(description), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).clearAndSetSemantics {}) {
            val s = stroke.toPx()
            val r = this.size.minDimension / 2f - s / 2f
            val tl = Offset(center.x - r, center.y - r)
            drawArc(track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(s))
            if (p > 0f) drawArc(color, -90f, 360f * p.coerceAtMost(1f), false, tl, Size(r * 2, r * 2), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}
