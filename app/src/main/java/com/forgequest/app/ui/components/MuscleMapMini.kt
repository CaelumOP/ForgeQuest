package com.forgequest.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

internal fun isMusclePartSelected(partMuscle: Muscle?, selected: Muscle?): Boolean =
    selected != null && partMuscle != null && partMuscle == selected

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
    val tapHint = stringResource(R.string.v42_muscle_tap_hint)
    val frontLabel = stringResource(R.string.v42_muscle_front)
    val backLabel = stringResource(R.string.v42_muscle_back)
    val pickMuscle: (Muscle) -> Unit = { m ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(if (selected == m) null else m)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
            Spacer(Modifier.height(4.dp))
            Text(tapHint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (selected != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.v42_muscle_showing, selected.localLabel()),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.teal,
                )
            }
            Spacer(Modifier.height(12.dp))
            val loaded = art
            if (loaded != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            FilterFigure(
                                view = loaded.front,
                                selected = selected,
                                description = "$frontLabel. $tapHint",
                                onTap = pickMuscle,
                                modifier = Modifier.fillMaxWidth().height(240.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(frontLabel, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            FilterFigure(
                                view = loaded.back,
                                selected = selected,
                                description = "$backLabel. $tapHint",
                                onTap = pickMuscle,
                                modifier = Modifier.fillMaxWidth().height(240.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(backLabel, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Muscle.entries.forEach { m ->
                        ChoiceChip(
                            text = m.localLabel(),
                            selected = m == selected,
                            onClick = { pickMuscle(m) },
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
    description: String,
    onTap: (Muscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val silhouetteFill = c.cardHigh
    val muscleFill = c.textSecondary.copy(alpha = 0.28f)
    Canvas(
        modifier
            .aspectRatio(view.width / view.height, matchHeightConstraintsFirst = true)
            .semantics { contentDescription = description }
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
            view.outline?.let { drawPath(it, silhouetteFill) }
            view.parts.forEach { part ->
                val muscle = part.muscle
                val isSelected = isMusclePartSelected(muscle, selected)
                val fill = when {
                    muscle == null -> silhouetteFill
                    isSelected -> c.teal
                    else -> muscleFill
                }
                drawPath(part.path, fill)
                if (muscle != null) {
                    drawPath(
                        part.path,
                        color = if (isSelected) c.textPrimary else c.divider,
                        style = Stroke(width = (if (isSelected) 1.5f else 1f).dp.toPx() / scale),
                    )
                }
            }
            view.outline?.let { drawPath(it, c.divider, style = Stroke(width = 1.dp.toPx() / scale)) }
        }
    }
}
