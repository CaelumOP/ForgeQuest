package com.forgequest.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Muscle
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeSpace
import kotlin.math.roundToInt

/**
 * Compact front/back muscle map for ExerciseDetailScreen:
 * primary muscles in data blue, secondary muscles in lighter data blue (no teal).
 */
@Composable
fun MuscleMapMini(
    primary: List<Muscle>,
    secondary: List<Muscle>,
    bodyType: BodyType = BodyType.MALE,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val art by produceState<BodyArt?>(initialValue = null, bodyType) {
        value = BodyArtLoader.load(context, bodyType)
    }
    val primarySet = primary.toSet()
    val secondarySet = secondary.toSet() - primarySet
    val mainColor = c.blue
    val secondaryColor = c.blue.copy(alpha = 0.36f)
    val restColor = c.cardHigh
    val outlineColor = c.divider

    val primNames = primary.map { it.localLabel() }
    val secNames = secondary.map { it.localLabel() }
    val desc = buildString {
        if (primNames.isNotEmpty()) append(primNames.joinToString())
        if (secNames.isNotEmpty()) {
            if (isNotEmpty()) append("; ")
            append(secNames.joinToString())
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { if (desc.isNotBlank()) contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = art ?: return@Box
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.widthIn(max = 320.dp).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                MiniFigure(loaded.front, primarySet, secondarySet, mainColor, secondaryColor, restColor, outlineColor, Modifier.weight(1f).fillMaxHeight())
                MiniFigure(loaded.back, primarySet, secondarySet, mainColor, secondaryColor, restColor, outlineColor, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MiniFigure(
    view: BodyView,
    primary: Set<Muscle>,
    secondary: Set<Muscle>,
    mainColor: Color,
    secondaryColor: Color,
    restColor: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.aspectRatio(view.width / view.height, matchHeightConstraintsFirst = true)) {
        val scale = minOf(size.width / view.width, size.height / view.height)
        val dx = (size.width - view.width * scale) / 2f - view.minX * scale
        val dy = (size.height - view.height * scale) / 2f - view.minY * scale
        withTransform({ translate(dx, dy); scale(scale, scale, pivot = Offset.Zero) }) {
            view.outline?.let { drawPath(it, restColor) }
            view.parts.forEach { part ->
                val fill = when (part.muscle) {
                    null -> restColor
                    in primary -> mainColor
                    in secondary -> secondaryColor
                    else -> restColor
                }
                drawPath(part.path, fill)
            }
            view.outline?.let { drawPath(it, outlineColor, style = Stroke(width = 1.dp.toPx() / scale)) }
        }
    }
}

/** Compact front/back body-map sheet for tap-filtering exercises by muscle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleFilterSheet(
    selected: Muscle?,
    bodyType: BodyType = BodyType.MALE,
    onSelect: (Muscle?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val art by produceState<BodyArt?>(initialValue = null, bodyType) {
        value = BodyArtLoader.load(context, bodyType)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ForgeSpace.Gutter)
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.picker_map_filter),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (selected != null) {
                    TonalPill(
                        stringResource(R.string.picker_clear_filter),
                        onClick = { onSelect(null); onDismiss() },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val loaded = art
            if (loaded != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.fillMaxWidth().height(260.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        FilterFigure(
                            view = loaded.front,
                            selected = selected,
                            onTap = { m ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(if (selected == m) null else m)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        FilterFigure(
                            view = loaded.back,
                            selected = selected,
                            onTap = { m ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(if (selected == m) null else m)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FilterFigure(
    view: BodyView,
    selected: Muscle?,
    onTap: (Muscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    Canvas(
        modifier
            .aspectRatio(view.width / view.height, matchHeightConstraintsFirst = true)
            .pointerInput(view) {
                detectTapGestures { pos ->
                    val scale = minOf(size.width / view.width, size.height / view.height)
                    val dx = (size.width - view.width * scale) / 2f - view.minX * scale
                    val dy = (size.height - view.height * scale) / 2f - view.minY * scale
                    val x = ((pos.x - dx) / scale).roundToInt()
                    val y = ((pos.y - dy) / scale).roundToInt()
                    view.parts.lastOrNull { it.muscle != null && it.region.contains(x, y) }?.muscle?.let(onTap)
                }
            },
    ) {
        val scale = minOf(size.width / view.width, size.height / view.height)
        val dx = (size.width - view.width * scale) / 2f - view.minX * scale
        val dy = (size.height - view.height * scale) / 2f - view.minY * scale
        withTransform({ translate(dx, dy); scale(scale, scale, pivot = Offset.Zero) }) {
            view.outline?.let { drawPath(it, c.cardHigh) }
            view.parts.forEach { part ->
                val fill = when {
                    part.muscle == null -> c.cardHigh
                    part.muscle == selected -> c.blue
                    else -> c.cardHigh
                }
                drawPath(part.path, fill)
                if (part.muscle == selected) {
                    drawPath(part.path, c.textPrimary, style = Stroke(width = 1.5.dp.toPx() / scale))
                }
            }
            view.outline?.let { drawPath(it, c.divider, style = Stroke(width = 1.dp.toPx() / scale)) }
        }
    }
}
