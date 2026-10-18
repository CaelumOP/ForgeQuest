package com.forgequest.app.ui.components

import android.content.Context
import android.graphics.Region
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.MuscleRecovery
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

class BodyPart(val slug: String, val muscle: Muscle?, val path: Path, val region: Region)

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
            BodyPart(p.slug, Muscle.fromSlug(p.slug), path, region)
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
fun MuscleRecoveryMap(
    recovery: Map<Muscle, MuscleRecovery>,
    bodyType: BodyType,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val art by produceState<BodyArt?>(null, bodyType) { value = BodyArtLoader.load(context, bodyType) }
    var selected by remember { mutableStateOf<Muscle?>(null) }
    val c = Fit.colors

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Front" to art?.front, "Back" to art?.back).forEach { (label, view) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.5f)) {
                        if (view != null) {
                            BodyFigure(view, recovery, selected, onTap = { selected = if (selected == it) null else it })
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val info = selected?.let { m ->
            val r = recovery[m]
            if (r == null) "${m.label} · fully recovered"
            else "${m.label} · ${((1f - r.fatigue) * 100).roundToInt()}% recovered · ready in ${r.hoursUntilReady} h"
        } ?: if (recovery.isEmpty()) "All muscles fresh. Tap a muscle for details." else "Tap a muscle for details"
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.cardHigh).padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(info, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, textAlign = TextAlign.Center, maxLines = 2)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendDot(c.coral, "Just trained")
            LegendDot(c.gold, "Recovering")
            LegendDot(c.teal, "Almost ready")
            LegendDot(c.muscleRest, "Fresh")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fit.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun BodyFigure(view: BodyView, recovery: Map<Muscle, MuscleRecovery>, selected: Muscle?, onTap: (Muscle) -> Unit) {
    val c = Fit.colors
    // Animated colors per muscle so the map "cools down" smoothly when data changes.
    val colors = Muscle.entries.associateWith { m ->
        animateColorAsState(recoveryColor(recovery[m]?.fatigue ?: 0f, c), label = m.slug).value
    }
    val bodyNeutral = if (c.isDark) Color(0xFF2A2C2F) else Color(0xFFE6EAF0)

    Canvas(
        Modifier.fillMaxWidth().aspectRatio(0.5f).pointerInput(view) {
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
                hit?.muscle?.let(onTap)
            }
        }
    ) {
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
            if (selected != null) {
                for (part in view.parts) if (part.muscle == selected) {
                    drawPath(part.path, c.textPrimary, style = Stroke(width = 2f / scale))
                }
            }
        }
    }
}
