package com.forgequest.app.ui.progress

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Muscle
import com.forgequest.app.ui.components.BodyArt
import com.forgequest.app.ui.components.BodyArtLoader
import com.forgequest.app.ui.components.BodyView
import com.forgequest.app.ui.components.LegendDot
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.FitColors
import com.forgequest.app.ui.theme.ForgeShape
import kotlin.math.roundToInt

/** Soft Coach data-only palette for weekly muscle volume (no teal). */
fun volumeColor(sets: Float, c: FitColors): Color = when {
    sets <= 0f -> c.muscleRest
    sets < 6f -> c.purple
    sets < 16f -> c.blue
    else -> c.gold
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyVolumeBodyMap(
    volume: Map<Muscle, Float>,
    bodyType: BodyType,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val art by produceState<BodyArt?>(null, bodyType) { value = BodyArtLoader.load(context, bodyType) }
    var selected by rememberSaveable { mutableStateOf<Muscle?>(null) }
    val haptic = LocalHapticFeedback.current
    val mapDesc = stringResource(R.string.prog41_vol_cd)
    val muscleActions = Muscle.entries.map { m ->
        val label = "${m.localLabel()} ${ProgressMetrics.formatSets(volume[m] ?: 0f)}"
        CustomAccessibilityAction(label) { selected = m; true }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clearAndSetSemantics {
                        contentDescription = mapDesc
                        customActions = muscleActions
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(art?.front, art?.back).forEach { view ->
                    Box(Modifier.weight(1f, fill = false).widthIn(max = 150.dp).fillMaxHeight()) {
                        if (view != null) {
                            VolumeFigure(
                                view = view,
                                volume = volume,
                                selected = selected,
                                onTap = { m ->
                                    val next = if (m == selected) null else m
                                    if (next != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selected = next
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val pick = selected
        if (pick != null) {
            val count = ProgressMetrics.formatSets(volume[pick] ?: 0f)
            Box(
                Modifier
                    .clip(ForgeShape.Full)
                    .background(c.blueTrack)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.prog41_vol_selected, pick.localLabel(), count),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = c.blue,
                )
            }
        } else {
            Text(
                stringResource(R.string.prog41_vol_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LegendDot(c.muscleRest, stringResource(R.string.prog41_vol_legend_0), dot = 8.dp, gap = 4.dp)
            LegendDot(c.purple, stringResource(R.string.prog41_vol_legend_low), dot = 8.dp, gap = 4.dp)
            LegendDot(c.blue, stringResource(R.string.prog41_vol_legend_mid), dot = 8.dp, gap = 4.dp)
            LegendDot(c.gold, stringResource(R.string.prog41_vol_legend_high), dot = 8.dp, gap = 4.dp)
        }
    }
}

@Composable
private fun VolumeFigure(
    view: BodyView,
    volume: Map<Muscle, Float>,
    selected: Muscle?,
    onTap: (Muscle?) -> Unit,
) {
    val c = Fit.colors
    val colors = Muscle.entries.associateWith { m ->
        val own = volumeColor(volume[m] ?: 0f, c)
        val target = if (m == selected) androidx.compose.ui.graphics.lerp(own, c.textPrimary, 0.25f) else own
        animateColorAsState(target, label = "vol_${m.slug}").value
    }
    val bodyNeutral = if (c.isDark) Color(0xFF2A2C2F) else Color(0xFFE6EAF0)

    val canvasMod = Modifier.fillMaxSize().pointerInput(view) {
        detectTapGestures { tap ->
            val scale = minOf(size.width / view.width, size.height / view.height)
            val dx = (size.width - view.width * scale) / 2f
            val dy = (size.height - view.height * scale) / 2f
            val x = ((tap.x - dx) / scale + view.minX).roundToInt()
            val y = ((tap.y - dy) / scale + view.minY).roundToInt()
            val hit = view.parts.firstOrNull { it.muscle != null && it.region.contains(x, y) }
                ?: view.parts.filter { it.muscle != null }.minByOrNull { part ->
                    val b = part.region.bounds
                    val cx = (b.left + b.right) / 2f
                    val cy = (b.top + b.bottom) / 2f
                    (cx - x) * (cx - x) + (cy - y) * (cy - y)
                }?.takeIf { part ->
                    val b = part.region.bounds
                    val pad = 24
                    x in (b.left - pad)..(b.right + pad) && y in (b.top - pad)..(b.bottom + pad)
                }
            onTap(hit?.muscle)
        }
    }

    Canvas(canvasMod) {
        val scale = minOf(size.width / view.width, size.height / view.height)
        val dx = (size.width - view.width * scale) / 2f
        val dy = (size.height - view.height * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale, pivot = Offset.Zero)
            translate(-view.minX, -view.minY)
        }) {
            view.outline?.let { drawPath(it, bodyNeutral) }
            for (part in view.parts) {
                val color = part.muscle?.let { colors[it] } ?: bodyNeutral
                drawPath(part.path, color)
            }
            view.outline?.let { drawPath(it, c.bodyOutline, style = Stroke(width = 1.2f / scale)) }
        }
    }
}
