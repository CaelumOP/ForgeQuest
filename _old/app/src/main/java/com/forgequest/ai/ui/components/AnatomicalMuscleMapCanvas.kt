package com.forgequest.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.MuscleHotCoral
import com.forgequest.ai.ui.theme.MuscleReadyTeal
import com.forgequest.ai.ui.theme.MuscleRestingBase
import com.forgequest.ai.ui.theme.MuscleSeparationStroke
import com.forgequest.ai.ui.theme.MuscleWarmAmber
import com.forgequest.ai.ui.theme.SilhouetteNeutral
import com.forgequest.ai.ui.theme.SurfaceContainerHigh
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary
import kotlin.math.hypot
import kotlin.math.roundToInt

sealed interface BezierCommand {
    data class MoveTo(val x: Float, val y: Float) : BezierCommand
    data class LineTo(val x: Float, val y: Float) : BezierCommand
    data class QuadTo(val cx: Float, val cy: Float, val ex: Float, val ey: Float) : BezierCommand
    data class CubicTo(
        val c1x: Float,
        val c1y: Float,
        val c2x: Float,
        val c2y: Float,
        val ex: Float,
        val ey: Float
    ) : BezierCommand
    data object Close : BezierCommand
}

/**
 * Pure Bezier polygon definition for a single anatomical structure or muscle head.
 * [hitBoxCenter] uses normalized [0f..1f] coordinates within its respective Front/Back canvas.
 */
data class MuscleRegionPath(
    val group: MuscleGroup?,
    val label: String,
    val isFront: Boolean,
    val buildPath: (width: Float, height: Float) -> Path,
    val hitBoxCenter: Offset,
    val traceCommands: (width: Float, height: Float) -> List<BezierCommand> = { _, _ -> emptyList() }
)

/**
 * Smoothly interpolates recovery heat color across the 48-hour recovery window:
 * - 1.0f -> #F28B82 (MuscleHotCoral, 0-16h "Just trained")
 * - 0.5f -> #FDD663 (MuscleWarmAmber, 16-36h "Recovering")
 * - 0.15f -> #5CD6C0 (MuscleReadyTeal, 36-48h "Almost ready")
 * - 0.0f -> #282A2C (MuscleRestingBase, 48h+ fully recovered)
 */
fun muscleRecoveryColor(intensity: Float): Color {
    val clamped = intensity.coerceIn(0f, 1f)
    return when {
        clamped <= 0.001f -> MuscleRestingBase
        clamped <= 0.15f -> lerp(MuscleRestingBase, MuscleReadyTeal, clamped / 0.15f)
        clamped <= 0.50f -> lerp(MuscleReadyTeal, MuscleWarmAmber, (clamped - 0.15f) / 0.35f)
        else -> lerp(MuscleWarmAmber, MuscleHotCoral, (clamped - 0.50f) / 0.50f)
    }
}

private fun buildSymmetricBezierPath(
    width: Float,
    height: Float,
    mirror: Boolean,
    builder: SymmetricPathScope.() -> Unit
): Path {
    val path = Path()
    val scope = SymmetricPathScope(path, null, width, height, mirror)
    scope.builder()
    path.close()
    return path
}

private fun traceSymmetricBezierCommands(
    width: Float,
    height: Float,
    mirror: Boolean,
    builder: SymmetricPathScope.() -> Unit
): List<BezierCommand> {
    val commands = mutableListOf<BezierCommand>()
    val scope = SymmetricPathScope(null, commands, width, height, mirror)
    scope.builder()
    commands += BezierCommand.Close
    return commands
}

private fun createRegionPath(
    group: MuscleGroup?,
    label: String,
    isFront: Boolean,
    hitBoxCenter: Offset,
    mirror: Boolean = false,
    builder: SymmetricPathScope.() -> Unit
): MuscleRegionPath = MuscleRegionPath(
    group = group,
    label = label,
    isFront = isFront,
    hitBoxCenter = hitBoxCenter,
    buildPath = { w, h -> buildSymmetricBezierPath(w, h, mirror, builder) },
    traceCommands = { w, h -> traceSymmetricBezierCommands(w, h, mirror, builder) }
)

private class SymmetricPathScope(
    private val path: Path?,
    private val commands: MutableList<BezierCommand>?,
    private val w: Float,
    private val h: Float,
    private val mirror: Boolean
) {
    private fun tx(nx: Float): Float = (if (mirror) (1f - nx) else nx) * w
    private fun ty(ny: Float): Float = ny * h

    fun moveTo(nx: Float, ny: Float) {
        val x = tx(nx)
        val y = ty(ny)
        path?.moveTo(x, y)
        commands?.add(BezierCommand.MoveTo(x, y))
    }

    fun lineTo(nx: Float, ny: Float) {
        val x = tx(nx)
        val y = ty(ny)
        path?.lineTo(x, y)
        commands?.add(BezierCommand.LineTo(x, y))
    }

    fun quadraticTo(cx: Float, cy: Float, ex: Float, ey: Float) {
        val qcx = tx(cx)
        val qcy = ty(cy)
        val qex = tx(ex)
        val qey = ty(ey)
        path?.quadraticTo(qcx, qcy, qex, qey)
        commands?.add(BezierCommand.QuadTo(qcx, qcy, qex, qey))
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, ex: Float, ey: Float) {
        val cc1x = tx(c1x)
        val cc1y = ty(c1y)
        val cc2x = tx(c2x)
        val cc2y = ty(c2y)
        val cex = tx(ex)
        val cey = ty(ey)
        path?.cubicTo(cc1x, cc1y, cc2x, cc2y, cex, cey)
        commands?.add(BezierCommand.CubicTo(cc1x, cc1y, cc2x, cc2y, cex, cey))
    }

    fun close() {
        path?.close()
        commands?.add(BezierCommand.Close)
    }
}

/**
 * Continuous athletic silhouette matching the user's reference image:
 * Broad capped shoulders (x=0.21..0.79), 3D muscular arms (x=0.18..0.30),
 * athletic V-taper waist (x=0.34..0.66), muscular thighs with clean 4% central gap,
 * sculpted calves, and grounded athletic sneakers with crisp soles.
 */
