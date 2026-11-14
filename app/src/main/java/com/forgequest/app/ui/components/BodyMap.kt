package com.forgequest.app.ui.components

import android.content.Context
import android.graphics.Region
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.MuscleRecovery
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.FitColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// Anatomy art: react-native-body-highlighter 3.2.0, MIT License (c) 2022 ELABBASSI Hicham.

@Serializable
private data class BodyJson(val male: GenderJson, val female: GenderJson)

@Serializable
private data class GenderJson(val front: ViewJson, val back: ViewJson)

@Serializable
private data class ViewJson(val viewBox: List<Float>, val outline: String? = null, val parts: List<PartJson>)

@Serializable
private data class PartJson(val slug: String, val side: String, val d: String)

class BodyPart(val muscle: Muscle?, val path: Path, val region: Region)

class BodyView(val minX: Float, val minY: Float, val width: Float, val height: Float, val outline: Path?, val parts: List<BodyPart>)

class BodyArt(val front: BodyView, val back: BodyView)

/** Parses the anatomy once per body type; paths are reused across recompositions. */
object BodyArtLoader {
    private val cache = mutableMapOf<BodyType, BodyArt>()

    suspend fun load(context: Context, type: BodyType): BodyArt = cache[type] ?: withContext(Dispatchers.Default) {
        val text = context.assets.open("body_map.json").bufferedReader().use { it.readText() }
        val body = Json { ignoreUnknownKeys = true }.decodeFromString<BodyJson>(text)
        val gender = if (type == BodyType.FEMALE) body.female else body.male
        BodyArt(gender.front.toView(), gender.back.toView()).also { cache[type] = it }
    }

    private fun ViewJson.toView(): BodyView {
        val parts = parts.map { p ->
            val path = PathParser().parsePathString(p.d).toPath()
            val android = path.asAndroidPath()
            val bounds = android.computeBoundsCompat()
            val region = Region().apply { setPath(android, Region(bounds)) }
            BodyPart(Muscle.fromSlug(p.slug), path, region)
        }
        val outlinePath = outline?.let { PathParser().parsePathString(it).toPath() }
        return BodyView(viewBox[0], viewBox[1], viewBox[2], viewBox[3], outlinePath, parts)
    }

    private fun android.graphics.Path.computeBoundsCompat(): android.graphics.Rect {
        val rf = android.graphics.RectF()
        @Suppress("DEPRECATION") computeBounds(rf, true)
        return android.graphics.Rect(rf.left.toInt() - 1, rf.top.toInt() - 1, rf.right.toInt() + 1, rf.bottom.toInt() + 1)
    }
}

/**
 * Coral (just trained) → amber (recovering) → teal (almost ready) → resting grey.
 * Uses the legend's exact colors; the map animates between them as muscles recover.
 */
fun recoveryColor(fatigue: Float, c: FitColors): Color = when {
    fatigue <= 0f -> c.muscleRest
    fatigue < 0.33f -> lerp(c.muscleRest, c.teal, 0.55f + 0.45f * (fatigue / 0.33f))
    fatigue < 0.66f -> c.gold
    else -> c.coral
}

@Composable
fun recoverySummaryDescription(recovery: Map<Muscle, MuscleRecovery>): String {
    val base = if (recovery.isEmpty()) stringResource(R.string.map_cd_all_fresh)
    else stringResource(
        R.string.map_cd_recovering,
        recovery.entries.sortedByDescending { it.value.fatigue }
            .map { stringResource(R.string.map_cd_muscle_ready, it.key.localLabel(), it.value.hoursUntilReady) }
            .joinToString(stringResource(R.string.list_separator)),
    )
    return "$base ${stringResource(R.string.map_tap_hint)}"
}

/**
 * Front and back figures side by side, centred (front on the left and back on the right in every language),
 * with the four-dot recovery legend on one line underneath. Tapping a muscle highlights it (with a small buzz) and swaps
 * the legend for a pill that opens its exercises; tapping it again, or empty space, clears the selection.
 */
