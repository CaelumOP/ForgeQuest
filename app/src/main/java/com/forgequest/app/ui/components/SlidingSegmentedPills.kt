package com.forgequest.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.LocalReducedMotion

private const val SLIDE_DURATION_MS = 220

/** Normalized horizontal start offset (0f..1f of track width) for the sliding pill at [position]. */
internal fun slidingPillOffsetFraction(position: Float, count: Int, isRtl: Boolean): Float {
    if (count <= 0) return 0f
    val clamped = position.coerceIn(0f, (count - 1).toFloat())
    val visual = if (isRtl) (count - 1) - clamped else clamped
    return visual / count.toFloat()
}

/**
 * Equal-width segmented pill selector with a single selection indicator that slides smoothly between options.
 * Mirrors in RTL, jumps immediately when reduced motion is enabled, enforces >= 48 dp targets, and exposes radio-group semantics.
 */
@Composable
fun SlidingSegmentedPills(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    disabled: Set<Int> = emptySet(),
    optionDescriptions: List<String> = emptyList(),
) {
    if (options.isEmpty()) return
    val c = Fit.colors
    val reduced = LocalReducedMotion.current
    val hasValid = selected in options.indices
    val pos = remember { Animatable(if (hasValid) selected.toFloat() else 0f) }
    var hadSelection by remember { mutableStateOf(hasValid) }

    LaunchedEffect(selected, options.size, reduced) {
        if (selected !in options.indices) {
            hadSelection = false
            return@LaunchedEffect
        }
        val target = selected.toFloat().coerceIn(0f, (options.size - 1).toFloat())
        if (!hadSelection || reduced) {
            pos.snapTo(target)
        } else {
            pos.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = SLIDE_DURATION_MS, easing = ForgeMotion.Easing.Standard),
            )
        }
        hadSelection = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ForgeShape.Full)
            .background(c.cardHigh)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup()
                .drawBehind {
                    if (!hasValid) return@drawBehind
                    val drawIndex = if (!hadSelection) selected.toFloat() else pos.value
                    val frac = slidingPillOffsetFraction(
                        position = drawIndex,
                        count = options.size,
                        isRtl = layoutDirection == LayoutDirection.Rtl,
                    )
                    val slotW = size.width / options.size.toFloat()
                    val r = size.height / 2f
                    drawRoundRect(
                        color = c.teal,
                        topLeft = Offset(x = frac * size.width, y = 0f),
                        size = Size(width = slotW, height = size.height),
                        cornerRadius = CornerRadius(r, r),
                    )
                },
        ) {
            options.forEachIndexed { i, label ->
                val isSel = i == selected
                val off = i in disabled
                val textColor by animateColorAsState(
                    targetValue = if (isSel) c.onTeal else c.textPrimary,
                    animationSpec = if (reduced || !hadSelection) snap() else tween(SLIDE_DURATION_MS, easing = ForgeMotion.Easing.Standard),
                    label = "pillText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .clip(ForgeShape.Full)
                        .selectable(
                            selected = isSel,
                            enabled = !off,
                            role = Role.RadioButton,
                            onClick = { onSelect(i) },
                        )
                        .semantics {
                            optionDescriptions.getOrNull(i)?.let { contentDescription = it }
                        }
                        .alpha(if (off) 0.4f else 1f)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        maxLines = 2,
                        softWrap = true,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