private fun SymmetricPathScope.buildContinuousBodyHalf(isLeftHalf: Boolean) {
    if (isLeftHalf) {
        moveTo(0.500f, 0.025f)
        // Cranium & temple
        cubicTo(0.450f, 0.025f, 0.435f, 0.048f, 0.438f, 0.082f)
        // Jawline & neck
        cubicTo(0.440f, 0.104f, 0.452f, 0.124f, 0.458f, 0.138f)
        // Broad upper trap slope to acromion
        cubicTo(0.400f, 0.146f, 0.300f, 0.155f, 0.220f, 0.165f)
        // Rounded lateral deltoid cap (broad athletic shoulder)
        cubicTo(0.155f, 0.180f, 0.120f, 0.210f, 0.135f, 0.252f)
        // Outer upper arm (bicep/tricep sweep - sleek athletic fullness)
        cubicTo(0.130f, 0.285f, 0.132f, 0.320f, 0.145f, 0.355f)
        // Brachioradialis flare & forearm taper to wrist (sleek anatomical curve)
        cubicTo(0.128f, 0.395f, 0.140f, 0.445f, 0.155f, 0.485f)
        // Hand & fingers (hanging naturally along outer thigh)
        cubicTo(0.150f, 0.515f, 0.155f, 0.540f, 0.165f, 0.555f)
        cubicTo(0.178f, 0.555f, 0.185f, 0.535f, 0.190f, 0.510f)
        // Inner wrist & ascending inner forearm
        lineTo(0.190f, 0.485f)
        cubicTo(0.200f, 0.475f, 0.235f, 0.410f, 0.245f, 0.355f)
        // Inner upper arm ascending to axilla
        cubicTo(0.248f, 0.320f, 0.250f, 0.285f, 0.250f, 0.252f)
        // Lateral torso: Latissimus dorsi sweep down to waist (sharp V-taper)
        cubicTo(0.290f, 0.310f, 0.325f, 0.365f, 0.345f, 0.420f)
        // Hip & outer glute / quad sweep
        cubicTo(0.335f, 0.455f, 0.328f, 0.505f, 0.332f, 0.540f)
        cubicTo(0.328f, 0.590f, 0.335f, 0.640f, 0.352f, 0.675f)
        // Lateral knee
        cubicTo(0.355f, 0.695f, 0.350f, 0.710f, 0.342f, 0.730f)
        // Lateral calf (Gastrocnemius bulge) down to ankle
        cubicTo(0.328f, 0.765f, 0.335f, 0.835f, 0.372f, 0.915f)
        // Sneaker heel, athletic sole, and toe
        cubicTo(0.352f, 0.925f, 0.348f, 0.955f, 0.350f, 0.975f)
        lineTo(0.442f, 0.975f)
        cubicTo(0.446f, 0.955f, 0.440f, 0.930f, 0.425f, 0.915f)
        // Medial calf (Medial gastrocnemius curve) ascending to knee
        cubicTo(0.462f, 0.850f, 0.468f, 0.790f, 0.450f, 0.730f)
        cubicTo(0.445f, 0.710f, 0.450f, 0.695f, 0.455f, 0.675f)
        // Inner thigh ascending to groin (clean athletic leg separation)
        cubicTo(0.470f, 0.635f, 0.478f, 0.585f, 0.480f, 0.540f)
        quadraticTo(0.490f, 0.535f, 0.500f, 0.535f)
    }
}

/**
 * Complete collection of Anterior (Front) and Posterior (Back) anatomical Bezier paths.
 * Every muscle head is sculpted with smooth, gapless Bezier curves matching the reference image.
 */
val ANATOMICAL_MUSCLE_REGION_PATHS: List<MuscleRegionPath> by lazy {
    buildAnatomicalMuscleRegionPaths()
}