@Composable
fun MuscleRecoveryMap(
    recovery: Map<Muscle, MuscleRecovery>,
    bodyType: BodyType,
    modifier: Modifier = Modifier,
    fallbackScroll: Boolean = false,
    onSeeExercises: ((Muscle) -> Unit)? = null,
    exerciseCount: (Muscle) -> Int = { 0 },
) {
    val context = LocalContext.current
    val art by produceState<BodyArt?>(null, bodyType) { value = BodyArtLoader.load(context, bodyType) }
    val summary = recoverySummaryDescription(recovery)
    var selected by rememberSaveable { mutableStateOf<Muscle?>(null) }
    val haptic = LocalHapticFeedback.current
    val onTap: ((Muscle?) -> Unit)? = onSeeExercises?.let {
        { m: Muscle? ->
            val next = if (m == selected) null else m
            if (next != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            selected = next
        }
    }
    // TalkBack can't pick a muscle by touch, so each muscle is an action on the map that opens its exercises directly.
    val muscleActions = if (onSeeExercises == null) emptyList() else Muscle.entries.map { m ->
        CustomAccessibilityAction(m.localLabel()) { onSeeExercises(m); true }
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val rowMod = if (fallbackScroll) {
            Modifier.fillMaxWidth().height(220.dp)
        } else {
            Modifier.weight(1f).fillMaxWidth()
        }
        // Front on the left and back on the right in every language: it's a picture of a body, not a reading order.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(
                rowMod.clearAndSetSemantics {
                    contentDescription = summary
                    if (muscleActions.isNotEmpty()) customActions = muscleActions
                },
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(art?.front, art?.back).forEach { view ->
                    // Equal shares of the row: with a height-driven aspect ratio the first figure took its full width and the
                    // second was squeezed into what was left, so the back came out smaller than the front.
                    Box(Modifier.weight(1f, fill = false).widthIn(max = 150.dp).fillMaxHeight().heightIn(max = 300.dp)) {
                        if (view != null) {
                            BodyFigure(view, recovery, selected = selected, onTap = onTap)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // One slot for the legend or the pill, a touch target tall, so the map does not jump when a muscle is picked.
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
            val pick = selected
            if (pick != null && onSeeExercises != null) {
                SeeExercisesPill(pick, remember(pick, exerciseCount) { exerciseCount(pick) }) { onSeeExercises(pick) }
            } else {
                MapLegend()
            }
        }
    }
}

/** The app's quiet secondary button (the map's card already has Start as its main teal action). */
@Composable
private fun SeeExercisesPill(muscle: Muscle, count: Int, onClick: () -> Unit) {
    TonalPill(
        stringResource(R.string.muscle_see_exercises, muscle.localLabel(), count),
        onClick = onClick,
        icon = Icons.Rounded.FitnessCenter,
    )
}

/** Wraps onto a second line at large text sizes instead of running off the card. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MapLegend() {
    val c = Fit.colors
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendDot(c.coral, stringResource(R.string.map_just_trained), dot = 8.dp, gap = 4.dp)
        LegendDot(c.gold, stringResource(R.string.map_recovering), dot = 8.dp, gap = 4.dp)
        LegendDot(c.teal, stringResource(R.string.map_almost_ready), dot = 8.dp, gap = 4.dp)
        LegendDot(c.muscleRest, stringResource(R.string.map_fresh), dot = 8.dp, gap = 4.dp)
    }
}

private const val PICK_TINT = 0.3f

@Composable
private fun BodyFigure(view: BodyView, recovery: Map<Muscle, MuscleRecovery>, selected: Muscle?, onTap: ((Muscle?) -> Unit)?) {
    val c = Fit.colors
    // Animated colors per muscle so the map "cools down" smoothly when data changes, and the picked muscle
    // fades into a solid accent fill instead of getting an outline.
    val colors = Muscle.entries.associateWith { m ->
        val own = recoveryColor(recovery[m]?.fatigue ?: 0f, c)
        // A tint, not a takeover: the picked muscle keeps the map's soft look and just leans teal.
        val target = if (m == selected) androidx.compose.ui.graphics.lerp(c.tealTrack, c.teal, PICK_TINT) else own
        animateColorAsState(target, label = m.slug).value
    }
    val bodyNeutral = if (c.isDark) Color(0xFF2A2C2F) else Color(0xFFE6EAF0)

    val canvasMod = Modifier.fillMaxSize().let { base ->
        if (onTap == null) base else base.pointerInput(view) {
            detectTapGestures { tap ->
                val scale = minOf(size.width / view.width, size.height / view.height)
                val dx = (size.width - view.width * scale) / 2f
                val dy = (size.height - view.height * scale) / 2f
                val x = ((tap.x - dx) / scale + view.minX).roundToInt()
                val y = ((tap.y - dy) / scale + view.minY).roundToInt()
                // Exact hit first, then the nearest muscle within a finger's width.
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