fun buildAnatomicalMuscleRegionPaths(): List<MuscleRegionPath> {
    val list = mutableListOf<MuscleRegionPath>()

    // =========================================================================
    // ANTERIOR (FRONT) VIEW
    // =========================================================================

    // 0. Continuous Athletic Silhouette (Left & Right Halves)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        list += createRegionPath(
            group = null,
            label = "$side Body Silhouette (Anterior)",
            isFront = true,
            hitBoxCenter = Offset(if (mirror) 0.60f else 0.40f, 0.50f),
            mirror = mirror
        ) {
            buildContinuousBodyHalf(isLeftHalf = true)
            lineTo(0.500f, 0.025f)
            close()
        }
    }

    // 1. Head, Face & Neck (Anterior)
    list += createRegionPath(
        group = null,
        label = "Head & Neck (Anterior)",
        isFront = true,
        hitBoxCenter = Offset(0.50f, 0.08f),
        mirror = false
    ) {
        moveTo(0.500f, 0.025f)
        cubicTo(0.550f, 0.025f, 0.562f, 0.048f, 0.560f, 0.082f)
        cubicTo(0.558f, 0.104f, 0.546f, 0.124f, 0.540f, 0.138f)
        cubicTo(0.542f, 0.148f, 0.550f, 0.160f, 0.552f, 0.170f)
        quadraticTo(0.525f, 0.174f, 0.500f, 0.174f)
        quadraticTo(0.475f, 0.174f, 0.448f, 0.170f)
        cubicTo(0.450f, 0.160f, 0.458f, 0.148f, 0.460f, 0.138f)
        cubicTo(0.454f, 0.124f, 0.442f, 0.104f, 0.440f, 0.082f)
        cubicTo(0.438f, 0.048f, 0.450f, 0.025f, 0.500f, 0.025f)
        close()
    }

    // 1B. Anterior Upper Trapezius Collar Slope (Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.635f else 0.365f
        list += createRegionPath(
            group = MuscleGroup.UPPER_BACK_TRAPS,
            label = "$side Upper Trapezius Collar",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.155f),
            mirror = mirror
        ) {
            moveTo(0.460f, 0.140f)
            cubicTo(0.400f, 0.146f, 0.300f, 0.155f, 0.220f, 0.165f)
            cubicTo(0.280f, 0.172f, 0.380f, 0.174f, 0.448f, 0.170f)
            cubicTo(0.455f, 0.158f, 0.458f, 0.148f, 0.460f, 0.140f)
        }
    }

    // 2A. Clavicular Pectoralis Major (Upper Pec Shelf - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.610f else 0.390f
        list += createRegionPath(
            group = MuscleGroup.CHEST,
            label = "$side Clavicular Pectoralis (Upper Chest)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.198f),
            mirror = mirror
        ) {
            moveTo(0.495f, 0.176f)
            cubicTo(0.435f, 0.174f, 0.320f, 0.174f, 0.240f, 0.182f)
            cubicTo(0.235f, 0.208f, 0.245f, 0.228f, 0.270f, 0.242f)
            cubicTo(0.340f, 0.240f, 0.435f, 0.235f, 0.495f, 0.225f)
            cubicTo(0.496f, 0.208f, 0.496f, 0.192f, 0.495f, 0.176f)
        }
    }

    // 2B. Sternal / Costal Pectoralis Major (Main Pec Plate - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.610f else 0.390f
        list += createRegionPath(
            group = MuscleGroup.CHEST,
            label = "$side Sternal Pectoralis Major",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.255f),
            mirror = mirror
        ) {
            moveTo(0.495f, 0.227f)
            cubicTo(0.435f, 0.237f, 0.340f, 0.240f, 0.270f, 0.244f)
            cubicTo(0.265f, 0.265f, 0.285f, 0.285f, 0.325f, 0.294f)
            cubicTo(0.385f, 0.296f, 0.455f, 0.288f, 0.495f, 0.276f)
            cubicTo(0.496f, 0.260f, 0.496f, 0.244f, 0.495f, 0.227f)
        }
    }

    // 3A. Anterior Deltoid (Front Shoulder Belly - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.785f else 0.215f
        list += createRegionPath(
            group = MuscleGroup.FRONT_DELTS,
            label = "$side Anterior Deltoid",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.218f),
            mirror = mirror
        ) {
            moveTo(0.235f, 0.170f)
            cubicTo(0.205f, 0.185f, 0.175f, 0.210f, 0.175f, 0.245f)
            cubicTo(0.178f, 0.262f, 0.182f, 0.268f, 0.185f, 0.270f)
            cubicTo(0.225f, 0.265f, 0.255f, 0.260f, 0.265f, 0.245f)
            cubicTo(0.262f, 0.220f, 0.250f, 0.195f, 0.235f, 0.170f)
        }
    }

    // 3B. Lateral Deltoid (Side Shoulder Cap - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.835f else 0.165f
        list += createRegionPath(
            group = MuscleGroup.SIDE_DELTS,
            label = "$side Lateral Deltoid",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.220f),
            mirror = mirror
        ) {
            moveTo(0.220f, 0.165f)
            cubicTo(0.155f, 0.180f, 0.120f, 0.210f, 0.135f, 0.252f)
            cubicTo(0.145f, 0.262f, 0.165f, 0.268f, 0.185f, 0.270f)
            cubicTo(0.175f, 0.245f, 0.185f, 0.200f, 0.220f, 0.165f)
        }
    }

    // 4A. Biceps Brachii Long Head (Outer Bicep - Left & Right - Flush with Deltoid & Elbow)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.840f else 0.160f
        list += createRegionPath(
            group = MuscleGroup.BICEPS,
            label = "$side Biceps Brachii (Outer Head)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.310f),
            mirror = mirror
        ) {
            moveTo(0.185f, 0.270f)
            lineTo(0.135f, 0.252f)
            cubicTo(0.130f, 0.285f, 0.132f, 0.320f, 0.145f, 0.355f)
            lineTo(0.190f, 0.358f)
            cubicTo(0.190f, 0.330f, 0.188f, 0.300f, 0.185f, 0.270f)
        }
    }

    // 4B. Biceps Brachii Short Head & Brachialis (Inner Bicep - Left & Right - Flush with Deltoid & Elbow)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.785f else 0.215f
        list += createRegionPath(
            group = MuscleGroup.BICEPS,
            label = "$side Biceps Brachii (Inner Head & Brachialis)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.310f),
            mirror = mirror
        ) {
            moveTo(0.185f, 0.270f)
            lineTo(0.250f, 0.252f)
            cubicTo(0.250f, 0.285f, 0.248f, 0.320f, 0.245f, 0.355f)
            lineTo(0.190f, 0.358f)
            cubicTo(0.190f, 0.330f, 0.188f, 0.300f, 0.185f, 0.270f)
        }
    }

    // 4C. Brachioradialis (Outer Forearm Head - Left & Right - Full Elbow Width)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.850f else 0.150f
        list += createRegionPath(
            group = MuscleGroup.FOREARMS,
            label = "$side Brachioradialis (Outer Forearm)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.420f),
            mirror = mirror
        ) {
            moveTo(0.190f, 0.358f)
            lineTo(0.145f, 0.355f)
            cubicTo(0.128f, 0.395f, 0.140f, 0.445f, 0.155f, 0.485f)
            lineTo(0.172f, 0.485f)
            cubicTo(0.175f, 0.440f, 0.182f, 0.400f, 0.190f, 0.358f)
        }
    }

    // 4D. Flexor Carpi & Pronator Teres (Inner Forearm Head - Left & Right - Tapered Wrist)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.795f else 0.205f
        list += createRegionPath(
            group = MuscleGroup.FOREARMS,
            label = "$side Forearm Flexors & Pronator",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.420f),
            mirror = mirror
        ) {
            moveTo(0.190f, 0.358f)
            lineTo(0.245f, 0.355f)
            cubicTo(0.235f, 0.410f, 0.200f, 0.460f, 0.190f, 0.485f)
            lineTo(0.172f, 0.485f)
            cubicTo(0.175f, 0.440f, 0.182f, 0.400f, 0.190f, 0.358f)
        }
    }

    // 5. Rectus Abdominis (6-Pack: Upper, Middle, Lower Segments)
    val abTiers = listOf(
        Triple("Upper", 0.285f, 0.330f),
        Triple("Middle", 0.335f, 0.380f),
        Triple("Lower", 0.385f, 0.430f)
    )
    abTiers.forEach { (tier, topY, botY) ->
        val midY = (topY + botY) * 0.5f
        listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
            val cx = if (mirror) 0.540f else 0.460f
            list += createRegionPath(
                group = MuscleGroup.ABS_CORE,
                label = "$side $tier Rectus Abdominis",
                isFront = true,
                hitBoxCenter = Offset(cx, midY),
                mirror = mirror
            ) {
                moveTo(0.495f, topY)
                cubicTo(0.458f, topY - 0.002f, 0.425f, topY + 0.004f, 0.420f, midY)
                cubicTo(0.425f, botY - 0.004f, 0.458f, botY + 0.002f, 0.495f, botY)
                cubicTo(0.496f, midY + 0.010f, 0.496f, midY - 0.010f, 0.495f, topY)
            }
        }
    }

    // 5B. Pyramidalis & Pelvic Core (Lower Taper into Groin)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.530f else 0.470f
        list += createRegionPath(
            group = MuscleGroup.ABS_CORE,
            label = "$side Pyramidalis & Pelvic Core",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.460f),
            mirror = mirror
        ) {
            moveTo(0.495f, 0.434f)
            cubicTo(0.462f, 0.434f, 0.430f, 0.440f, 0.425f, 0.460f)
            cubicTo(0.438f, 0.485f, 0.468f, 0.515f, 0.495f, 0.530f)
            cubicTo(0.496f, 0.498f, 0.496f, 0.466f, 0.495f, 0.434f)
        }
    }

    // 5C. Serratus Anterior (Ribcage - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.635f else 0.365f
        list += createRegionPath(
            group = MuscleGroup.OBLIQUES,
            label = "$side Serratus Anterior",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.315f),
            mirror = mirror
        ) {
            moveTo(0.415f, 0.288f)
            cubicTo(0.368f, 0.288f, 0.332f, 0.295f, 0.320f, 0.305f)
            cubicTo(0.325f, 0.325f, 0.340f, 0.345f, 0.355f, 0.358f)
            cubicTo(0.395f, 0.348f, 0.412f, 0.328f, 0.415f, 0.288f)
        }
    }

    // 5D. External Obliques (Waist V-Cut - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.625f else 0.375f
        list += createRegionPath(
            group = MuscleGroup.OBLIQUES,
            label = "$side External Obliques (Waist)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.405f),
            mirror = mirror
        ) {
            moveTo(0.415f, 0.348f)
            cubicTo(0.365f, 0.352f, 0.342f, 0.378f, 0.348f, 0.420f)
            cubicTo(0.352f, 0.455f, 0.395f, 0.495f, 0.420f, 0.515f)
            cubicTo(0.420f, 0.460f, 0.418f, 0.400f, 0.415f, 0.348f)
        }
    }

    // 6A. Vastus Lateralis (Outer Quad Sweep - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.655f else 0.345f
        list += createRegionPath(
            group = MuscleGroup.QUADS,
            label = "$side Vastus Lateralis (Outer Quad)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.605f),
            mirror = mirror
        ) {
            moveTo(0.355f, 0.485f)
            cubicTo(0.330f, 0.525f, 0.325f, 0.585f, 0.335f, 0.648f)
            cubicTo(0.340f, 0.672f, 0.355f, 0.685f, 0.372f, 0.680f)
            cubicTo(0.375f, 0.628f, 0.378f, 0.565f, 0.385f, 0.505f)
            quadraticTo(0.370f, 0.492f, 0.355f, 0.485f)
        }
    }

    // 6B. Rectus Femoris (Center Quad - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.605f else 0.395f
        list += createRegionPath(
            group = MuscleGroup.QUADS,
            label = "$side Rectus Femoris (Center Quad)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.595f),
            mirror = mirror
        ) {
            moveTo(0.390f, 0.495f)
            cubicTo(0.378f, 0.545f, 0.375f, 0.615f, 0.380f, 0.675f)
            cubicTo(0.392f, 0.688f, 0.412f, 0.688f, 0.422f, 0.675f)
            cubicTo(0.426f, 0.615f, 0.428f, 0.545f, 0.422f, 0.498f)
            quadraticTo(0.405f, 0.492f, 0.390f, 0.495f)
        }
    }

    // 6C. Vastus Medialis & Medial Quad (VMO Teardrop - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.555f else 0.445f
        list += createRegionPath(
            group = MuscleGroup.QUADS,
            label = "$side Vastus Medialis (VMO Teardrop)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.615f),
            mirror = mirror
        ) {
            moveTo(0.422f, 0.498f)
            cubicTo(0.426f, 0.555f, 0.426f, 0.620f, 0.422f, 0.675f)
            cubicTo(0.435f, 0.690f, 0.465f, 0.680f, 0.472f, 0.640f)
            cubicTo(0.478f, 0.595f, 0.478f, 0.550f, 0.475f, 0.525f)
            quadraticTo(0.445f, 0.505f, 0.422f, 0.498f)
        }
    }

    // 6E. Knee Joint / Patella & Patellar Tendon (Front)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.600f else 0.400f
        list += createRegionPath(
            group = null,
            label = "$side Patella & Knee Joint",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.705f),
            mirror = mirror
        ) {
            moveTo(0.380f, 0.680f)
            cubicTo(0.370f, 0.695f, 0.372f, 0.718f, 0.385f, 0.730f)
            cubicTo(0.415f, 0.730f, 0.425f, 0.718f, 0.422f, 0.680f)
            quadraticTo(0.400f, 0.675f, 0.380f, 0.680f)
        }
    }

    // 7A. Tibialis Anterior (Outer Shin - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.635f else 0.365f
        list += createRegionPath(
            group = MuscleGroup.CALVES,
            label = "$side Tibialis Anterior (Shin)",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.825f),
            mirror = mirror
        ) {
            moveTo(0.375f, 0.735f)
            cubicTo(0.345f, 0.748f, 0.332f, 0.795f, 0.345f, 0.855f)
            cubicTo(0.355f, 0.885f, 0.368f, 0.908f, 0.382f, 0.915f)
            cubicTo(0.392f, 0.915f, 0.395f, 0.902f, 0.395f, 0.885f)
            cubicTo(0.395f, 0.835f, 0.392f, 0.785f, 0.375f, 0.735f)
        }
    }

    // 7B. Medial Calf & Soleus (Inner Lower Leg - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.582f else 0.418f
        list += createRegionPath(
            group = MuscleGroup.CALVES,
            label = "$side Medial Calf & Soleus",
            isFront = true,
            hitBoxCenter = Offset(cx, 0.825f),
            mirror = mirror
        ) {
            moveTo(0.398f, 0.735f)
            cubicTo(0.398f, 0.795f, 0.398f, 0.845f, 0.398f, 0.905f)
            cubicTo(0.408f, 0.915f, 0.418f, 0.915f, 0.422f, 0.902f)
            cubicTo(0.448f, 0.855f, 0.455f, 0.795f, 0.430f, 0.740f)
            quadraticTo(0.415f, 0.735f, 0.398f, 0.735f)
        }
    }

    // =========================================================================
    // POSTERIOR (BACK) VIEW — AIRTIGHT GAPLESS COHESIVE GEOMETRY
    // =========================================================================

    // 0. Continuous Athletic Silhouette (Posterior Left & Right Halves)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        list += createRegionPath(
            group = null,
            label = "$side Body Silhouette (Posterior)",
            isFront = false,
            hitBoxCenter = Offset(if (mirror) 0.60f else 0.40f, 0.50f),
            mirror = mirror
        ) {
            buildContinuousBodyHalf(isLeftHalf = true)
            lineTo(0.500f, 0.025f)
            close()
        }
    }

    // 1. Posterior Cranium & Cervical Neck Silhouette (with athletic hair)
    list += createRegionPath(
        group = null,
        label = "Head & Neck (Posterior)",
        isFront = false,
        hitBoxCenter = Offset(0.50f, 0.075f),
        mirror = false
    ) {
        moveTo(0.500f, 0.025f)
        cubicTo(0.550f, 0.025f, 0.562f, 0.048f, 0.560f, 0.082f)
        cubicTo(0.558f, 0.104f, 0.546f, 0.124f, 0.540f, 0.138f)
        quadraticTo(0.520f, 0.142f, 0.500f, 0.142f)
        quadraticTo(0.480f, 0.142f, 0.460f, 0.138f)
        cubicTo(0.454f, 0.124f, 0.442f, 0.104f, 0.440f, 0.082f)
        cubicTo(0.438f, 0.048f, 0.450f, 0.025f, 0.500f, 0.025f)
        close()
    }

    // 2A. Upper Trapezius Slope (Left & Right — Base of skull out to Acromion)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.635f else 0.365f
        list += createRegionPath(
            group = MuscleGroup.UPPER_BACK_TRAPS,
            label = "$side Upper Trapezius",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.155f),
            mirror = mirror
        ) {
            moveTo(0.495f, 0.125f)
            cubicTo(0.440f, 0.132f, 0.320f, 0.145f, 0.220f, 0.165f)
            cubicTo(0.280f, 0.180f, 0.370f, 0.194f, 0.435f, 0.198f)
            lineTo(0.495f, 0.198f)
            lineTo(0.495f, 0.125f)
        }
    }

    // 2B. Middle Trapezius & Rhomboid Hood (Left & Right — Flush with Upper Trapezius)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.560f else 0.440f
        list += createRegionPath(
            group = MuscleGroup.UPPER_BACK_TRAPS,
            label = "$side Middle Trapezius & Rhomboids",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.225f),
            mirror = mirror
        ) {
            // Upper border matches Upper Trap lower curve
            moveTo(0.495f, 0.198f)
            lineTo(0.435f, 0.198f)
            cubicTo(0.360f, 0.194f, 0.280f, 0.180f, 0.220f, 0.165f)
            // Lateral border sweeps down to scapular spine
            cubicTo(0.265f, 0.205f, 0.310f, 0.240f, 0.355f, 0.265f)
            // Lower border tapers to midline V-point
            cubicTo(0.395f, 0.278f, 0.450f, 0.285f, 0.495f, 0.285f)
            lineTo(0.495f, 0.198f)
        }
    }

    // 2C. Lower Trapezius V-Taper (Left & Right — Flush with Middle Trap down to T12)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.535f else 0.465f
        list += createRegionPath(
            group = MuscleGroup.UPPER_BACK_TRAPS,
            label = "$side Lower Trapezius (V-Taper)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.315f),
            mirror = mirror
        ) {
            // Upper border matches Middle Trap lower curve
            moveTo(0.495f, 0.285f)
            cubicTo(0.450f, 0.285f, 0.395f, 0.278f, 0.355f, 0.265f)
            // Lateral border descending along erectors and lats
            cubicTo(0.390f, 0.305f, 0.445f, 0.335f, 0.495f, 0.350f)
            cubicTo(0.496f, 0.330f, 0.496f, 0.305f, 0.495f, 0.285f)
        }
    }

    // 3A. Posterior Deltoid (Rear Shoulder Cap - Left & Right - Broad Flare)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.835f else 0.165f
        list += createRegionPath(
            group = MuscleGroup.REAR_DELTS,
            label = "$side Posterior Deltoid",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.218f),
            mirror = mirror
        ) {
            moveTo(0.220f, 0.165f)
            cubicTo(0.155f, 0.180f, 0.120f, 0.210f, 0.135f, 0.252f)
            cubicTo(0.145f, 0.262f, 0.165f, 0.268f, 0.185f, 0.270f)
            cubicTo(0.205f, 0.248f, 0.215f, 0.205f, 0.220f, 0.165f)
        }
    }

    // 3B. Infraspinatus & Teres Major Scapular Pocket (Green in Reference - Flush with Trap & Rear Delt)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.730f else 0.270f
        list += createRegionPath(
            group = MuscleGroup.REAR_DELTS,
            label = "$side Infraspinatus & Teres Major",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.235f),
            mirror = mirror
        ) {
            // Bounded by Rear Delt laterally and Middle Trap medially
            moveTo(0.220f, 0.165f)
            cubicTo(0.215f, 0.205f, 0.205f, 0.248f, 0.185f, 0.270f)
            cubicTo(0.235f, 0.275f, 0.295f, 0.275f, 0.355f, 0.265f)
            // Medial edge hugs Middle Trap
            cubicTo(0.310f, 0.240f, 0.265f, 0.205f, 0.220f, 0.165f)
        }
    }

    // 4A. Latissimus Dorsi Outer Wing (Deep Blue in Reference - Flush with Teres & Erector)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.675f else 0.325f
        list += createRegionPath(
            group = MuscleGroup.LATS,
            label = "$side Latissimus Dorsi (Outer Wing)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.345f),
            mirror = mirror
        ) {
            moveTo(0.185f, 0.270f)
            cubicTo(0.240f, 0.275f, 0.280f, 0.315f, 0.345f, 0.420f)
            cubicTo(0.375f, 0.425f, 0.405f, 0.425f, 0.420f, 0.420f)
            cubicTo(0.400f, 0.365f, 0.380f, 0.315f, 0.355f, 0.265f)
            cubicTo(0.295f, 0.275f, 0.235f, 0.275f, 0.185f, 0.270f)
        }
    }

    // 4B. Latissimus Dorsi Scapular Sweep (Inner Wing - Hugs Lower Trap & Erector)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.605f else 0.395f
        list += createRegionPath(
            group = MuscleGroup.LATS,
            label = "$side Latissimus Dorsi (Scapular Sweep)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.340f),
            mirror = mirror
        ) {
            moveTo(0.355f, 0.265f)
            cubicTo(0.380f, 0.315f, 0.400f, 0.365f, 0.420f, 0.420f)
            cubicTo(0.435f, 0.422f, 0.440f, 0.420f, 0.440f, 0.400f)
            cubicTo(0.440f, 0.365f, 0.435f, 0.335f, 0.390f, 0.305f)
            quadraticTo(0.370f, 0.285f, 0.355f, 0.265f)
        }
    }

    // 5. Lumbar Erector Spinae (3 Flush Paraspinal Segments - Light Blue in Reference)
    val lumbarTiers = listOf(
        Triple("Upper", 0.350f, 0.375f),
        Triple("Middle", 0.375f, 0.400f),
        Triple("Lower", 0.400f, 0.425f)
    )
    lumbarTiers.forEach { (tier, topY, botY) ->
        val midY = (topY + botY) * 0.5f
        listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
            val cx = if (mirror) 0.532f else 0.468f
            list += createRegionPath(
                group = MuscleGroup.LOWER_BACK,
                label = "$side $tier Lumbar Erector Spinae",
                isFront = false,
                hitBoxCenter = Offset(cx, midY),
                mirror = mirror
            ) {
                moveTo(0.495f, topY)
                cubicTo(0.465f, topY, 0.440f, topY + 0.002f, 0.440f, midY)
                cubicTo(0.440f, botY - 0.002f, 0.465f, botY, 0.495f, botY)
                cubicTo(0.496f, midY + 0.005f, 0.496f, midY - 0.005f, 0.495f, topY)
            }
        }
    }

    // 6A. Triceps Lateral Head (Outer Horseshoe Limb - Left & Right - Flush with Deltoid & Elbow)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.840f else 0.160f
        list += createRegionPath(
            group = MuscleGroup.TRICEPS,
            label = "$side Triceps Lateral Head",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.305f),
            mirror = mirror
        ) {
            moveTo(0.185f, 0.270f)
            lineTo(0.135f, 0.252f)
            cubicTo(0.130f, 0.285f, 0.132f, 0.320f, 0.145f, 0.355f)
            cubicTo(0.152f, 0.358f, 0.162f, 0.358f, 0.168f, 0.355f)
            cubicTo(0.170f, 0.330f, 0.175f, 0.315f, 0.185f, 0.270f)
        }
    }

    // 6B. Triceps Long Head (Inner Horseshoe Limb - Left & Right - Flush with Deltoid & Elbow)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.785f else 0.215f
        list += createRegionPath(
            group = MuscleGroup.TRICEPS,
            label = "$side Triceps Long Head",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.305f),
            mirror = mirror
        ) {
            moveTo(0.185f, 0.270f)
            lineTo(0.250f, 0.252f)
            cubicTo(0.250f, 0.285f, 0.248f, 0.320f, 0.245f, 0.355f)
            cubicTo(0.238f, 0.358f, 0.228f, 0.358f, 0.220f, 0.355f)
            cubicTo(0.218f, 0.330f, 0.200f, 0.315f, 0.185f, 0.270f)
        }
    }

    // 6C. Triceps Medial Head & Olecranon Tendon (Chevron Wedge Between Lateral & Long Heads)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.805f else 0.195f
        list += createRegionPath(
            group = MuscleGroup.TRICEPS,
            label = "$side Triceps Medial Head (Chevron)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.340f),
            mirror = mirror
        ) {
            moveTo(0.185f, 0.290f)
            cubicTo(0.195f, 0.315f, 0.210f, 0.330f, 0.220f, 0.355f)
            cubicTo(0.210f, 0.368f, 0.178f, 0.368f, 0.168f, 0.355f)
            cubicTo(0.172f, 0.330f, 0.178f, 0.315f, 0.185f, 0.290f)
        }
    }

    // 7A. Brachioradialis & Extensor Carpi (Outer Forearm Head - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.850f else 0.150f
        list += createRegionPath(
            group = MuscleGroup.FOREARMS,
            label = "$side Forearm Extensors (Outer Head)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.420f),
            mirror = mirror
        ) {
            moveTo(0.190f, 0.365f)
            lineTo(0.145f, 0.355f)
            cubicTo(0.128f, 0.395f, 0.140f, 0.445f, 0.155f, 0.485f)
            lineTo(0.172f, 0.485f)
            cubicTo(0.175f, 0.440f, 0.182f, 0.400f, 0.190f, 0.365f)
        }
    }

    // 7B. Extensor Digitorum & Carpi Ulnaris (Inner Forearm Head - Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.795f else 0.205f
        list += createRegionPath(
            group = MuscleGroup.FOREARMS,
            label = "$side Forearm Extensors (Inner Head)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.420f),
            mirror = mirror
        ) {
            moveTo(0.190f, 0.365f)
            lineTo(0.245f, 0.355f)
            cubicTo(0.235f, 0.410f, 0.200f, 0.460f, 0.190f, 0.485f)
            lineTo(0.172f, 0.485f)
            cubicTo(0.175f, 0.440f, 0.182f, 0.400f, 0.190f, 0.365f)
        }
    }

    // 8A. Gluteus Medius (Upper Hip Shelf - Purple in Reference - Flush with Maximus)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.615f else 0.385f
        list += createRegionPath(
            group = MuscleGroup.GLUTES,
            label = "$side Gluteus Medius",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.450f),
            mirror = mirror
        ) {
            moveTo(0.348f, 0.420f)
            cubicTo(0.335f, 0.445f, 0.330f, 0.470f, 0.332f, 0.485f)
            cubicTo(0.365f, 0.485f, 0.410f, 0.468f, 0.440f, 0.445f)
            cubicTo(0.405f, 0.430f, 0.375f, 0.422f, 0.348f, 0.420f)
        }
    }

    // 8B. Gluteus Maximus (Full Rounded Butterfly Belly - Purple in Reference - Flush with Medius)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.575f else 0.425f
        list += createRegionPath(
            group = MuscleGroup.GLUTES,
            label = "$side Gluteus Maximus",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.495f),
            mirror = mirror
        ) {
            // Upper border hugs Lower Erector & Gluteus Medius
            moveTo(0.495f, 0.425f)
            cubicTo(0.475f, 0.435f, 0.455f, 0.440f, 0.440f, 0.445f)
            cubicTo(0.410f, 0.468f, 0.365f, 0.485f, 0.332f, 0.485f)
            // Outer curved sweep to infragluteal fold
            cubicTo(0.330f, 0.518f, 0.335f, 0.540f, 0.355f, 0.545f)
            cubicTo(0.410f, 0.552f, 0.465f, 0.545f, 0.495f, 0.535f)
            cubicTo(0.496f, 0.500f, 0.496f, 0.460f, 0.495f, 0.425f)
        }
    }

    // 9A. Biceps Femoris (Lateral Outer Hamstring - Red in Reference - Flush with Gluteal Fold)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.635f else 0.365f
        list += createRegionPath(
            group = MuscleGroup.HAMSTRINGS,
            label = "$side Biceps Femoris (Outer Hamstring)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.615f),
            mirror = mirror
        ) {
            moveTo(0.355f, 0.545f)
            cubicTo(0.332f, 0.585f, 0.332f, 0.635f, 0.352f, 0.675f)
            cubicTo(0.370f, 0.690f, 0.388f, 0.685f, 0.395f, 0.670f)
            cubicTo(0.398f, 0.625f, 0.395f, 0.580f, 0.385f, 0.550f)
            quadraticTo(0.370f, 0.546f, 0.355f, 0.545f)
        }
    }

    // 9B. Semitendinosus (Medial Inner Hamstring - Red in Reference)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.585f else 0.415f
        list += createRegionPath(
            group = MuscleGroup.HAMSTRINGS,
            label = "$side Semitendinosus (Inner Hamstring)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.615f),
            mirror = mirror
        ) {
            moveTo(0.395f, 0.550f)
            cubicTo(0.398f, 0.595f, 0.400f, 0.635f, 0.402f, 0.675f)
            cubicTo(0.415f, 0.688f, 0.435f, 0.685f, 0.442f, 0.668f)
            cubicTo(0.448f, 0.625f, 0.448f, 0.585f, 0.435f, 0.550f)
            quadraticTo(0.415f, 0.548f, 0.395f, 0.550f)
        }
    }

    // 9C. Semimembranosus (Deep Inner Hamstring)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.545f else 0.455f
        list += createRegionPath(
            group = MuscleGroup.HAMSTRINGS,
            label = "$side Semimembranosus (Deep Inner)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.605f),
            mirror = mirror
        ) {
            moveTo(0.440f, 0.550f)
            cubicTo(0.452f, 0.585f, 0.455f, 0.625f, 0.445f, 0.668f)
            cubicTo(0.455f, 0.678f, 0.468f, 0.668f, 0.472f, 0.640f)
            cubicTo(0.478f, 0.605f, 0.478f, 0.570f, 0.470f, 0.545f)
            quadraticTo(0.455f, 0.548f, 0.440f, 0.550f)
        }
    }

    // 9D. Popliteal Fossa (Diamond Behind Knee - Back)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.600f else 0.400f
        list += createRegionPath(
            group = null,
            label = "$side Popliteal Fossa (Knee Hollow)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.705f),
            mirror = mirror
        ) {
            moveTo(0.395f, 0.675f)
            lineTo(0.420f, 0.675f)
            lineTo(0.425f, 0.725f)
            lineTo(0.380f, 0.725f)
            close()
        }
    }

    // 10A. Lateral Gastrocnemius (Outer Calf Head - Blue in Reference)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.645f else 0.355f
        list += createRegionPath(
            group = MuscleGroup.CALVES,
            label = "$side Lateral Gastrocnemius (Calf)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.785f),
            mirror = mirror
        ) {
            moveTo(0.380f, 0.725f)
            cubicTo(0.345f, 0.738f, 0.328f, 0.770f, 0.335f, 0.825f)
            cubicTo(0.342f, 0.855f, 0.360f, 0.872f, 0.378f, 0.865f)
            cubicTo(0.388f, 0.855f, 0.390f, 0.830f, 0.390f, 0.785f)
            cubicTo(0.390f, 0.755f, 0.388f, 0.735f, 0.380f, 0.725f)
        }
    }

    // 10B. Medial Gastrocnemius (Inner Calf Head - Blue in Reference)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.585f else 0.415f
        list += createRegionPath(
            group = MuscleGroup.CALVES,
            label = "$side Medial Gastrocnemius (Calf)",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.800f),
            mirror = mirror
        ) {
            moveTo(0.395f, 0.725f)
            cubicTo(0.395f, 0.765f, 0.395f, 0.810f, 0.392f, 0.855f)
            cubicTo(0.402f, 0.880f, 0.422f, 0.880f, 0.435f, 0.855f)
            cubicTo(0.460f, 0.815f, 0.465f, 0.765f, 0.442f, 0.730f)
            quadraticTo(0.418f, 0.725f, 0.395f, 0.725f)
        }
    }

    // 10C. Soleus & Achilles Tendon Taper (Left & Right)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.605f else 0.395f
        list += createRegionPath(
            group = MuscleGroup.CALVES,
            label = "$side Soleus & Achilles Tendon",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.890f),
            mirror = mirror
        ) {
            moveTo(0.378f, 0.865f)
            cubicTo(0.370f, 0.882f, 0.370f, 0.900f, 0.372f, 0.915f)
            cubicTo(0.395f, 0.915f, 0.420f, 0.915f, 0.425f, 0.915f)
            cubicTo(0.425f, 0.898f, 0.420f, 0.880f, 0.410f, 0.865f)
            quadraticTo(0.395f, 0.872f, 0.378f, 0.865f)
        }
    }

    // 11. Athletic Running Sneaker Accent (Sole & Heel - Grounded Stance)
    listOf(false to "Left", true to "Right").forEach { (mirror, side) ->
        val cx = if (mirror) 0.600f else 0.400f
        list += createRegionPath(
            group = null,
            label = "$side Athletic Sneaker Sole",
            isFront = false,
            hitBoxCenter = Offset(cx, 0.955f),
            mirror = mirror
        ) {
            moveTo(0.350f, 0.955f)
            lineTo(0.442f, 0.955f)
            lineTo(0.442f, 0.975f)
            lineTo(0.350f, 0.975f)
            close()
        }
    }

    return list
}

/**
 * Side-by-side Front (Anterior) and Back (Posterior) hyper-detailed anatomical muscle recovery diagram.
 * Supports interactive tap inspection on any muscle head to show exact recovery percentage and hours remaining.
 */
@Composable
fun AnatomicalMuscleMapCanvas(
    muscleHeatMap: Map<MuscleGroup, Float>,
    modifier: Modifier = Modifier
) {
    val allRegions = remember { ANATOMICAL_MUSCLE_REGION_PATHS }
    val frontRegions = remember(allRegions) { allRegions.filter { it.isFront } }
    val backRegions = remember(allRegions) { allRegions.filter { !it.isFront } }

    val defaultSelectedGroup = remember(muscleHeatMap) {
        muscleHeatMap.entries
            .filter { it.value > 0.02f }
            .maxByOrNull { it.value }
            ?.key
    }

    var tappedRegion by remember(defaultSelectedGroup) {
        mutableStateOf(
            defaultSelectedGroup?.let { grp ->
                allRegions.firstOrNull { it.group == grp }
            }
        )
    }

    val activeDescription = remember(muscleHeatMap) {
        val trained = muscleHeatMap.entries.filter { it.value > 0.05f }
        if (trained.isEmpty()) {
            "Anatomical muscle recovery map. All muscles 100 percent recovered."
        } else {
            "Anatomical muscle recovery map. " + trained.joinToString { (m, v) ->
                "${m.displayName} ${((1f - v.coerceIn(0f, 1f)) * 100).roundToInt()} percent recovered"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = activeDescription },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnatomicalFigurePane(
                title = "Front",
                regions = frontRegions,
                muscleHeatMap = muscleHeatMap,
                selectedRegion = tappedRegion,
                onRegionTapped = { tappedRegion = it },
                modifier = Modifier.weight(1f)
            )

            AnatomicalFigurePane(
                title = "Back",
                regions = backRegions,
                muscleHeatMap = muscleHeatMap,
                selectedRegion = tappedRegion,
                onRegionTapped = { tappedRegion = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Sleek Pill Tooltip (#282A2C background, 16.dp radius)
        val regionToDisplay = tappedRegion
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (regionToDisplay?.group != null) {
                    val grp = regionToDisplay.group
                    val intensity = (muscleHeatMap[grp] ?: 0f).coerceIn(0f, 1f)
                    val recoveredPct = ((1f - intensity) * 100f).roundToInt().coerceIn(0, 100)
                    val readyHours = (intensity * 48f).roundToInt().coerceAtLeast(0)
                    val dotColor = muscleRecoveryColor(intensity)
                    val readyLabel = if (readyHours > 0) "Ready in ${readyHours}h" else "Ready now"

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${regionToDisplay.label} · $recoveredPct% recovered · $readyLabel",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Tap any muscle head to inspect recovery status",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun AnatomicalFigurePane(
    title: String,
    regions: List<MuscleRegionPath>,
    muscleHeatMap: Map<MuscleGroup, Float>,
    selectedRegion: MuscleRegionPath?,
    onRegionTapped: (MuscleRegionPath) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(regions) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val closest = regions
                            .filter { it.group != null }
                            .minByOrNull { reg ->
                                hypot(
                                    tapOffset.x - reg.hitBoxCenter.x * w,
                                    tapOffset.y - reg.hitBoxCenter.y * h
                                )
                            }
                        if (closest != null) {
                            onRegionTapped(closest)
                        }
                    }
                }
        ) {
            drawAnatomicalRegions(
                regions = regions,
                muscleHeatMap = muscleHeatMap,
                selectedRegion = selectedRegion
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

internal fun DrawScope.drawAnatomicalRegions(
    regions: List<MuscleRegionPath>,
    muscleHeatMap: Map<MuscleGroup, Float>,
    selectedRegion: MuscleRegionPath? = null
) {
    val w = size.width
    val h = size.height
    val separatorStrokePx = 1.2.dp.toPx()
    val contourStrokePx = 1.dp.toPx()

    regions.forEach { region ->
        val path = region.buildPath(w, h)
        val group = region.group
        if (group == null) {
            val isSneakerSole = region.label.contains("Sneaker")
            val silhouetteColor = if (isSneakerSole) Color(0xFFD1D5DB) else SilhouetteNeutral
            drawPath(
                path = path,
                color = silhouetteColor,
                style = Fill
            )
            drawPath(
                path = path,
                color = MuscleSeparationStroke.copy(alpha = 0.85f),
                style = Stroke(width = contourStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else {
            val intensity = (muscleHeatMap[group] ?: 0f).coerceIn(0f, 1f)
            val fillColor = muscleRecoveryColor(intensity)
            val isSelected = selectedRegion == region || (selectedRegion?.group != null && selectedRegion.group == group)

            drawPath(
                path = path,
                color = fillColor,
                style = Fill
            )

            // Crisp 1.2.dp #131314 anatomical separator trench
            drawPath(
                path = path,
                color = BackgroundCanvas,
                style = Stroke(width = separatorStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Crisp 1.dp #3C4043 outer anatomical contour stroke (highlighted when selected or active)
            val outerStrokeColor = when {
                isSelected -> TextPrimary.copy(alpha = 0.95f)
                intensity > 0.05f -> fillColor.copy(alpha = 0.85f)
                else -> MuscleSeparationStroke
            }
            drawPath(
                path = path,
                color = outerStrokeColor,
                style = Stroke(width = contourStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

internal fun DrawScope.drawFrontBodySilhouette(intensities: Map<MuscleGroup, Float>) {
    val frontRegions = ANATOMICAL_MUSCLE_REGION_PATHS.filter { it.isFront }
    drawAnatomicalRegions(frontRegions, intensities)
}

internal fun DrawScope.drawBackBodySilhouette(intensities: Map<MuscleGroup, Float>) {
    val backRegions = ANATOMICAL_MUSCLE_REGION_PATHS.filter { !it.isFront }
    drawAnatomicalRegions(backRegions, intensities)
}
